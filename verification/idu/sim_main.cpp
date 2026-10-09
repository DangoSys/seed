#include "VIDUVerificationTop.h"
#include "verilated.h"
#include "verilated_cov.h"
#include "verilated_vcd_c.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <random>
#include <string>
#include <vector>

static uint32_t r(uint8_t f7, uint8_t rs2, uint8_t rs1, uint8_t f3,
                  uint8_t rd, uint8_t op) {
  return (uint32_t(f7) << 25) | (uint32_t(rs2) << 20) |
         (uint32_t(rs1) << 15) | (uint32_t(f3) << 12) |
         (uint32_t(rd) << 7) | op;
}
static uint32_t i(uint16_t imm, uint8_t rs1, uint8_t f3, uint8_t rd, uint8_t op) {
  return (uint32_t(imm & 0xfff) << 20) | (uint32_t(rs1) << 15) |
         (uint32_t(f3) << 12) | (uint32_t(rd) << 7) | op;
}
static uint32_t s(uint16_t imm, uint8_t rs2, uint8_t rs1, uint8_t f3) {
  return (uint32_t(imm & 0xfe0) << 20) | (uint32_t(rs2) << 20) |
         (uint32_t(rs1) << 15) | (uint32_t(f3) << 12) |
         (uint32_t(imm & 31) << 7) | 0x23;
}
static uint32_t b(uint16_t imm, uint8_t rs2, uint8_t rs1, uint8_t f3) {
  return (uint32_t(imm & 0x1000) << 19) | (uint32_t(imm & 0x7e0) << 20) |
         (uint32_t(rs2) << 20) | (uint32_t(rs1) << 15) |
         (uint32_t(f3) << 12) | (uint32_t(imm & 0x1e) << 7) |
         (uint32_t(imm & 0x800) >> 4) | 0x63;
}

int main(int argc, char** argv) {
  VerilatedContext context;
  context.commandArgs(argc, argv);
  context.traceEverOn(true);
  VIDUVerificationTop dut{&context};
  VerilatedVcdC trace;
  dut.trace(&trace, 99);
  trace.open("wave.vcd");
  const bool referenceCheck = !(argc > 1 && std::string(argv[1]) == "--ltl-only");
  uint64_t cycles = 0;
  uint64_t referenceChecks = 0;
  auto tick = [&](bool valid, uint32_t instr, uint64_t pc,
                  uint64_t rs1Data, uint64_t rs2Data) {
    dut.clock = 0;
    dut.reset = 0;
    dut.io_in_valid = valid;
    dut.io_in_instr = instr;
    dut.io_in_pc = pc;
    dut.io_rs1Data = rs1Data;
    dut.io_rs2Data = rs2Data;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    if (referenceCheck) {
      ++referenceChecks;
      const bool pass = dut.io_out_valid == valid && dut.io_out_pc == pc &&
          dut.io_out_rd == ((instr >> 7) & 31) &&
          dut.io_out_branchFunct3 == ((instr >> 12) & 7) &&
          dut.io_out_rs1Val == rs1Data && dut.io_out_rs2Val == rs2Data &&
          dut.io_out_rs1 == dut.io_rs1 && dut.io_out_rs2 == dut.io_rs2 &&
          dut.io_rs1 == (dut.io_usesRs1 ? ((instr >> 15) & 31) : 0) &&
          dut.io_rs2 == (dut.io_usesRs2 ? ((instr >> 20) & 31) : 0);
      if (!pass) {
        std::cerr << "REFERENCE_MISMATCH cycle=" << cycles
                  << " instr=" << std::hex << instr << std::dec << std::endl;
        return false;
      }
    }
    dut.clock = 1;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    dut.clock = 0;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    ++cycles;
    return true;
  };

#define STEP(v, ins) if (!tick(v, ins, 0x80000000ULL + cycles * 4, cycles + 7, cycles + 13)) return 1
  STEP(false, 0xffffffffU);
  STEP(true, 0x800002b7U); // LUI with negative U immediate and no sources.
  STEP(true, 0x00008197U); // AUIPC.
  STEP(true, 0x004000efU); // JAL.
  STEP(true, i(0xffc, 1, 0, 3, 0x67)); // JALR, negative I immediate.
  for (uint8_t f3 : {uint8_t(0), uint8_t(1), uint8_t(4), uint8_t(5), uint8_t(6), uint8_t(7)})
    STEP(true, b(0x1ffc, 2, 1, f3));
  for (uint8_t f3 = 0; f3 < 7; ++f3) STEP(true, i(0xfff, 1, f3, 3, 0x03));
  for (uint8_t f3 = 0; f3 < 4; ++f3) STEP(true, s(0xffc, 2, 1, f3));
  for (uint8_t f3 : {uint8_t(0), uint8_t(2), uint8_t(3), uint8_t(4), uint8_t(6), uint8_t(7)})
    STEP(true, i(0xffc, 1, f3, 3, 0x13));
  STEP(true, i(0x021, 1, 1, 3, 0x13));
  STEP(true, i(0x421, 1, 5, 3, 0x13));
  STEP(true, i(0xfff, 1, 0, 3, 0x1b));
  STEP(true, i(0x01f, 1, 1, 3, 0x1b));
  STEP(true, i(0x41f, 1, 5, 3, 0x1b));
  for (uint8_t f3 = 0; f3 < 8; ++f3) STEP(true, r(0, 2, 1, f3, 3, 0x33));
  STEP(true, r(32, 2, 1, 0, 3, 0x33));
  for (uint8_t f3 = 0; f3 < 8; ++f3) STEP(true, r(1, 2, 1, f3, 3, 0x33));
  for (uint8_t f3 : {uint8_t(0), uint8_t(1), uint8_t(5)})
    STEP(true, r(0, 2, 1, f3, 3, 0x3b));
  STEP(true, r(32, 2, 1, 5, 3, 0x3b));
  for (uint8_t f3 : {uint8_t(0), uint8_t(4), uint8_t(5), uint8_t(6), uint8_t(7)})
    STEP(true, r(1, 2, 1, f3, 3, 0x3b));
  STEP(true, i(0, 0, 0, 1, 0x03)); // Used source can be x0.
  STEP(true, i(0, 1, 7, 3, 0x03)); // Reserved load funct3.
  STEP(true, i(0, 1, 1, 3, 0x67)); // Reserved JALR funct3.
  STEP(true, i(0x041, 1, 1, 3, 0x13)); // Reserved SLLI upper bits.
  STEP(true, 0xffffffffU); // Unsupported opcode.

  std::mt19937_64 rng(0x5eed);
  for (int n = 0; n < 5000; ++n) {
    const uint32_t raw = uint32_t(rng());
    STEP((n % 17) != 0, raw);
  }
  STEP(true, i(0, 1, 0, 3, 0x03));
  STEP(true, r(0, 2, 1, 0, 3, 0x33));
  dut.final();
  context.coveragep()->write("coverage.dat");
  trace.close();
  std::ofstream report("simulation.json");
  report << "{\"status\":\"passed\",\"cycles\":" << cycles
         << ",\"reference_checks\":" << referenceChecks
         << ",\"random_seed\":24301,\"random_cycles\":5000}\n";
  std::cout << "PASS cycles=" << cycles << " reference_checks=" << referenceChecks << std::endl;
  return 0;
}
