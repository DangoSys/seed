package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

class EXStage(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IdEx(p))
    val exmem = Input(new ExMem(p))
    val memwb = Input(new MemWb(p))
    val out = Output(new ExMem(p))
    val redirect = Valid(UInt(p.vaddrBits.W))
  })

  val fwdA = Mux(io.in.rs1 === 0.U, 0.U, Mux(io.exmem.valid && io.exmem.regWrite && !io.exmem.memToReg && io.exmem.rd === io.in.rs1 && io.exmem.rd =/= 0.U, io.exmem.aluResult, Mux(io.memwb.valid && io.memwb.regWrite && io.memwb.rd === io.in.rs1 && io.memwb.rd =/= 0.U, io.memwb.result, io.in.rs1Val)))
  val fwdB = Mux(io.in.rs2 === 0.U, 0.U, Mux(io.exmem.valid && io.exmem.regWrite && !io.exmem.memToReg && io.exmem.rd === io.in.rs2 && io.exmem.rd =/= 0.U, io.exmem.aluResult, Mux(io.memwb.valid && io.memwb.regWrite && io.memwb.rd === io.in.rs2 && io.memwb.rd =/= 0.U, io.memwb.result, io.in.rs2Val)))
  val aluA = Mux(io.in.usePc, io.in.pc, fwdA)
  val aluB = Mux(io.in.aluSrcImm, io.in.imm, fwdB)
  val aluResult = MuxLookup(io.in.aluOp, aluA + aluB)(Seq(
    AluOp.add -> (aluA + aluB), AluOp.sub -> (aluA - aluB), AluOp.sll -> (aluA << aluB(4, 0)),
    AluOp.slt -> (aluA.asSInt < aluB.asSInt).asUInt, AluOp.sltu -> (aluA < aluB).asUInt,
    AluOp.xor -> (aluA ^ aluB), AluOp.srl -> (aluA >> aluB(4, 0)), AluOp.sra -> (aluA.asSInt >> aluB(4, 0)).asUInt,
    AluOp.and -> (aluA & aluB), AluOp.or -> (aluA | aluB), AluOp.mul -> (aluA * aluB),
    AluOp.div -> Mux(aluB === 0.U, "hffffffff".U, (aluA.asSInt / aluB.asSInt).asUInt),
    AluOp.rem -> Mux(aluB === 0.U, aluA, (aluA.asSInt % aluB.asSInt).asUInt)))

  val branchCond = MuxLookup(io.in.branchFunct3, false.B)(Seq(
    0.U -> (fwdA === fwdB), 1.U -> (fwdA =/= fwdB), 4.U -> (fwdA.asSInt < fwdB.asSInt),
    5.U -> (fwdA.asSInt >= fwdB.asSInt), 6.U -> (fwdA < fwdB), 7.U -> (fwdA >= fwdB)))
  val taken = io.in.valid && (io.in.jump || (io.in.branch && branchCond))
  val target = (Mux(io.in.jalr, fwdA + io.in.imm, io.in.pc + io.in.imm)) & "hfffffffe".U

  val out = WireDefault(0.U.asTypeOf(new ExMem(p)))
  out.valid := io.in.valid; out.pc := io.in.pc; out.aluResult := Mux(io.in.jump, io.in.pc + 4.U, aluResult)
  out.storeData := fwdB; out.rd := io.in.rd; out.regWrite := io.in.regWrite; out.memRead := io.in.memRead; out.memWrite := io.in.memWrite
  out.memToReg := io.in.memToReg; out.memSize := io.in.memSize; out.loadUnsigned := io.in.loadUnsigned
  out.memIssued := false.B; out.memDone := !(io.in.memRead || io.in.memWrite)
  io.out := out
  io.redirect.valid := taken
  io.redirect.bits := target
}
