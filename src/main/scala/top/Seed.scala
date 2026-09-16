package framework.seed

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

class Seed(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new SeedIO)

  io.axi.aw.valid := false.B
  io.axi.aw.bits  := DontCare
  io.axi.w.valid  := false.B
  io.axi.w.bits   := DontCare
  io.axi.b.ready  := false.B
  io.axi.ar.valid := false.B
  io.axi.ar.bits  := DontCare
  io.axi.r.ready  := false.B
  io.cease        := false.B
}

object SeedTop extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new Seed(),
    firtoolOpts = args.drop(1) ++ Seq("--split-verilog", "-o=build"),
    args = Array("--target-dir", "build")
  )
}
