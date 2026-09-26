package framework.seed

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam
import framework.seed.pipeline._

/** Single-outstanding, single-beat AXI4 bridge for the pipeline ports. */
class AxiBridge(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val imem = Flipped(new Bundle { val req = Decoupled(new PipeIMemReq(p)); val resp = Flipped(Decoupled(new PipeIMemResp(p))) })
    val dmem = Flipped(new Bundle { val req = Decoupled(new PipeDMemReq(p)); val resp = Flipped(Decoupled(new PipeDMemResp(p))) })
    val axi = new SeedAxiMaster
  })
  val idle :: readWait :: writeWait :: Nil = Enum(3)
  val state = RegInit(idle); val readFromData = RegInit(false.B)
  val readAddr = RegInit(0.U(64.W))
  val dataSelected = io.dmem.req.valid; val instSelected = !dataSelected && io.imem.req.valid
  val dataRead = dataSelected && !io.dmem.req.bits.isWrite; val dataWrite = dataSelected && io.dmem.req.bits.isWrite

  io.axi.ar.valid := state===idle && (instSelected || dataRead)
  io.axi.ar.bits.addr := Mux(dataSelected,io.dmem.req.bits.addr,io.imem.req.bits.addr)
  io.axi.ar.bits.size := Mux(dataSelected, MuxLookup(io.dmem.req.bits.mask, 3.U)(Seq("b00000001".U -> 0.U, "b00000011".U -> 1.U, "b00001111".U -> 2.U, "b11111111".U -> 3.U)), 2.U)
  io.axi.ar.bits.len:=0.U; io.axi.ar.bits.burst:=1.U; io.axi.ar.bits.id:=0.U
  io.axi.aw.valid := state===idle && dataWrite; io.axi.aw.bits.addr:=io.dmem.req.bits.addr; io.axi.aw.bits.size:=MuxLookup(io.dmem.req.bits.mask, 3.U)(Seq("b00000001".U -> 0.U, "b00000011".U -> 1.U, "b00001111".U -> 2.U, "b11111111".U -> 3.U)); io.axi.aw.bits.len:=0.U; io.axi.aw.bits.burst:=1.U; io.axi.aw.bits.id:=0.U
  io.axi.w.valid := state===idle && dataWrite; io.axi.w.bits.data:=io.dmem.req.bits.wdata; io.axi.w.bits.strb:=io.dmem.req.bits.mask; io.axi.w.bits.last:=true.B

  io.imem.req.ready := state===idle && instSelected && io.axi.ar.ready
  io.dmem.req.ready := state===idle && Mux(dataWrite,io.axi.aw.ready&&io.axi.w.ready,dataRead&&io.axi.ar.ready)
  io.axi.r.ready := state===readWait && Mux(readFromData,io.dmem.resp.ready,io.imem.resp.ready)
  io.imem.resp.valid := state===readWait && !readFromData && io.axi.r.valid; io.imem.resp.bits.data:=Mux(readAddr(2), io.axi.r.bits.data(63, 32), io.axi.r.bits.data(31, 0)); io.imem.resp.bits.resp:=io.axi.r.bits.resp
  io.dmem.resp.valid := (state===readWait&&readFromData&&io.axi.r.valid)||(state===writeWait&&io.axi.b.valid)
  io.dmem.resp.bits.rdata:=Mux(state===writeWait,0.U,io.axi.r.bits.data >> (readAddr(2, 0) * 8.U)); io.dmem.resp.bits.resp:=Mux(state===writeWait,io.axi.b.bits.resp,io.axi.r.bits.resp)
  io.axi.b.ready := state===writeWait && io.dmem.resp.ready

  when(state===idle){
    when(io.axi.ar.fire){readFromData:=dataSelected;readAddr:=io.axi.ar.bits.addr;state:=readWait}
    .elsewhen(io.axi.aw.fire&&io.axi.w.fire){state:=writeWait}
  }.elsewhen(state===readWait&&io.axi.r.fire){state:=idle}
  .elsewhen(state===writeWait&&io.axi.b.fire){state:=idle}
}
