package framework.seed

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.IFStage

class Seed(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new SeedIO)

  val ifStage = Module(new IFStage(p))

  io.axi.aw.valid := false.B
  io.axi.aw.bits  := DontCare
  io.axi.w.valid  := false.B
  io.axi.w.bits   := DontCare
  io.axi.b.ready  := false.B

  ifStage.io.redirect.valid := false.B
  ifStage.io.redirect.bits  := DontCare
  ifStage.io.out.ready      := true.B

  io.axi.ar <> ifStage.io.axi.ar
  io.axi.r  <> ifStage.io.axi.r

  io.cease        := false.B
}

object SeedTop extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new Seed(),
    firtoolOpts = args.drop(1) ++ Seq("--split-verilog", "-o=build"),
    args = Array("--target-dir", "build")
  )
}
