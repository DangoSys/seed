package framework.seed.verification

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.{IFStage, IfIdBundle, PipeIMemReq, PipeIMemResp}

/** Real DUT plus independent interface history; iabv injects the properties. */
class IFUVerificationTop extends Module {
  val p = SeedParam()
  val io = IO(new Bundle {
    val redirect = Flipped(Valid(UInt(p.vaddrBits.W)))
    val stall = Input(Bool())
    val outReady = Input(Bool())
    val imem = new Bundle {
      val req = Decoupled(new PipeIMemReq(p))
      val resp = Flipped(Decoupled(new PipeIMemResp(p)))
    }
    val out = Output(new IfIdBundle(p))
  })

  val dut = Module(new IFStage(p))
  dut.io.redirect := io.redirect
  dut.io.stall := io.stall
  dut.io.outReady := io.outReady
  io.imem <> dut.io.imem
  io.out := dut.io.out

  // Model only sampled interface transactions, never the DUT's internal state.
  val expectedPc = RegInit(p.resetPc.U(p.vaddrBits.W))
  val outstanding = RegInit(false.B)
  val savedPc = RegInit(0.U(p.vaddrBits.W))
  val stale = RegInit(false.B)
  val firstFetch = RegInit(true.B)
  val redirected = RegInit(false.B)
  val historyValid = RegNext(!reset.asBool, false.B)
  val previousReset = RegNext(reset.asBool, false.B)
  val previousPc = RegNext(io.imem.req.bits.addr)
  val previousTarget = RegNext(io.redirect.bits)

  when(io.redirect.valid) {
    expectedPc := io.redirect.bits
    redirected := true.B
  }.elsewhen(io.imem.req.fire) {
    expectedPc := expectedPc +% 4.U
    redirected := false.B
  }
  when(io.imem.req.fire) {
    outstanding := true.B
    savedPc := io.imem.req.bits.addr
    firstFetch := false.B
  }
  when(outstanding && io.redirect.valid) { stale := true.B }
  when(io.imem.resp.fire) {
    outstanding := false.B
    stale := false.B
  }

  val liveResponse = outstanding && !stale && !io.redirect.valid
  val canDeliver = liveResponse && !io.stall && io.outReady
  // Generated assertions/cover properties are inserted in the isolated workspace.
}

object EmitIFUVerification extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new IFUVerificationTop,
    Array("--target-dir", "build/ifu-rtl"),
    Array("--disable-all-randomization")
  )
}
