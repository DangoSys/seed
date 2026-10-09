package framework.seed.verification

import chisel3._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.RegisterFile

/** Public register-file boundary with a stimulus-derived reference state. */
class RegisterFileVerificationTop extends Module {
  val p = SeedParam()
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1Data = Output(UInt(p.xLen.W))
    val rs2Data = Output(UInt(p.xLen.W))
    val writeEnable = Input(Bool())
    val writeAddr = Input(UInt(5.W))
    val writeData = Input(UInt(p.xLen.W))
  })
  val dut = Module(new RegisterFile(p))
  dut.io.rs1 := io.rs1
  dut.io.rs2 := io.rs2
  dut.io.writeEnable := io.writeEnable
  dut.io.writeAddr := io.writeAddr
  dut.io.writeData := io.writeData
  io.rs1Data := dut.io.rs1Data
  io.rs2Data := dut.io.rs2Data

  // Reference storage is updated solely from public write inputs. Assertions
  // compare the DUT's public read ports with this independent expected state.
  val expected = RegInit(VecInit(Seq.fill(32)(0.U(p.xLen.W))))
  when(io.writeEnable && io.writeAddr =/= 0.U) {
    expected(io.writeAddr) := io.writeData
  }
  val expectedRs1 = Mux(io.rs1 === 0.U, 0.U, expected(io.rs1))
  val expectedRs2 = Mux(io.rs2 === 0.U, 0.U, expected(io.rs2))
  val previousWriteData = RegNext(io.writeData)
  val previousWriteAddr = RegNext(io.writeAddr)
  val previousRs1 = RegNext(io.rs1)
  val previousRs2 = RegNext(io.rs2)
  val previousRs1Data = RegNext(io.rs1Data)
  val previousRs2Data = RegNext(io.rs2Data)
  // Generated assertions and covers are inserted in an isolated workspace.
}

object EmitRegisterFileVerification extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new RegisterFileVerificationTop,
    Array("--target-dir", "build/register-file-rtl"),
    Array("--disable-all-randomization")
  )
}
