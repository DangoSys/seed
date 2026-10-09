package framework.seed.verification

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam
import framework.seed.pipeline.{IDU, IdEx, IfId}

/** Public IDU boundary. iabv inserts the reviewed properties here. */
class IDUVerificationTop extends Module {
  val p = SeedParam()
  val io = IO(new Bundle {
    val in = Input(new IfId(p))
    val rs1Data = Input(UInt(p.xLen.W))
    val rs2Data = Input(UInt(p.xLen.W))
    val rs1 = Output(UInt(5.W))
    val rs2 = Output(UInt(5.W))
    val usesRs1 = Output(Bool())
    val usesRs2 = Output(Bool())
    val out = Output(new IdEx(p))
  })
  val dut = Module(new IDU(p))
  dut.io.in := io.in
  dut.io.rs1Data := io.rs1Data
  dut.io.rs2Data := io.rs2Data
  io.rs1 := dut.io.rs1
  io.rs2 := dut.io.rs2
  io.usesRs1 := dut.io.usesRs1
  io.usesRs2 := dut.io.usesRs2
  io.out := dut.io.out

  // These independent predicates use only the instruction input, not DUT decode signals.
  val opcode = io.in.instr(6, 0)
  val funct3 = io.in.instr(14, 12)
  val funct7 = io.in.instr(31, 25)
  val isLoad = opcode === "h03".U && funct3 =/= 7.U
  val isStore = opcode === "h23".U && funct3 <= 3.U
  val isBranch = opcode === "h63".U &&
    (funct3 === 0.U || funct3 === 1.U || funct3 >= 4.U)
  val isIAlu = opcode === "h13".U &&
    (funct3 === 0.U || funct3 === 2.U || funct3 === 3.U ||
      funct3 === 4.U || funct3 === 6.U || funct3 === 7.U)
  val isShift64 = opcode === "h13".U &&
    (funct3 === 1.U && io.in.instr(31, 26) === 0.U ||
      funct3 === 5.U && (io.in.instr(31, 26) === 0.U || io.in.instr(31, 26) === 16.U))
  val isShift32 = opcode === "h1b".U &&
    (funct3 === 1.U && funct7 === 0.U ||
      funct3 === 5.U && (funct7 === 0.U || funct7 === 32.U))
  val isRegAlu = opcode === "h33".U &&
    (funct7 === 1.U || funct7 === 0.U &&
      (funct3 === 0.U || funct3 === 1.U || funct3 === 2.U ||
        funct3 === 3.U || funct3 === 4.U || funct3 === 5.U ||
        funct3 === 6.U || funct3 === 7.U) ||
      funct7 === 32.U && (funct3 === 0.U || funct3 === 5.U))
  val isWordReg = opcode === "h3b".U &&
    (funct7 === 0.U && (funct3 === 0.U || funct3 === 1.U || funct3 === 5.U) ||
      funct7 === 32.U && (funct3 === 0.U || funct3 === 5.U) ||
      funct7 === 1.U && (funct3 === 0.U || funct3 >= 4.U))
  val isSupported = opcode === "h37".U || opcode === "h17".U ||
    opcode === "h6f".U || opcode === "h67".U && funct3 === 0.U ||
    isLoad || isStore || isBranch || isIAlu || isShift64 ||
    opcode === "h1b".U && funct3 === 0.U || isShift32 || isRegAlu || isWordReg
  // Generated assertions and covers are inserted in an isolated workspace.
}

object EmitIDUVerification extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new IDUVerificationTop,
    Array("--target-dir", "build/idu-rtl"),
    Array("--disable-all-randomization")
  )
}
