package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

class WBU(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new MemWb(p))
    val writeValid = Output(Bool())
    val writeAddr = Output(UInt(5.W))
    val writeData = Output(UInt(p.xLen.W))
    val retired = Output(Bool())
    val retiredPc = Output(UInt(p.vaddrBits.W))
  })
  io.writeValid := io.in.valid && io.in.regWrite && io.in.rd =/= 0.U
  io.writeAddr := io.in.rd; io.writeData := io.in.result
  io.retired := io.in.valid; io.retiredPc := io.in.pc
}
