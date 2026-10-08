/*************************************************************************
    > File Name: IFU.scala
    > Author: Nick
    > Email: chengni2001@gmail.com
    > Created Time: 2026-10-08 15:24:24
    > Description:
*************************************************************************/

package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

/** Instruction fetch logic. The fetched instruction is captured only by IFID. */
class IFStage(val p: SeedParam = SeedParam()) extends Module {
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

  /** Initialize the PC register. */
  val pcReg = Module(new PCReg(p))


  val requestPc = RegInit(0.U(p.vaddrBits.W))
  val pending = RegInit(false.B)
  val discardResponse = RegInit(false.B)

  val consumeResponse = pending && io.imem.resp.valid && !io.stall && io.outReady && !io.redirect.valid && !discardResponse

  /** Request the instruction from the instruction memory. */
  io.imem.req.valid := !pending && !io.stall && !io.redirect.valid && io.outReady
  pcReg.io.redirect := io.redirect
  pcReg.io.advance := io.imem.req.fire
  io.imem.req.bits.addr := pcReg.io.pc
  io.imem.resp.ready := pending && (io.redirect.valid || discardResponse || (!io.stall && io.outReady))
  io.out.valid := consumeResponse
  io.out.pc := requestPc
  io.out.instr := io.imem.resp.bits.data

  /** Track the outstanding fetch: its PC, completion, and redirect discard. */
  when(io.imem.req.fire) {
    pending := true.B
    requestPc := pcReg.io.pc
  }
  when(io.redirect.valid) {
    when(pending) { discardResponse := true.B }
  }
  when(io.imem.resp.fire) {
    pending := false.B
    discardResponse := false.B
  }
}
