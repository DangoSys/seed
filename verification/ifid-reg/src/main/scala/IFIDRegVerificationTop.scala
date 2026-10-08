package framework.seed.verification

import chisel3._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.{IFIDReg, IfId}

/** DUT boundary and sampled interface history; iabv injects the properties. */
class IFIDRegVerificationTop extends Module {
  val p = SeedParam()
  val io = IO(new Bundle {
    val in = Input(new IfId(p))
    val enable = Input(Bool())
    val flush = Input(Bool())
    val out = Output(new IfId(p))
  })

  val dut = Module(new IFIDReg(p))
  dut.io.in := io.in
  dut.io.enable := io.enable
  dut.io.flush := io.flush
  io.out := dut.io.out

  // These are sampled from the public interface, not from DUT internals.
  val previousInValid = RegNext(io.in.valid)
  val previousInPc = RegNext(io.in.pc)
  val previousInInstr = RegNext(io.in.instr)
  val previousOutValid = RegNext(io.out.valid)
  val previousOutPc = RegNext(io.out.pc)
  val previousOutInstr = RegNext(io.out.instr)
  // Generated assertions and covers are inserted in an isolated workspace.
}

object EmitIFIDRegVerification extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new IFIDRegVerificationTop,
    Array("--target-dir", "build/ifid-reg-rtl"),
    Array("--disable-all-randomization")
  )
}
