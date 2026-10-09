package framework.seed.pipeline

import chisel3._
import framework.seed.configs.SeedParam

/** Integer register file with two combinational read ports and one synchronous write port. */
class RegisterFile(val p: SeedParam = SeedParam()) extends Module {
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1Data = Output(UInt(p.xLen.W))
    val rs2Data = Output(UInt(p.xLen.W))
    val writeEnable = Input(Bool())
    val writeAddr = Input(UInt(5.W))
    val writeData = Input(UInt(p.xLen.W))
  })

  val regs = RegInit(VecInit(Seq.fill(32)(0.U(p.xLen.W))))

  io.rs1Data := Mux(io.rs1 === 0.U, 0.U, regs(io.rs1))
  io.rs2Data := Mux(io.rs2 === 0.U, 0.U, regs(io.rs2))

  when(io.writeEnable && io.writeAddr =/= 0.U) {
    regs(io.writeAddr) := io.writeData
  }
}
