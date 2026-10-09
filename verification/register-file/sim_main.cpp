#include "VRegisterFileVerificationTop.h"
#include "verilated.h"
#include "verilated_cov.h"
#include "verilated_vcd_c.h"

#include <array>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <random>
#include <string>

int main(int argc, char** argv) {
  VerilatedContext context;
  context.commandArgs(argc, argv);
  context.traceEverOn(true);
  VRegisterFileVerificationTop dut{&context};
  VerilatedVcdC trace;
  dut.trace(&trace, 99);
  trace.open("wave.vcd");
  const bool referenceCheck = !(argc > 1 && std::string(argv[1]) == "--ltl-only");
  std::array<uint64_t, 32> model{};
  uint64_t cycles = 0;
  uint64_t checks = 0;

  auto observe = [&](uint8_t rs1, uint8_t rs2) {
    dut.io_rs1 = rs1;
    dut.io_rs2 = rs2;
    dut.eval();
    trace.dump(context.time()); context.timeInc(1);
    if (!referenceCheck) return true;
    ++checks;
    if (dut.io_rs1Data == model[rs1] && dut.io_rs2Data == model[rs2]) return true;
    std::cerr << "REFERENCE_MISMATCH cycle=" << cycles << " rs1=" << int(rs1)
              << " rs2=" << int(rs2) << " expected=" << model[rs1] << ","
              << model[rs2] << " actual=" << dut.io_rs1Data << ","
              << dut.io_rs2Data << std::endl;
    return false;
  };

  auto tick = [&](bool reset, bool enable, uint8_t addr, uint64_t data,
                  uint8_t rs1, uint8_t rs2) {
    dut.clock = 0;
    dut.reset = reset;
    dut.io_writeEnable = enable;
    dut.io_writeAddr = addr;
    dut.io_writeData = data;
    dut.io_rs1 = rs1;
    dut.io_rs2 = rs2;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    // A same-cycle read sees the old state even when write address collides.
    if (!reset && !observe(rs1, rs2)) return false;
    dut.clock = 1;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    ++cycles;
    if (reset) model.fill(0);
    else if (enable && addr != 0) model[addr] = data;
    // Reset is still high here; the public read ports already reflect reset state.
    if (!observe(rs1, rs2)) return false;
    dut.clock = 0;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    return true;
  };

#define STEP(r, e, a, d, s1, s2) if (!tick(r, e, a, d, s1, s2)) return 1
  STEP(true, false, 0, 0, 0, 0);
  STEP(false, true, 1, 0x1111111111111111ULL, 1, 1);
  STEP(false, true, 2, 0x2222222222222222ULL, 1, 2);
  STEP(false, false, 2, 0xdeadbeefULL, 2, 1);
  STEP(false, true, 0, UINT64_MAX, 0, 2);
  for (uint8_t address = 1; address < 32; ++address)
    STEP(false, true, address, 0x1000000000000000ULL + address, address, 0);
  // A second reset checks that previously written state is cleared.
  STEP(true, true, 5, UINT64_MAX, 5, 31);
  for (uint8_t address = 0; address < 32; ++address)
    STEP(false, false, 0, 0, address, uint8_t(31 - address));

  std::mt19937_64 rng(0x5eed);
  for (int i = 0; i < 5000; ++i) {
    const uint64_t control = rng();
    const uint8_t addr = uint8_t((control >> 8) & 31);
    const uint8_t rs1 = uint8_t(control & 31);
    const uint8_t rs2 = uint8_t((control >> 16) & 31);
    STEP(i % 503 == 0, (control & 1) != 0, addr, rng(), rs1, rs2);
  }
  STEP(false, false, 0, 0, 0, 0);
  STEP(false, false, 0, 0, 1, 2);
  dut.final();
  context.coveragep()->write("coverage.dat");
  trace.close();
  std::ofstream report("simulation.json");
  report << "{\"status\":\"passed\",\"cycles\":" << cycles
         << ",\"reference_checks\":" << checks
         << ",\"random_seed\":24301,\"random_cycles\":5000}\n";
  std::cout << "PASS cycles=" << cycles << " reference_checks=" << checks << std::endl;
  return 0;
}
