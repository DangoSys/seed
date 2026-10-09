/*************************************************************************
    > File Name: IDU.scala
    > Author: Nick
    > Email: chengni2001@gmail.com
    > Created Time: 2026-10-08 15:24:24
    > Description:
*************************************************************************/

package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

/** ID stage: decode a 32-bit instruction and consume external register-file read data.
  *
  * `in` comes from IFIDReg. It is an `IfId` with these fields:
  * - `valid`: 1 bit. This entry holds an instruction. When it is 0, ignore `pc` and `instr`.
  * - `pc`: `vaddrBits` bits, currently 64. Address of the instruction.
  * - `instr`: 32 bits. Instruction word to decode.
  *
  * The other ports are:
  * - `rs1Data`, `rs2Data`: `xLen` bits, currently 64. Register-file read data, supplied by PipelineCore.
  * - `rs1`, `rs2`: 5 bits each. Source register numbers for the current instruction; unused sources are zero. PipelineCore uses them to read the register file.
  * - `usesRs1`, `usesRs2`: 1 bit each. Set only when this instruction actually reads that source. PipelineCore uses them for the load-use check.
  *
  * `out` goes to IDEXReg. It is an `IdEx` with these fields:
  * - `valid`: 1 bit. Copied from `in.valid`. An unsupported opcode stays valid but keeps the zero control signals below.
  * - `pc`: `vaddrBits` bits. Copied from `in.pc`.
  * - `rs1`, `rs2`, `rd`: 5 bits each. Source and destination register numbers from `instr`.
  * - `rs1Val`, `rs2Val`: `xLen` bits. Snapshot of `rs1Data` and `rs2Data` for this instruction.
  * - `imm`: `xLen` bits. The immediate selected for this instruction, sign-extended to 64 bits.
  * - `aluOp`: 5 bits. ALU operation. Values are `AluOp` in PipelineTypes.
  * - `aluSrcImm`: 1 bit. The ALU's second operand is `imm`. When 0, it is `rs2Val`.
  * - `usePc`: 1 bit. Add `imm` to `pc` (AUIPC) instead of to `rs1Val`.
  * - `regWrite`: 1 bit. Write `rd` when this instruction retires.
  * - `memRead`: 1 bit. This instruction is a load.
  * - `memWrite`: 1 bit. This instruction is a store.
  * - `memToReg`: 1 bit. The value written to `rd` comes from the load data.
  * - `branch`: 1 bit. Conditional branch. EX checks the condition.
  * - `jump`: 1 bit. JAL or JALR.
  * - `jalr`: 1 bit. The jump target is `rs1Val + imm`. When 0, a jump target is `pc + imm`.
  * - `branchFunct3`: 3 bits. `funct3`, selecting BEQ, BNE, BLT, BGE, BLTU or BGEU.
  * - `memSize`: 3 bits. Access size: 0 byte, 1 halfword, 2 word, 3 doubleword. Non-memory instructions leave it at 2.
  * - `loadUnsigned`: 1 bit. Zero-extend the loaded value. MEM performs the extension.
  * - `wordOp`: 1 bit. An RV64 W instruction. The operation uses the low 32 bits and sign-extends the result.
  * - `unsignedOp`: 1 bit. An unsigned M operation: MULHU, DIVU or REMU, including the W forms of DIVU and REMU.
  */

