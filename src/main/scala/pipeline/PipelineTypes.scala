package framework.seed.pipeline

import chisel3._
import chisel3.util._
import framework.seed.configs.SeedParam

class PipeIMemReq(val p: SeedParam = SeedParam()) extends Bundle { val addr = UInt(p.vaddrBits.W) }
class PipeIMemResp(val p: SeedParam = SeedParam()) extends Bundle { val data = UInt(32.W); val resp = UInt(2.W) }
class PipeDMemReq(val p: SeedParam = SeedParam()) extends Bundle {
  val addr = UInt(p.vaddrBits.W)
  val wdata = UInt(p.xLen.W)
  val mask = UInt((p.xLen / 8).W)
  val isWrite = Bool()
}
class PipeDMemResp(val p: SeedParam = SeedParam()) extends Bundle { val rdata = UInt(p.xLen.W); val resp = UInt(2.W) }

class PipelineIO(val p: SeedParam = SeedParam()) extends Bundle {
  val imem = new Bundle {
    val req = Decoupled(new PipeIMemReq(p))
    val resp = Flipped(Decoupled(new PipeIMemResp(p)))
  }
  val dmem = new Bundle {
    val req = Decoupled(new PipeDMemReq(p))
    val resp = Flipped(Decoupled(new PipeDMemResp(p)))
  }
  val retired = Output(Bool())
  val retiredPc = Output(UInt(p.vaddrBits.W))
}

object AluOp {
  val add = 0.U(5.W); val sub = 1.U(5.W); val sll = 2.U(5.W); val slt = 3.U(5.W); val sltu = 4.U(5.W)
  val xor = 5.U(5.W); val srl = 6.U(5.W); val sra = 7.U(5.W); val and = 8.U(5.W); val or = 9.U(5.W)
  val mul = 10.U(5.W); val div = 11.U(5.W); val rem = 12.U(5.W); val divu = 13.U(5.W); val remu = 14.U(5.W)
  val mulh = 15.U(5.W); val mulhsu = 16.U(5.W); val mulhu = 17.U(5.W)
}

class IfIdBundle(val p: SeedParam) extends Bundle {
  val valid = Bool(); val pc = UInt(p.vaddrBits.W); val instr = UInt(32.W)
}

class IdEx(val p: SeedParam) extends Bundle {
  val valid = Bool(); val pc = UInt(p.vaddrBits.W); val rs1 = UInt(5.W); val rs2 = UInt(5.W); val rd = UInt(5.W)
  val rs1Val = UInt(p.xLen.W); val rs2Val = UInt(p.xLen.W); val imm = UInt(p.xLen.W); val aluOp = UInt(5.W)
  val aluSrcImm = Bool(); val usePc = Bool(); val regWrite = Bool(); val memRead = Bool(); val memWrite = Bool(); val memToReg = Bool()
  val branch = Bool(); val jump = Bool(); val jalr = Bool(); val branchFunct3 = UInt(3.W); val memSize = UInt(3.W); val loadUnsigned = Bool(); val wordOp = Bool(); val unsignedOp = Bool()
}

class ExMem(val p: SeedParam) extends Bundle {
  val valid = Bool(); val pc = UInt(p.vaddrBits.W); val aluResult = UInt(p.xLen.W); val storeData = UInt(p.xLen.W); val rd = UInt(5.W)
  val regWrite = Bool(); val memRead = Bool(); val memWrite = Bool(); val memToReg = Bool(); val memSize = UInt(3.W); val loadUnsigned = Bool()
  val memIssued = Bool(); val memDone = Bool(); val loadData = UInt(p.xLen.W)
}

class MemWb(val p: SeedParam) extends Bundle {
  val valid = Bool(); val pc = UInt(p.vaddrBits.W); val result = UInt(p.xLen.W); val rd = UInt(5.W); val regWrite = Bool()
}
