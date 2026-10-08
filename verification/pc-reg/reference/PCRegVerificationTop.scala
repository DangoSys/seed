package framework.seed.verification

import chisel3._
import chisel3.util._
import chisel3.ltl.{AssertProperty, CoverProperty, Sequence}
import framework.seed.configs.SeedParam
import framework.seed.pipeline.PCReg

/** Port-level properties for the real PCReg. All checks sample rising edges. */
class PCRegVerificationTop extends Module {
  val p = SeedParam()
  val io = IO(new Bundle {
    val redirect = Flipped(Valid(UInt(p.vaddrBits.W)))
    val advance = Input(Bool())
    val pc = Output(UInt(p.vaddrBits.W))
  })
  val dut = Module(new PCReg(p))
  dut.io.redirect := io.redirect
  dut.io.advance := io.advance
  io.pc := dut.io.pc

  // Uninitialized history registers are read only by next-cycle consequents.
  val previousPc = RegNext(io.pc)
  val previousTarget = RegNext(io.redirect.bits)
  def seq(b: Bool): Sequence = Sequence.BoolSequence(b)
  def nextCheck(trigger: Bool, result: Bool, id: String): Unit = {
    // No reset disable: at t+1, pre-edge PC reflects the operation sampled at t,
    // including a reset at t. A new reset at t+1 must not hide that result.
    AssertProperty(seq(trigger) |=> seq(result), clock = Some(clock),
      disable = None, label = Some(id))
  }

  nextCheck(reset.asBool, io.pc === p.resetPc.U, "REQ_PC_001_RESET")
  nextCheck(!reset.asBool && io.redirect.valid,
    io.pc === previousTarget, "REQ_PC_002_REDIRECT")
  nextCheck(!reset.asBool && !io.redirect.valid && io.advance,
    io.pc === (previousPc +% 4.U), "REQ_PC_003_ADVANCE")
  nextCheck(!reset.asBool && !io.redirect.valid && !io.advance,
    io.pc === previousPc, "REQ_PC_004_HOLD")
  nextCheck(!reset.asBool && io.redirect.valid && io.advance,
    io.pc === previousTarget, "REQ_PC_005_PRIORITY")

  Seq(
    "COV_RESET" -> reset.asBool,
    "COV_REDIRECT" -> (!reset.asBool && io.redirect.valid),
    "COV_ADVANCE" -> (!reset.asBool && !io.redirect.valid && io.advance),
    "COV_HOLD" -> (!reset.asBool && !io.redirect.valid && !io.advance),
    "COV_PRIORITY" -> (!reset.asBool && io.redirect.valid && io.advance),
    "COV_WRAP" -> (!reset.asBool && !io.redirect.valid && io.advance &&
      io.pc > ((BigInt(1) << p.vaddrBits) - 5).U)
  ).foreach { case (id, condition) =>
    CoverProperty(seq(condition), clock = Some(clock), disable = None, label = Some(id))
  }
}

object EmitPCRegVerification extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new PCRegVerificationTop,
    Array("--target-dir", "build/pc-reg-rtl"),
    Array("--disable-all-randomization")
  )
}
