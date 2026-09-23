package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed._
import framework.seed.configs.SeedParam

class IFPacket(val p: SeedParam) extends Bundle {
  val pc    = UInt(p.vaddrBits.W)
  val instr = UInt(p.xLen.W)
}

class IFStage(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val redirect = Flipped(Valid(UInt(p.vaddrBits.W)))
    val axi = new Bundle {
      val ar = Decoupled(new SeedAxiReadAddr)
      val r  = Flipped(Decoupled(new SeedAxiReadData))
    }
    val out = Decoupled(new IFPacket(p))
  })

  val pc          = RegInit(0.U(p.vaddrBits.W))
  val fetchPc     = Reg(UInt(p.vaddrBits.W))
  val outstanding = RegInit(false.B)
  val dropResp    = RegInit(false.B)

  val outQueue = Module(new Queue(new IFPacket(p), entries = 1, pipe = true, flow = true))

  outQueue.io.enq.valid      := false.B
  outQueue.io.enq.bits.pc    := fetchPc
  outQueue.io.enq.bits.instr := io.axi.r.bits.data
  io.out <> outQueue.io.deq

  val staleResp = dropResp || io.redirect.valid

  io.axi.ar.valid      := !io.redirect.valid && !outstanding && outQueue.io.enq.ready
  io.axi.ar.bits.addr  := pc
  io.axi.ar.bits.size  := 2.U
  io.axi.ar.bits.len   := 0.U
  io.axi.ar.bits.burst := 1.U
  io.axi.ar.bits.id    := 0.U

  io.axi.r.ready := outQueue.io.enq.ready || staleResp

  when(io.redirect.valid) {
    pc := io.redirect.bits
    when(outstanding && !io.axi.r.fire) {
      dropResp := true.B
    }
  }.elsewhen(io.axi.ar.fire) {
    fetchPc     := pc
    pc          := pc + 4.U
    outstanding := true.B
  }

  when(io.axi.r.fire) {
    outstanding := false.B
    when(staleResp) {
      dropResp := false.B
    }.otherwise {
      outQueue.io.enq.valid := true.B
    }
  }
}
