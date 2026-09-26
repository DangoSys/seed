package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** ID/EX pipeline register. */
class IDEXReg(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IdEx(p))
    val enable = Input(Bool())
    val flush = Input(Bool())
    val out = Output(new IdEx(p))
  })
  val reg = RegInit(0.U.asTypeOf(new IdEx(p)))
  when(io.flush) { reg.valid := false.B }.elsewhen(io.enable) { reg := io.in }
  io.out := reg
}
