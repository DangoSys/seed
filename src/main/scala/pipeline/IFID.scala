/*************************************************************************
    > File Name: IFID.scala
    > Author: Nick
    > Email: chengni2001@gmail.com
    > Created Time: 2026-10-08 11:21:24
    > Description:
*************************************************************************/

package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** IF/ID pipeline register. */
class IFID(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new IfIdBundle(p))
    val enable = Input(Bool())
    val flush = Input(Bool())
    val out = Output(new IfIdBundle(p))
  })
  val reg = RegInit(0.U.asTypeOf(new IfIdBundle(p)))
  when(io.flush) { reg.valid := false.B }.elsewhen(io.enable) { reg := io.in }
  io.out := reg
}
