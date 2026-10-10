package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

/** EX stage: resolve operands, execute ALU/M operations, and resolve control flow.
  *
  * The implementation follows the data path from left to right:
  *
  *   1. Read the two source values, applying EX/MEM then MEM/WB forwarding.
  *   2. Select PC or rs1 for operand A, and an immediate or rs2 for operand B.
  *   3. Adapt W-operation operands to 32 bits and select the ALU/M result.
  *   4. Resolve branches and jumps.
  *   5. Pack the result and memory controls into ExMem.
  *
  * Keeping these decisions separate makes it possible to follow one instruction
  * through EX without having to decode nested Mux expressions at each use site.
  */
class EXU(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IdEx(p))
    val exmem = Input(new ExMem(p))
    val memwb = Input(new MemWb(p))
    val out = Output(new ExMem(p))
    val redirect = Valid(UInt(p.vaddrBits.W))
  })

  // --------------------------------------------------------------------------
  // 1. Operand forwarding
  // --------------------------------------------------------------------------

  /** Select the newest available value for one decoded source register. */
  def forwardedValue(sourceReg: UInt, decodedValue: UInt): UInt = {
    val value = WireDefault(decodedValue)

    when(sourceReg === 0.U) {
      value := 0.U
    }.elsewhen(
      io.exmem.valid && io.exmem.regWrite && !io.exmem.memToReg &&
        io.exmem.rd =/= 0.U && io.exmem.rd === sourceReg) {
      value := io.exmem.aluResult
    }.elsewhen(
      io.memwb.valid && io.memwb.regWrite &&
        io.memwb.rd =/= 0.U && io.memwb.rd === sourceReg) {
      value := io.memwb.result
    }

    value
  }

  val rs1Value = forwardedValue(io.in.rs1, io.in.rs1Val)
  val rs2Value = forwardedValue(io.in.rs2, io.in.rs2Val)

  // --------------------------------------------------------------------------
  // 2. Operand selection and W-operation preparation
  // --------------------------------------------------------------------------

  // W operations use the low 32 bits, then sign-extend their result. The
  // unsigned form is needed by DIVUW/REMUW and is also harmless for shifts.
  val signedWordRs1 = Cat(Fill(32, rs1Value(31)), rs1Value(31, 0))
  val signedWordRs2 = Cat(Fill(32, rs2Value(31)), rs2Value(31, 0))
  val unsignedWordRs1 = Cat(0.U(32.W), rs1Value(31, 0))
  val unsignedWordRs2 = Cat(0.U(32.W), rs2Value(31, 0))
  // SRLW/SRLIW are logical shifts, so their left operand is zero-extended
  // even though their decoder does not use the M-operation unsigned flag.
  val wordUsesUnsignedRs1 = io.in.unsignedOp || io.in.aluOp === AluOp.srl
  val wordRs1 = Mux(wordUsesUnsignedRs1, unsignedWordRs1, signedWordRs1)
  val wordRs2 = Mux(io.in.unsignedOp, unsignedWordRs2, signedWordRs2)

  // Select the architectural operands before applying the operation width.
  // In particular, an immediate must remain the second operand of ADDIW and
  // the W shift instructions; it must not be replaced by forwarded rs2.
  val operandA = Mux(io.in.usePc, io.in.pc, Mux(io.in.wordOp, wordRs1, rs1Value))
  val operandB = Mux(io.in.aluSrcImm, io.in.imm, Mux(io.in.wordOp, wordRs2, rs2Value))
  val shiftAmount = Mux(io.in.wordOp, Cat(0.U(1.W), operandB(4, 0)), operandB(5, 0))

  // --------------------------------------------------------------------------
  // 3. ALU and M operations
  // --------------------------------------------------------------------------

  val productSignedSigned = (operandA.asSInt * operandB.asSInt).asUInt
  val productUnsignedUnsigned = operandA * operandB
  val productSignedUnsigned =
    (Cat(Fill(64, operandA(63)), operandA).asSInt *
      Cat(0.U(64.W), operandB).asSInt).asUInt

  val aluResult = MuxLookup(io.in.aluOp, operandA + operandB)(Seq(
    AluOp.add -> (operandA + operandB),
    AluOp.sub -> (operandA - operandB),
    AluOp.sll -> (operandA << shiftAmount),
    AluOp.slt -> (operandA.asSInt < operandB.asSInt).asUInt,
    AluOp.sltu -> (operandA < operandB).asUInt,
    AluOp.xor -> (operandA ^ operandB),
    AluOp.srl -> (operandA >> shiftAmount),
    AluOp.sra -> (operandA.asSInt >> shiftAmount).asUInt,
    AluOp.and -> (operandA & operandB),
    AluOp.or -> (operandA | operandB),
    AluOp.mul -> (operandA * operandB),
    AluOp.mulh -> productSignedSigned(127, 64),
    AluOp.mulhsu -> productSignedUnsigned(127, 64),
    AluOp.mulhu -> productUnsignedUnsigned(127, 64),
    AluOp.div -> Mux(operandB === 0.U, "hffffffffffffffff".U,
      (operandA.asSInt / operandB.asSInt).asUInt),
    AluOp.divu -> Mux(operandB === 0.U, "hffffffffffffffff".U, operandA / operandB),
    AluOp.rem -> Mux(operandB === 0.U, operandA,
      (operandA.asSInt % operandB.asSInt).asUInt),
    AluOp.remu -> Mux(operandB === 0.U, operandA, operandA % operandB)
  ))

  // Every W result is sign-extended only after the selected operation has run.
  val result = Mux(io.in.wordOp,
    Cat(Fill(32, aluResult(31)), aluResult(31, 0)),
    aluResult)

  // --------------------------------------------------------------------------
  // 4. Branch and jump resolution
  // --------------------------------------------------------------------------

  val branchCondition = MuxLookup(io.in.branchFunct3, false.B)(Seq(
    0.U -> (rs1Value === rs2Value),
    1.U -> (rs1Value =/= rs2Value),
    4.U -> (rs1Value.asSInt < rs2Value.asSInt),
    5.U -> (rs1Value.asSInt >= rs2Value.asSInt),
    6.U -> (rs1Value < rs2Value),
    7.U -> (rs1Value >= rs2Value)
  ))

  val controlTransferTaken = io.in.valid &&
    (io.in.jump || (io.in.branch && branchCondition))
  val controlTransferTarget =
    (Mux(io.in.jalr, rs1Value + io.in.imm, io.in.pc + io.in.imm)) &
      "hfffffffffffffffe".U

  // --------------------------------------------------------------------------
  // 5. EX/MEM output
  // --------------------------------------------------------------------------

  val out = WireDefault(0.U.asTypeOf(new ExMem(p)))
  out.valid := io.in.valid
  out.pc := io.in.pc
  out.aluResult := Mux(io.in.jump, io.in.pc + 4.U, result)
  out.storeData := rs2Value
  out.rd := io.in.rd
  out.regWrite := io.in.regWrite
  out.memRead := io.in.memRead
  out.memWrite := io.in.memWrite
  out.memToReg := io.in.memToReg
  out.memSize := io.in.memSize
  out.loadUnsigned := io.in.loadUnsigned
  out.memIssued := false.B
  out.memDone := !(io.in.memRead || io.in.memWrite)
  io.out := out

  io.redirect.valid := controlTransferTaken
  io.redirect.bits := controlTransferTarget
}
