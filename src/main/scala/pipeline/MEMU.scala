package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

/** MEM stage: one outstanding 64-bit AXI data access at a time. */
class MEMU(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExMem(p))
    val dmem = new Bundle { val req = Decoupled(new PipeDMemReq(p)); val resp = Flipped(Decoupled(new PipeDMemResp(p))) }
    val advance = Output(Bool())
    val out = Output(new MemWb(p))
  })

  val hasMem = io.in.valid && (io.in.memRead || io.in.memWrite)
  io.dmem.req.valid := hasMem && !io.in.memIssued
  io.dmem.req.bits.addr := io.in.aluResult
  io.dmem.req.bits.wdata := io.in.storeData << (io.in.aluResult(2, 0) * 8.U)
  io.dmem.req.bits.mask := MuxLookup(io.in.memSize, "b11111111".U)(Seq(
    0.U -> ("b00000001".U << io.in.aluResult(2, 0)),
    1.U -> ("b00000011".U << Cat(io.in.aluResult(2, 1), 0.U(1.W))),
    2.U -> ("b00001111".U << Cat(io.in.aluResult(2), 0.U(2.W))),
    3.U -> "b11111111".U))
  io.dmem.req.bits.isWrite := io.in.memWrite
  io.dmem.resp.ready := hasMem && io.in.memIssued && !io.in.memDone
  io.advance := !hasMem || io.in.memDone

  val loadByte = (io.in.loadData >> (io.in.aluResult(2, 0) * 8.U))(7, 0)
  val loadHalf = (io.in.loadData >> (io.in.aluResult(2, 1) * 16.U))(15, 0)
  val loadWord = (io.in.loadData >> (io.in.aluResult(2) * 32.U))(31, 0)
  val loadValue = MuxLookup(io.in.memSize, io.in.loadData)(Seq(
    0.U -> Mux(io.in.loadUnsigned, Cat(0.U(56.W), loadByte), Cat(Fill(56, loadByte(7)), loadByte)),
    1.U -> Mux(io.in.loadUnsigned, Cat(0.U(48.W), loadHalf), Cat(Fill(48, loadHalf(15)), loadHalf)),
    2.U -> Mux(io.in.loadUnsigned, Cat(0.U(32.W), loadWord), Cat(Fill(32, loadWord(31)), loadWord))))

  val out = WireDefault(0.U.asTypeOf(new MemWb(p)))
  out.valid := io.in.valid
  out.pc := io.in.pc
  out.rd := io.in.rd
  out.regWrite := io.in.regWrite
  out.result := Mux(io.in.memToReg, loadValue, io.in.aluResult)
  io.out := out
}
