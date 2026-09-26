package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

class MEMStage(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExMem(p))
    val dmem = new Bundle { val req = Decoupled(new PipeDMemReq(p)); val resp = Flipped(Decoupled(new PipeDMemResp(p))) }
    val advance = Output(Bool())
    val out = Output(new MemWb(p))
  })

  val hasMem = io.in.valid && (io.in.memRead || io.in.memWrite)
  io.dmem.req.valid := hasMem && !io.in.memIssued
  io.dmem.req.bits.addr := io.in.aluResult
  io.dmem.req.bits.wdata := io.in.storeData << (io.in.aluResult(1, 0) * 8.U)
  io.dmem.req.bits.mask := MuxLookup(io.in.memSize, "b1111".U)(Seq(
    0.U -> ("b0001".U << io.in.aluResult(1, 0)),
    1.U -> ("b0011".U << Cat(io.in.aluResult(1), 0.U(1.W))),
    2.U -> "b1111".U))
  io.dmem.req.bits.isWrite := io.in.memWrite
  io.dmem.resp.ready := hasMem && io.in.memIssued && !io.in.memDone
  io.advance := !hasMem || io.in.memDone

  val loadByte = (io.in.loadData >> (io.in.aluResult(1, 0) * 8.U))(7, 0)
  val loadHalf = (io.in.loadData >> (io.in.aluResult(1) * 16.U))(15, 0)
  val loadValue = Mux(io.in.memSize === 0.U, Mux(io.in.loadUnsigned, Cat(0.U(24.W), loadByte), Cat(Fill(24, loadByte(7)), loadByte)), Mux(io.in.memSize === 1.U, Mux(io.in.loadUnsigned, Cat(0.U(16.W), loadHalf), Cat(Fill(16, loadHalf(15)), loadHalf)), io.in.loadData))
  val out = WireDefault(0.U.asTypeOf(new MemWb(p)))
  out.valid := io.in.valid; out.pc := io.in.pc; out.rd := io.in.rd; out.regWrite := io.in.regWrite
  out.result := Mux(io.in.memToReg, loadValue, io.in.aluResult)
  io.out := out
}
