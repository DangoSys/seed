package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** EX/MEM pipeline register, including the memory transaction state. */
class EXMEMReg(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExMem(p))
    val enable = Input(Bool())
    val setMemIssued = Input(Bool())
    val setMemDone = Input(Bool())
    val loadData = Input(UInt(p.xLen.W))
    val out = Output(new ExMem(p))
  })
  val reg = RegInit(0.U.asTypeOf(new ExMem(p)))
  when(io.enable) { reg := io.in }
  when(io.setMemIssued) { reg.memIssued := true.B }
  when(io.setMemDone) { reg.memDone := true.B; reg.loadData := io.loadData }
  io.out := reg
}
