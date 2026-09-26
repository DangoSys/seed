package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** Pipeline top: stage modules are connected by the four IF/ID, ID/EX,
  * EX/MEM, and MEM/WB registers. Hazard control remains here because it
  * observes more than one adjacent stage. */
class PipelineCore(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new PipelineIO(p))

  val ifStage = Module(new IFStage(p))
  val idStage = Module(new IDStage(p))
  val exStage = Module(new EXStage(p))
  val memStage = Module(new MEMStage(p))
  val wbStage = Module(new WBStage(p))

  val idex = RegInit(0.U.asTypeOf(new IdEx(p)))
  val exmem = RegInit(0.U.asTypeOf(new ExMem(p)))
  val memwb = RegInit(0.U.asTypeOf(new MemWb(p)))
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(p.xLen.W))))

  ifStage.io.imem <> io.imem
  memStage.io.dmem <> io.dmem
  idStage.io.in := ifStage.io.out
  idStage.io.rs1Data := Mux(idStage.io.rs1 === 0.U, 0.U, regs(idStage.io.rs1))
  idStage.io.rs2Data := Mux(idStage.io.rs2 === 0.U, 0.U, regs(idStage.io.rs2))
  exStage.io.in := idex
  exStage.io.exmem := exmem
  exStage.io.memwb := memwb
  memStage.io.in := exmem
  wbStage.io.in := memwb

  when(wbStage.io.writeValid) { regs(wbStage.io.writeAddr) := wbStage.io.writeData }

  val loadUse = ifStage.io.out.valid && idex.valid && idex.memToReg && idex.rd =/= 0.U &&
    ((idStage.io.usesRs1 && idStage.io.rs1 === idex.rd) || (idStage.io.usesRs2 && idStage.io.rs2 === idex.rd))
  val memStall = exmem.valid && (exmem.memRead || exmem.memWrite) && !exmem.memDone
  val redirect = exStage.io.redirect.valid && !memStall
  val frontStall = loadUse || memStall

  ifStage.io.redirect.valid := redirect
  ifStage.io.redirect.bits := exStage.io.redirect.bits
  ifStage.io.stall := frontStall
  ifStage.io.consume := !frontStall && !redirect && ifStage.io.out.valid

  when(memStage.io.dmem.req.fire) { exmem.memIssued := true.B }
  when(memStage.io.dmem.resp.fire) {
    exmem.memDone := true.B
    exmem.loadData := memStage.io.dmem.resp.bits.rdata
  }

  when(memStage.io.advance) {
    memwb := memStage.io.out
    exmem := exStage.io.out
  }.otherwise {
    memwb.valid := false.B
  }

  when(!memStall) {
    when(redirect || loadUse) {
      idex.valid := false.B
    }.otherwise {
      idex := idStage.io.out
    }
  }

  io.retired := wbStage.io.retired
  io.retiredPc := wbStage.io.retiredPc
}
