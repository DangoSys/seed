package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** MEM/WB pipeline register. */
class MEMWBReg(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new MemWb(p))
    val out = Output(new MemWb(p))
  })
  val reg = RegInit(0.U.asTypeOf(new MemWb(p)))
  reg := io.in
  io.out := reg
}
