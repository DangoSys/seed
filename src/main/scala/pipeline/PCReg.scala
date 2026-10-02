/*************************************************************************
    > File Name: PCReg.scala
    > Author: Nick
    > Email: chengni2001@gmail.com
    > Created Time: 2026-10-02 15:24:26
    > Description:
*************************************************************************/

package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

/** Fetch PC. Reset value comes from SeedParam.resetPc. */
class PCReg(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val redirect = Flipped(Valid(UInt(p.vaddrBits.W)))
    val advance  = Input(Bool())
    val pc       = Output(UInt(p.vaddrBits.W))
  })

  val pcReg = RegInit(p.resetPc.U(p.vaddrBits.W))
  io.pc := pcReg

  // Redirect has priority over a normal sequential update.
  when(io.redirect.valid) {
    pcReg := io.redirect.bits
  }.elsewhen(io.advance) {
    // The current MVP has fixed 32-bit instructions.
    pcReg := pcReg +% 4.U
  }
}
