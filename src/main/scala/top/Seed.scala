package framework.seed

import chisel3._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.PipelineCore

class Seed(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new SeedIO)
  val core = Module(new PipelineCore(p)); val bridge = Module(new AxiBridge(p))
  core.io.imem <> bridge.io.imem; core.io.dmem <> bridge.io.dmem; io.axi <> bridge.io.axi
  io.cease := false.B
}

object SeedTop extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new Seed(),
    firtoolOpts = args.drop(1),
    args = Array("--target-dir", "build")
  )
}
