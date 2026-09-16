package framework.seed

import chisel3._
import chisel3.util._

class SeedAxiWriteAddr extends Bundle {
  val addr  = UInt(32.W)
  val size  = UInt(3.W)
  val len   = UInt(8.W)
  val burst = UInt(2.W)
  val id    = UInt(4.W)
}

class SeedAxiWriteData extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)
  val last = Bool()
}

class SeedAxiWriteResp extends Bundle {
  val resp = UInt(2.W)
  val id   = UInt(4.W)
}

class SeedAxiReadAddr extends Bundle {
  val addr  = UInt(32.W)
  val size  = UInt(3.W)
  val len   = UInt(8.W)
  val burst = UInt(2.W)
  val id    = UInt(4.W)
}

class SeedAxiReadData extends Bundle {
  val data = UInt(32.W)
  val resp = UInt(2.W)
  val last = Bool()
  val id   = UInt(4.W)
}

class SeedAxiMaster extends Bundle {
  val aw = Decoupled(new SeedAxiWriteAddr)
  val w  = Decoupled(new SeedAxiWriteData)
  val b  = Flipped(Decoupled(new SeedAxiWriteResp))
  val ar = Decoupled(new SeedAxiReadAddr)
  val r  = Flipped(Decoupled(new SeedAxiReadData))
}

class SeedIO extends Bundle {
  val axi     = new SeedAxiMaster
  val mtip    = Input(Bool())
  val msip    = Input(Bool())
  val meip    = Input(Bool())
  val cease   = Output(Bool())
}
