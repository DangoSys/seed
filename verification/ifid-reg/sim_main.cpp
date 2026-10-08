#include "VIFIDRegVerificationTop.h"
#include "verilated.h"
#include "verilated_cov.h"
#include "verilated_vcd_c.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <map>
#include <random>
#include <string>

struct Entry {
  bool valid;
  uint64_t pc;
  uint32_t instr;
};

int main(int argc, char** argv) {
  VerilatedContext context;
  context.commandArgs(argc, argv);
  context.traceEverOn(true);
  VIFIDRegVerificationTop dut{&context};
  VerilatedVcdC trace;
  dut.trace(&trace, 99);
  trace.open("wave.vcd");
  const bool referenceCheck = !(argc > 1 && std::string(argv[1]) == "--ltl-only");
  Entry expected{false, 0, 0};
  uint64_t cycles = 0;
  std::map<std::string, uint64_t> hits;

  auto tick = [&](bool reset, bool flush, bool enable, Entry input) {
    dut.clock = 0;
    dut.reset = reset;
    dut.io_flush = flush;
    dut.io_enable = enable;
    dut.io_in_valid = input.valid;
    dut.io_in_pc = input.pc;
    dut.io_in_instr = input.instr;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);

    if (reset) ++hits["reset"];
    else if (flush) {
      ++hits[enable ? "flush_priority" : "flush_only"];
      if (expected.valid) ++hits["flush_valid"];
    } else if (enable) ++hits[input.valid ? "capture_valid" : "capture_invalid"];
    else ++hits[expected.valid ? "hold_valid" : "hold_invalid"];

    if (reset) expected = {false, 0, 0};
    else if (flush) expected.valid = false;
    else if (enable) expected = input;

    dut.clock = 1;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    ++cycles;
    if (referenceCheck && (dut.io_out_valid != expected.valid ||
                           dut.io_out_pc != expected.pc ||
                           dut.io_out_instr != expected.instr)) {
      std::cerr << "REFERENCE_MISMATCH cycle=" << cycles
                << " expected={" << expected.valid << "," << expected.pc << "," << expected.instr << "}"
                << " actual={" << int(dut.io_out_valid) << "," << dut.io_out_pc
                << "," << dut.io_out_instr << "}" << std::endl;
      return false;
    }
    dut.clock = 0;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    return true;
  };

#define STEP(r, f, e, v, p, i) if (!tick(r, f, e, Entry{v, p, i})) return 1
  STEP(true, true, true, true, 0x1234ULL, 0x12345678U);
  STEP(false, false, false, true, 0x1000ULL, 0x11111111U);
  STEP(false, false, true, true, 0x80000000ULL, 0xdeadbeefU);
  STEP(false, false, false, true, 0x2000ULL, 0x22222222U);
  STEP(false, true, true, true, UINT64_MAX, UINT32_MAX); // Flush wins; payload holds.
  STEP(false, false, false, false, 0, 0); // Invalid payload still holds.
  STEP(false, false, true, true, UINT64_MAX, UINT32_MAX);
  STEP(false, true, false, false, 0x3333ULL, 0x33333333U);
  STEP(false, false, true, false, 0x1234ULL, 0x56789abcU); // Capture invalid input too.
  STEP(false, false, false, true, 0x4444ULL, 0x44444444U);
  STEP(true, false, false, true, 0x5555ULL, 0x55555555U); // Mid-run reset clears all fields.

  std::mt19937_64 rng(0x5eed);
  for (int i = 0; i < 10000; ++i) {
    const uint64_t controls = rng();
    const Entry input{bool(controls & 8), rng(), uint32_t(rng())};
    if (!tick(i % 257 == 0, controls & 1, controls & 2, input)) return 1;
  }
  // Drain next-cycle implications before ending the simulation.
  STEP(false, false, false, false, 0, 0);
  STEP(false, false, false, false, 0, 0);

  for (const auto* name : {"reset", "flush_priority", "flush_only", "flush_valid",
                           "capture_valid", "capture_invalid", "hold_valid", "hold_invalid"}) {
    if (!hits[name]) { std::cerr << "Missing scenario: " << name << std::endl; return 2; }
  }
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
