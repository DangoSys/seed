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

/** ID stage: decode a 32-bit instruction and read the 64-bit register file. */
class IDU(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IfId(p))
    val rs1Data = Input(UInt(p.xLen.W))
    val rs2Data = Input(UInt(p.xLen.W))
    val rs1 = Output(UInt(5.W))
    val rs2 = Output(UInt(5.W))
    val usesRs1 = Output(Bool())
    val usesRs2 = Output(Bool())
    val out = Output(new IdEx(p))
  })

  val instr = io.in.instr
  val opcode = instr(6, 0)
  val funct3 = instr(14, 12)
  val funct7 = instr(31, 25)
  val rs1 = instr(19, 15)
  val rs2 = instr(24, 20)
  val rd = instr(11, 7)

  val immI = Cat(Fill(52, instr(31)), instr(31, 20))
  val immS = Cat(Fill(52, instr(31)), instr(31, 25), instr(11, 7))
  val immB = Cat(Fill(51, instr(31)), instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U)
  val immU = Cat(Fill(32, instr(31)), instr(31, 12), Fill(12, 0.U))
  val immJ = Cat(Fill(43, instr(31)), instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U)

  val out = WireDefault(0.U.asTypeOf(new IdEx(p)))
  out.valid := io.in.valid
  out.pc := io.in.pc
  out.rs1 := rs1
  out.rs2 := rs2
  out.rd := rd
  out.rs1Val := io.rs1Data
  out.rs2Val := io.rs2Data
  out.branchFunct3 := funct3
  out.memSize := 2.U // word is the default data width for non-memory operations
  val usesRs1 = WireDefault(false.B)
  val usesRs2 = WireDefault(false.B)

  switch(opcode) {
    is("b0110111".U) { out.imm := immU; out.aluSrcImm := true.B; out.regWrite := true.B } // LUI
    is("b0010111".U) { out.imm := immU; out.aluSrcImm := true.B; out.usePc := true.B; out.regWrite := true.B } // AUIPC
    is("b1101111".U) { out.imm := immJ; out.jump := true.B; out.regWrite := true.B } // JAL
    is("b1100111".U) { out.imm := immI; out.aluSrcImm := true.B; out.jalr := true.B; out.jump := true.B; out.regWrite := true.B; usesRs1 := true.B } // JALR
    is("b1100011".U) { out.imm := immB; out.branch := true.B; usesRs1 := true.B; usesRs2 := true.B }
    is("b0000011".U) { // LB/LH/LW/LD and unsigned variants
      out.imm := immI; out.aluSrcImm := true.B; out.regWrite := true.B; out.memRead := true.B; out.memToReg := true.B; usesRs1 := true.B
      out.memSize := MuxLookup(funct3, 2.U(3.W))(Seq(0.U -> 0.U, 1.U -> 1.U, 2.U -> 2.U, 3.U -> 3.U, 4.U -> 0.U, 5.U -> 1.U, 6.U -> 2.U))
      out.loadUnsigned := funct3(2)
    }
    is("b0100011".U) { // SB/SH/SW/SD
      out.imm := immS; out.aluSrcImm := true.B; out.memWrite := true.B; usesRs1 := true.B; usesRs2 := true.B
      out.memSize := MuxLookup(funct3, 2.U(3.W))(Seq(0.U -> 0.U, 1.U -> 1.U, 2.U -> 2.U, 3.U -> 3.U))
    }
    is("b0010011".U) { // RV64I immediate operations
      out.imm := immI; out.aluSrcImm := true.B; out.regWrite := true.B; usesRs1 := true.B
      switch(funct3) {
        is(0.U) { out.aluOp := AluOp.add }
        is(2.U) { out.aluOp := AluOp.slt }
        is(3.U) { out.aluOp := AluOp.sltu }
        is(4.U) { out.aluOp := AluOp.xor }
        is(6.U) { out.aluOp := AluOp.or }
        is(7.U) { out.aluOp := AluOp.and }
        is(1.U) { out.aluOp := AluOp.sll; out.imm := Cat(0.U(58.W), instr(25, 20)) }
        is(5.U) { out.aluOp := Mux(instr(30), AluOp.sra, AluOp.srl); out.imm := Cat(0.U(58.W), instr(25, 20)) }
      }
    }
    is("b0110011".U) { // RV64I/M register operations
      out.regWrite := true.B; usesRs1 := true.B; usesRs2 := true.B
      switch(funct3) {
        is(0.U) { out.aluOp := Mux(funct7 === "b0100000".U, AluOp.sub, Mux(funct7 === "b0000001".U, AluOp.mul, AluOp.add)) }
        is(1.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.mulh, AluOp.sll) }
        is(2.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.mulhsu, AluOp.slt) }
        is(3.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.mulhu, AluOp.sltu); out.unsignedOp := true.B }
        is(4.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.div, AluOp.xor) }
        is(5.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.divu, Mux(funct7 === "b0100000".U, AluOp.sra, AluOp.srl)); out.unsignedOp := funct7 === "b0000001".U }
        is(6.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.rem, AluOp.or) }
        is(7.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.remu, AluOp.and); out.unsignedOp := funct7 === "b0000001".U }
      }
    }
    is("b0011011".U) { // ADDIW/SLLIW/SRLIW/SRAIW
      out.wordOp := true.B; out.imm := immI; out.aluSrcImm := true.B; out.regWrite := true.B; usesRs1 := true.B
      switch(funct3) {
        is(0.U) { out.aluOp := AluOp.add }
        is(1.U) { out.aluOp := AluOp.sll; out.imm := Cat(0.U(59.W), instr(24, 20)) }
        is(5.U) { out.aluOp := Mux(instr(30), AluOp.sra, AluOp.srl); out.imm := Cat(0.U(59.W), instr(24, 20)) }
      }
    }
    is("b0111011".U) { // ADDW/SUBW and the RV64 M word operations
      out.wordOp := true.B; out.regWrite := true.B; usesRs1 := true.B; usesRs2 := true.B
      switch(funct3) {
        is(0.U) { out.aluOp := Mux(funct7 === "b0100000".U, AluOp.sub, Mux(funct7 === "b0000001".U, AluOp.mul, AluOp.add)) }
        is(1.U) { out.aluOp := AluOp.sll }
        is(4.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.div, AluOp.xor) }
        is(5.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.divu, Mux(funct7 === "b0100000".U, AluOp.sra, AluOp.srl)); out.unsignedOp := funct7 === "b0000001".U }
        is(6.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.rem, AluOp.or) }
        is(7.U) { out.aluOp := Mux(funct7 === "b0000001".U, AluOp.remu, AluOp.and); out.unsignedOp := funct7 === "b0000001".U }
      }
    }
  }

  io.out := out
  io.rs1 := rs1
  io.rs2 := rs2
  io.usesRs1 := usesRs1
  io.usesRs2 := usesRs2
}
