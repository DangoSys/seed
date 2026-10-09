package framework.seed.verification

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import framework.seed.pipeline.IDU
import org.scalatest.funspec.AnyFunSpec

class IDUChiselSimSpec extends AnyFunSpec with ChiselSim {
  private def r(f7: Int, rs2: Int, rs1: Int, f3: Int, rd: Int, op: Int): BigInt =
    (BigInt(f7) << 25) | (BigInt(rs2) << 20) | (BigInt(rs1) << 15) |
      (BigInt(f3) << 12) | (BigInt(rd) << 7) | BigInt(op)

  private def i(imm: Int, rs1: Int, f3: Int, rd: Int, op: Int): BigInt =
    (BigInt(imm & 0xfff) << 20) | (BigInt(rs1) << 15) |
      (BigInt(f3) << 12) | (BigInt(rd) << 7) | BigInt(op)

  describe("IDU named instruction decode") {
    it("distinguishes instructions sharing an opcode and ignores reserved encodings") {
      simulate(new IDU()) { dut =>
        def decode(instruction: BigInt): Unit = {
          dut.io.in.valid.poke(true.B)
          dut.io.in.pc.poke(0x80000000L)
          dut.io.in.instr.poke(instruction)
          dut.io.rs1Data.poke(7)
          dut.io.rs2Data.poke(9)
        }

        // U-type immediate bits must not masquerade as a source register.
        decode((BigInt(8) << 12) | (BigInt(5) << 7) | 0x37)
        dut.io.rs1.expect(0)
        dut.io.rs2.expect(0)
        dut.io.usesRs1.expect(false.B)
        dut.io.out.rs1.expect(0)
        dut.io.out.imm.expect(0x8000)
        dut.io.out.regWrite.expect(true.B)
        dut.io.out.aluSrcImm.expect(true.B)

        decode(i(-4, rs1 = 1, f3 = 0, rd = 3, op = 0x13)) // ADDI
        dut.io.rs1.expect(1)
        dut.io.rs2.expect(0)
        dut.io.out.imm.expect((BigInt(1) << 64) - 4)
        dut.io.out.aluOp.expect(0)
        dut.io.out.regWrite.expect(true.B)

        decode(r(f7 = 0x20, rs2 = 2, rs1 = 1, f3 = 0, rd = 3, op = 0x33)) // SUB
        dut.io.out.aluOp.expect(1)
        dut.io.usesRs1.expect(true.B)
        dut.io.usesRs2.expect(true.B)

        decode(r(f7 = 0x01, rs2 = 2, rs1 = 1, f3 = 0, rd = 3, op = 0x33)) // MUL
        dut.io.out.aluOp.expect(10)
        dut.io.out.regWrite.expect(true.B)

        decode(i(0x421, rs1 = 1, f3 = 5, rd = 3, op = 0x13)) // SRAI by 33
        dut.io.out.aluOp.expect(7)
        dut.io.out.imm.expect(33)

        decode(i(0x041, rs1 = 1, f3 = 1, rd = 3, op = 0x13)) // Reserved SLLI encoding
        dut.io.out.regWrite.expect(false.B)
        dut.io.usesRs1.expect(false.B)

        decode(i(0, rs1 = 1, f3 = 4, rd = 3, op = 0x03)) // LBU
        dut.io.out.memRead.expect(true.B)
        dut.io.out.memSize.expect(0)
        dut.io.out.loadUnsigned.expect(true.B)

        decode(i(0, rs1 = 1, f3 = 1, rd = 3, op = 0x67)) // Reserved JALR funct3
        dut.io.out.jump.expect(false.B)
        dut.io.out.regWrite.expect(false.B)

        decode(r(f7 = 0x01, rs2 = 2, rs1 = 1, f3 = 5, rd = 3, op = 0x3b)) // DIVUW
        dut.io.out.aluOp.expect(13)
        dut.io.out.wordOp.expect(true.B)
        dut.io.out.unsignedOp.expect(true.B)

        dut.io.in.valid.poke(false.B)
        dut.io.rs1.expect(0)
        dut.io.rs2.expect(0)
        dut.io.out.valid.expect(false.B)
        dut.io.out.regWrite.expect(false.B)
      }
    }
  }
}
