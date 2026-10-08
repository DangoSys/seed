#include "VPCRegVerificationTop.h"
#include "verilated.h"
#include "verilated_cov.h"
#include "verilated_vcd_c.h"
#include <cstdint>
#include <fstream>
#include <iostream>
#include <map>
#include <random>
#include <string>

int main(int argc, char** argv) {
  VerilatedContext context;
  context.commandArgs(argc, argv);
  context.traceEverOn(true);
  VPCRegVerificationTop dut{&context};
  VerilatedVcdC trace;
  dut.trace(&trace, 99);
  trace.open("wave.vcd");
  const bool referenceCheck = !(argc > 1 && std::string(argv[1]) == "--ltl-only");
  const uint64_t resetPc = 0x80000000ULL;
  uint64_t expected = 0, cycles = 0;
  std::map<std::string, uint64_t> hits;
  auto tick = [&](bool reset, bool redirect, bool advance, uint64_t target) {
    dut.clock = 0;
    dut.reset = reset;
    dut.io_redirect_valid = redirect;
    dut.io_advance = advance;
    dut.io_redirect_bits = target;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    if (reset) ++hits["reset"];
    else if (redirect) {
      ++hits["redirect"];
      if (advance) ++hits["priority"];
    } else if (advance) {
      ++hits["advance"];
      if (expected > UINT64_MAX - 4) ++hits["wrap"];
    } else ++hits["hold"];
    if (reset) expected = resetPc;
    else if (redirect) expected = target;
    else if (advance) expected += uint64_t{4};
    dut.clock = 1;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    ++cycles;
    if (referenceCheck && dut.io_pc != expected) {
      std::cerr << "REFERENCE_MISMATCH cycle=" << cycles << " expected=" << expected
                << " actual=" << dut.io_pc << std::endl;
      return false;
    }
    dut.clock = 0;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    return true;
  };
  #define STEP(r, d, a, t) if (!tick(r, d, a, t)) return 1
  STEP(true, true, true, 0x1234); // Reset wins over both controls.
  STEP(true, false, false, 0);
  STEP(false, false, false, 0);
  for (int i = 0; i < 8; ++i) { STEP(false, false, true, 0); }
  for (int i = 0; i < 8; ++i) { STEP(false, false, false, i); }
  STEP(false, true, false, 0x1000);
  STEP(false, true, true, 0x2000);
  STEP(false, false, false, 0x3000); // Target changes after redirect sampling.
  STEP(false, true, false, UINT64_MAX - 3);
  STEP(false, false, true, 0); // Wrap to zero.
  STEP(true, true, true, 0); // Mid-run reset.
  std::mt19937_64 rng(0x5eed);
  for (int i = 0; i < 10000; ++i) {
    const uint64_t controls = rng(), target = rng();
    STEP(i % 257 == 0, controls & 1, controls & 2, target);
  }
  // Drain the last next-cycle consequent before ending the simulation.
  STEP(false, false, false, 0);
  STEP(false, false, false, 0);
  for (const auto* name : {"reset", "redirect", "priority", "advance", "hold", "wrap"})
    if (!hits[name]) { std::cerr << "Missing scenario: " << name << std::endl; return 2; }
  dut.final();
  context.coveragep()->write("coverage.dat");
  trace.close();
  std::ofstream report("simulation.json");
  report << "{\"status\":\"passed\",\"cycles\":" << cycles
         << ",\"random_seed\":24301,\"random_cycles\":10000,\"scenario_hits\":{";
  bool first = true;
  for (const auto& item : hits) {
    if (!first) report << ',';
    report << '\"' << item.first << "\":" << item.second;
    first = false;
  }
  report << "}}\n";
  std::cout << "PASS cycles=" << cycles << " random_seed=24301" << std::endl;
  return 0;
}
