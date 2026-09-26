package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** IF/ID pipeline register. */
class IFIDReg(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IfId(p))
    val enable = Input(Bool())
    val flush = Input(Bool())
    val out = Output(new IfId(p))
  })
  val reg = RegInit(0.U.asTypeOf(new IfId(p)))
  when(io.flush) { reg.valid := false.B }.elsewhen(io.enable) { reg := io.in }
  io.out := reg
}
