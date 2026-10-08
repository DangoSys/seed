package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** Pipeline top: stage logic and every inter-stage register are separate
  * modules. This module only connects them and implements global hazards. */
class PipelineCore(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new PipelineIO(p))

  val ifStage = Module(new IFStage(p))
  val ifidReg = Module(new IFID(p))
  val idStage = Module(new IDStage(p))
  val idexReg = Module(new IDEXReg(p))
  val exStage = Module(new EXStage(p))
  val exmemReg = Module(new EXMEMReg(p))
  val memStage = Module(new MEMStage(p))
  val memwbReg = Module(new MEMWBReg(p))
  val wbStage = Module(new WBStage(p))
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(p.xLen.W))))

  ifStage.io.imem <> io.imem
  memStage.io.dmem <> io.dmem
  ifidReg.io.in := ifStage.io.out
  idStage.io.in := ifidReg.io.out
  idStage.io.rs1Data := Mux(idStage.io.rs1 === 0.U, 0.U, regs(idStage.io.rs1))
  idStage.io.rs2Data := Mux(idStage.io.rs2 === 0.U, 0.U, regs(idStage.io.rs2))
  idexReg.io.in := idStage.io.out
  exStage.io.in := idexReg.io.out
  exStage.io.exmem := exmemReg.io.out
  exStage.io.memwb := memwbReg.io.out
  exmemReg.io.in := exStage.io.out
  memStage.io.in := exmemReg.io.out
  memwbReg.io.in := memStage.io.out
  wbStage.io.in := memwbReg.io.out

  when(wbStage.io.writeValid) { regs(wbStage.io.writeAddr) := wbStage.io.writeData }

  val loadUse = ifidReg.io.out.valid && idexReg.io.out.valid && idexReg.io.out.memToReg && idexReg.io.out.rd =/= 0.U &&
    ((idStage.io.usesRs1 && idStage.io.rs1 === idexReg.io.out.rd) || (idStage.io.usesRs2 && idStage.io.rs2 === idexReg.io.out.rd))
  val memStall = exmemReg.io.out.valid && (exmemReg.io.out.memRead || exmemReg.io.out.memWrite) && !exmemReg.io.out.memDone
  val redirect = exStage.io.redirect.valid && !memStall
  val frontStall = loadUse || memStall

  ifStage.io.redirect.valid := redirect
  ifStage.io.redirect.bits := exStage.io.redirect.bits
  ifStage.io.stall := frontStall
  ifidReg.io.enable := !frontStall && !redirect
  ifStage.io.outReady := ifidReg.io.enable
  ifidReg.io.flush := redirect
  idexReg.io.enable := !memStall
  idexReg.io.flush := redirect
  when(loadUse) { idexReg.io.in.valid := false.B }
  when(redirect) { idexReg.io.in.valid := false.B }

  exmemReg.io.enable := memStage.io.advance
  exmemReg.io.setMemIssued := memStage.io.dmem.req.fire
  exmemReg.io.setMemDone := memStage.io.dmem.resp.fire
  exmemReg.io.loadData := memStage.io.dmem.resp.bits.rdata

  // A stalled memory transaction clears WB for the next cycle, then captures
  // the completed transaction when MEM becomes available again.
  when(memStage.io.advance) { memwbReg.io.in := memStage.io.out }
    .otherwise { memwbReg.io.in.valid := false.B }

  io.retired := wbStage.io.retired
  io.retiredPc := wbStage.io.retiredPc
}