class IDU(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    /*Ports from IFIDReg*/
    val in = Input(new IfId(p))
    /*Ports from RegisterFile*/
    val rs1Data = Input(UInt(p.xLen.W))
    val rs2Data = Input(UInt(p.xLen.W))
    /*Ports to RegisterFile*/
    val rs1 = Output(UInt(5.W))
    val rs2 = Output(UInt(5.W))
    /*Ports to PipelineCore*/
    val usesRs1 = Output(Bool())
    val usesRs2 = Output(Bool())
    /*Ports to IDEXReg*/
    val out = Output(new IdEx(p))
  })

  import Instruction._

  val instr = io.in.instr
  val funct3 = instr(14, 12)
  val rs1 = instr(19, 15)
  val rs2 = instr(24, 20)
  val rd = instr(11, 7)

  val immI = Cat(Fill(52, instr(31)), instr(31, 20))
  val immS = Cat(Fill(52, instr(31)), instr(31, 25), instr(11, 7))
  val immB = Cat(Fill(51, instr(31)), instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U)
  val immU = Cat(Fill(32, instr(31)), instr(31, 12), Fill(12, 0.U))
  val immJ = Cat(Fill(43, instr(31)), instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U)
  val shamt6 = Cat(0.U(58.W), instr(25, 20))
  val shamt5 = Cat(0.U(59.W), instr(24, 20))

  val out = WireDefault(0.U.asTypeOf(new IdEx(p)))
  val usesRs1 = WireDefault(false.B)
  val usesRs2 = WireDefault(false.B)
  out.valid := io.in.valid
  out.pc := io.in.pc
  out.rd := rd
  out.rs1Val := io.rs1Data
  out.rs2Val := io.rs2Data
  out.branchFunct3 := funct3
  out.memSize := 2.U // Default for instructions without a memory access.


  // Instructions in one family set the same control signals and differ only in
  // the ALU operation, immediate, access size, or RV64 W behavior. Each helper
  // fills that common pattern; the when blocks below only choose the variant.
  // word and unsigned default to false, so a plain 64-bit instruction omits them.

  // ADDI and the other immediate ALU ops. Shifts pass shamt instead of immI.
  // ADDIW and the W shifts pass word = true.
  def aluImmediate(op: UInt, immediate: UInt = immI, word: Boolean = false): Unit = {
    out.aluOp := op
    out.imm := immediate
    out.aluSrcImm := true.B
    out.regWrite := true.B
    out.wordOp := word.B
    usesRs1 := true.B
  }


  // ADD, SUB, and the M register ops. W forms pass word; DIVU and REMU pass unsigned.
  def aluRegister(op: UInt, word: Boolean = false, unsigned: Boolean = false): Unit = {
    out.aluOp := op
    out.regWrite := true.B
    out.wordOp := word.B
    out.unsignedOp := unsigned.B
    usesRs1 := true.B
    usesRs2 := true.B
  }


  // BEQ, BNE, BLT, BGE, BLTU, BGEU. branchFunct3 selects the condition in EX.
  def branch(): Unit = {
    out.imm := immB
    out.branch := true.B
    usesRs1 := true.B
    usesRs2 := true.B
  }


  // LB, LH, LW, LD. The unsigned variants pass unsigned = true.
  def load(size: Int, unsigned: Boolean = false): Unit = {
    out.imm := immI
    out.aluSrcImm := true.B
    out.regWrite := true.B
    out.memRead := true.B
    out.memToReg := true.B
    out.memSize := size.U
    out.loadUnsigned := unsigned.B
    usesRs1 := true.B
  }

  // SB, SH, SW, SD. size is memSize: 0 byte, 1 halfword, 2 word, 3 doubleword.
  def store(size: Int): Unit = {
    out.imm := immS
    out.aluSrcImm := true.B
    out.memWrite := true.B
    out.memSize := size.U
    usesRs1 := true.B
    usesRs2 := true.B
  }

  // Match complete instruction encodings, so every case names one instruction.
  when(io.in.valid && INST_LUI === instr) {
    out.imm := immU
    out.aluSrcImm := true.B
    out.regWrite := true.B
  }
  when(io.in.valid && INST_AUIPC === instr) {
    out.imm := immU
    out.aluSrcImm := true.B
    out.usePc := true.B
    out.regWrite := true.B
  }
  when(io.in.valid && INST_JAL === instr) {
    out.imm := immJ
    out.jump := true.B
    out.regWrite := true.B
  }
  when(io.in.valid && INST_JALR === instr) {
    out.imm := immI
    out.jump := true.B
    out.jalr := true.B
    out.regWrite := true.B
    usesRs1 := true.B
  }

  when(io.in.valid && INST_BEQ === instr)  { branch() }
  when(io.in.valid && INST_BNE === instr)  { branch() }
  when(io.in.valid && INST_BLT === instr)  { branch() }
  when(io.in.valid && INST_BGE === instr)  { branch() }
  when(io.in.valid && INST_BLTU === instr) { branch() }
  when(io.in.valid && INST_BGEU === instr) { branch() }

  when(io.in.valid && INST_LB === instr)  { load(0) }
  when(io.in.valid && INST_LH === instr)  { load(1) }
  when(io.in.valid && INST_LW === instr)  { load(2) }
  when(io.in.valid && INST_LD === instr)  { load(3) }
  when(io.in.valid && INST_LBU === instr) { load(0, unsigned = true) }
  when(io.in.valid && INST_LHU === instr) { load(1, unsigned = true) }
  when(io.in.valid && INST_LWU === instr) { load(2, unsigned = true) }

  when(io.in.valid && INST_SB === instr) { store(0) }
  when(io.in.valid && INST_SH === instr) { store(1) }
  when(io.in.valid && INST_SW === instr) { store(2) }
  when(io.in.valid && INST_SD === instr) { store(3) }

  when(io.in.valid && INST_ADDI === instr)  { aluImmediate(AluOp.add) }
  when(io.in.valid && INST_SLTI === instr)  { aluImmediate(AluOp.slt) }
  when(io.in.valid && INST_SLTIU === instr) { aluImmediate(AluOp.sltu) }
  when(io.in.valid && INST_XORI === instr)  { aluImmediate(AluOp.xor) }
  when(io.in.valid && INST_ORI === instr)   { aluImmediate(AluOp.or) }
  when(io.in.valid && INST_ANDI === instr)  { aluImmediate(AluOp.and) }
  when(io.in.valid && INST_SLLI === instr)  { aluImmediate(AluOp.sll, shamt6) }
  when(io.in.valid && INST_SRLI === instr)  { aluImmediate(AluOp.srl, shamt6) }
  when(io.in.valid && INST_SRAI === instr)  { aluImmediate(AluOp.sra, shamt6) }

  when(io.in.valid && INST_ADD === instr)  { aluRegister(AluOp.add) }
  when(io.in.valid && INST_SUB === instr)  { aluRegister(AluOp.sub) }
  when(io.in.valid && INST_SLL === instr)  { aluRegister(AluOp.sll) }
  when(io.in.valid && INST_SLT === instr)  { aluRegister(AluOp.slt) }
  when(io.in.valid && INST_SLTU === instr) { aluRegister(AluOp.sltu) }
  when(io.in.valid && INST_XOR === instr)  { aluRegister(AluOp.xor) }
  when(io.in.valid && INST_SRL === instr)  { aluRegister(AluOp.srl) }
  when(io.in.valid && INST_SRA === instr)  { aluRegister(AluOp.sra) }
  when(io.in.valid && INST_OR === instr)   { aluRegister(AluOp.or) }
  when(io.in.valid && INST_AND === instr)  { aluRegister(AluOp.and) }

  when(io.in.valid && INST_MUL === instr)    { aluRegister(AluOp.mul) }
  when(io.in.valid && INST_MULH === instr)   { aluRegister(AluOp.mulh) }
  when(io.in.valid && INST_MULHSU === instr) { aluRegister(AluOp.mulhsu) }
  when(io.in.valid && INST_MULHU === instr)  { aluRegister(AluOp.mulhu, unsigned = true) }
  when(io.in.valid && INST_DIV === instr)    { aluRegister(AluOp.div) }
  when(io.in.valid && INST_DIVU === instr)   { aluRegister(AluOp.divu, unsigned = true) }
  when(io.in.valid && INST_REM === instr)    { aluRegister(AluOp.rem) }
  when(io.in.valid && INST_REMU === instr)   { aluRegister(AluOp.remu, unsigned = true) }

  when(io.in.valid && INST_ADDIW === instr) { aluImmediate(AluOp.add, word = true) }
  when(io.in.valid && INST_SLLIW === instr) { aluImmediate(AluOp.sll, shamt5, word = true) }
  when(io.in.valid && INST_SRLIW === instr) { aluImmediate(AluOp.srl, shamt5, word = true) }
  when(io.in.valid && INST_SRAIW === instr) { aluImmediate(AluOp.sra, shamt5, word = true) }

  when(io.in.valid && INST_ADDW === instr)  { aluRegister(AluOp.add, word = true) }
  when(io.in.valid && INST_SUBW === instr)  { aluRegister(AluOp.sub, word = true) }
  when(io.in.valid && INST_SLLW === instr)  { aluRegister(AluOp.sll, word = true) }
  when(io.in.valid && INST_SRLW === instr)  { aluRegister(AluOp.srl, word = true) }
  when(io.in.valid && INST_SRAW === instr)  { aluRegister(AluOp.sra, word = true) }
  when(io.in.valid && INST_MULW === instr)  { aluRegister(AluOp.mul, word = true) }
  when(io.in.valid && INST_DIVW === instr)  { aluRegister(AluOp.div, word = true) }
  when(io.in.valid && INST_DIVUW === instr) { aluRegister(AluOp.divu, word = true, unsigned = true) }
  when(io.in.valid && INST_REMW === instr)  { aluRegister(AluOp.rem, word = true) }
  when(io.in.valid && INST_REMUW === instr) { aluRegister(AluOp.remu, word = true, unsigned = true) }

  // A format without a source register must not feed immediate bits to EX forwarding.
  val readRs1 = Mux(io.in.valid && usesRs1, rs1, 0.U)
  val readRs2 = Mux(io.in.valid && usesRs2, rs2, 0.U)
  out.rs1 := readRs1
  out.rs2 := readRs2

  io.out := out
  io.rs1 := readRs1
  io.rs2 := readRs2
  io.usesRs1 := io.in.valid && usesRs1
  io.usesRs2 := io.in.valid && usesRs2
}
