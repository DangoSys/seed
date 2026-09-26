package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

class IFStage(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val redirect = Flipped(Valid(UInt(p.vaddrBits.W)))
    val stall = Input(Bool())
    val consume = Input(Bool())
    val imem = new Bundle {
      val req = Decoupled(new PipeIMemReq(p))
      val resp = Flipped(Decoupled(new PipeIMemResp(p)))
    }
    val out = Output(new IfId(p))
  })

  val packet = RegInit(0.U.asTypeOf(new IfId(p)))
  val pc = RegInit(0.U(p.vaddrBits.W))
  val requestPc = RegInit(0.U(p.vaddrBits.W))
  val pending = RegInit(false.B)
  val discardResponse = RegInit(false.B)

  val canFetch = !io.stall && (!packet.valid || io.consume) && !io.redirect.valid
  io.imem.req.valid := !pending && canFetch
  io.imem.req.bits.addr := pc
  io.imem.resp.ready := pending
  io.out := packet

  when(io.imem.req.fire) {
    pending := true.B
    requestPc := pc
    pc := pc + 4.U
  }

  when(io.redirect.valid) {
    pc := io.redirect.bits
    packet.valid := false.B
    when(pending) { discardResponse := true.B }
  }.elsewhen(io.imem.resp.fire) {
    pending := false.B
    when(!discardResponse && !io.stall) {
      packet.valid := true.B
      packet.pc := requestPc
      packet.instr := io.imem.resp.bits.data
    }
    discardResponse := false.B
  }.elsewhen(io.consume && !io.stall) {
    packet.valid := false.B
  }
}
