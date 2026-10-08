package framework.seed.verification

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import framework.seed.configs.SeedParam
import framework.seed.pipeline.IFIDReg
import org.scalatest.funspec.AnyFunSpec

import scala.util.Random

/** Direct IFIDReg test with an independent three-field reference model. */
class IFIDRegChiselSimSpec extends AnyFunSpec with ChiselSim {
  private case class Entry(valid: Boolean, pc: BigInt, instr: BigInt)
  private val zero = Entry(valid = false, pc = 0, instr = 0)

  describe("IFIDReg") {
    it("resets, captures, holds and flushes the complete IfId state") {
      val p = SeedParam()
      val pcMask = (BigInt(1) << p.vaddrBits) - 1
      val instrMask = (BigInt(1) << 32) - 1

      simulate(new IFIDReg(p)) { dut =>
        var expected = zero
        var cycle = 0

        def check(): Unit = {
          dut.io.out.valid.expect(expected.valid.B, s"valid after cycle $cycle")
          dut.io.out.pc.expect(expected.pc, s"pc after cycle $cycle")
          dut.io.out.instr.expect(expected.instr, s"instr after cycle $cycle")
        }

        def step(reset: Boolean, flush: Boolean, enable: Boolean, input: Entry): Unit = {
          dut.reset.poke(reset)
          dut.io.flush.poke(flush)
          dut.io.enable.poke(enable)
          dut.io.in.valid.poke(input.valid)
          dut.io.in.pc.poke(input.pc & pcMask)
          dut.io.in.instr.poke(input.instr & instrMask)
          dut.clock.step()

          expected =
            if (reset) zero
            else if (flush) expected.copy(valid = false)
            else if (enable) input.copy(pc = input.pc & pcMask, instr = input.instr & instrMask)
            else expected
          cycle += 1
          check()
        }

        dut.io.flush.poke(false)
        dut.io.enable.poke(false)
        dut.io.in.valid.poke(false)
        dut.io.in.pc.poke(0)
        dut.io.in.instr.poke(0)
        check() // ChiselSim's initial reset must clear all fields.

        val a = Entry(valid = true, pc = BigInt("80000000", 16), instr = BigInt("deadbeef", 16))
        val b = Entry(valid = true, pc = pcMask, instr = instrMask)
        val invalid = Entry(valid = false, pc = 0x1234, instr = 0x5678)
        step(reset = false, flush = false, enable = false, input = a)
        step(reset = false, flush = false, enable = true, input = a)
        step(reset = false, flush = false, enable = false, input = b)
        step(reset = false, flush = true, enable = true, input = b) // Flush wins; payload holds.
        step(reset = false, flush = false, enable = false, input = b)
        step(reset = false, flush = false, enable = true, input = b)
        step(reset = false, flush = true, enable = false, input = a)
        step(reset = false, flush = false, enable = true, input = invalid) // Even invalid input is fully captured.
        step(reset = false, flush = false, enable = false, input = a)
        step(reset = true, flush = true, enable = true, input = b) // Reset wins over both controls.
        step(reset = false, flush = false, enable = true, input = a)

        val random = new Random(0x5eed)
        for (_ <- 0 until 1000) {
          step(
            reset = random.nextInt(257) == 0,
            flush = random.nextBoolean(),
            enable = random.nextBoolean(),
            input = Entry(random.nextBoolean(), BigInt(p.vaddrBits, random), BigInt(32, random))
          )
        }
      }
    }
  }
}
