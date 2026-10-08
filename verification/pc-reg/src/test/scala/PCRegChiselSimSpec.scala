package framework.seed.verification

import chisel3.simulator.scalatest.ChiselSim
import framework.seed.configs.SeedParam
import framework.seed.pipeline.PCReg
import org.scalatest.funspec.AnyFunSpec

import scala.util.Random


/** Functional PCReg test. ChiselSim drives the DUT and checks an independent PC model. */
class PCRegChiselSimSpec extends AnyFunSpec with ChiselSim {
  describe("PCReg") {
    it("handles reset, redirect priority, hold, increment, wrap and random controls") {
      val p = SeedParam()
      val mask = (BigInt(1) << p.vaddrBits) - 1

      simulate(new PCReg(p)) { dut =>
        var expected = p.resetPc
        var cycle = 0

        dut.io.redirect.valid.poke(false)
        dut.io.redirect.bits.poke(BigInt(0))
        dut.io.advance.poke(false)
        dut.io.pc.expect(expected, "PC after the ChiselSim reset procedure")

        def step(reset: Boolean, redirect: Boolean, advance: Boolean, target: BigInt): Unit = {
          dut.reset.poke(reset)
          dut.io.redirect.valid.poke(redirect)
          dut.io.redirect.bits.poke(target & mask)
          dut.io.advance.poke(advance)
          dut.clock.step()

          expected =
            if (reset) p.resetPc
            else if (redirect) target & mask
            else if (advance) (expected + 4) & mask
            else expected
          cycle += 1
          dut.io.pc.expect(expected, s"PC after cycle $cycle")
        }

        step(reset = false, redirect = false, advance = false, target = 0)
        for (_ <- 0 until 8) step(reset = false, redirect = false, advance = true, target = 0)
        step(reset = false, redirect = false, advance = false, target = 0)
        step(reset = false, redirect = true, advance = false, target = 0x1000)
        step(reset = false, redirect = true, advance = true, target = 0x2000)
        step(reset = false, redirect = false, advance = false, target = 0x3000)
        step(reset = false, redirect = true, advance = false, target = mask - 3)
        step(reset = false, redirect = false, advance = true, target = 0)
        step(reset = true, redirect = true, advance = true, target = 0x1234)

        // Random tests
        val random = new Random(0x5eed)
        for (_ <- 0 until 1000) {
          step(
            reset = random.nextInt(257) == 0,
            redirect = random.nextBoolean(),
            advance = random.nextBoolean(),
            target = BigInt(64, random)
          )
        }
      }
    }
  }
}
