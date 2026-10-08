#include "VIFUVerificationTop.h"
#include "verilated.h"
#include "verilated_cov.h"
#include "verilated_vcd_c.h"
#include <cstdint>
#include <deque>
#include <fstream>
#include <iostream>
#include <map>
#include <random>
#include <stdexcept>
#include <string>

#ifndef IFU_PROTOCOL_CHECK
#define IFU_PROTOCOL_CHECK 0
#endif

struct Controls {
  bool reset = false, ready = true, stall = false, outReady = true, redirect = false;
  uint64_t target = 0;
  unsigned latency = 1;
};

struct Request {
  uint64_t pc, due;
  uint32_t data;
  bool stale = false;
  unsigned delay;
};

class Simulation {
 public:
  VerilatedContext context;
  VIFUVerificationTop dut{&context};
  VerilatedVcdC trace;
  std::deque<Request> memory;
  std::map<std::string, uint64_t> hits;
  uint64_t cycles = 0, expectedPc = 0x80000000ULL, serial = 0;
  bool checkReference, blocked = false, awaitingTarget = false;
  uint64_t blockedAddress = 0;

  explicit Simulation(bool reference) : checkReference(reference) {
    context.traceEverOn(true);
    dut.trace(&trace, 99);
    trace.open("wave.vcd");
  }

  void require(bool condition, const std::string& what) {
    if (!condition) throw std::runtime_error(what + " cycle=" + std::to_string(cycles));
  }

  void tick(Controls c = {}) {
    require(c.latency >= 1 && c.latency <= 32, "ENV_INVALID_LATENCY");
    if (c.reset) require(memory.empty(), "ENV_RESET_PENDING_UNSPECIFIED");
    const bool offered = !c.reset && !memory.empty() && cycles >= memory.front().due;
    dut.clock = 0;
    dut.reset = c.reset;
    dut.io_stall = c.stall;
    dut.io_outReady = c.outReady;
    dut.io_redirect_valid = c.redirect;
    dut.io_redirect_bits = c.target;
    dut.io_imem_req_ready = c.ready && !c.reset;
    dut.io_imem_resp_valid = offered;
    dut.io_imem_resp_bits_data = offered ? memory.front().data : 0xdeadbeefU;
    dut.io_imem_resp_bits_resp = 0;
    dut.eval();
    trace.dump(context.time()); context.timeInc(1);
    const bool request = dut.io_imem_req_valid && dut.io_imem_req_ready;
    const bool response = dut.io_imem_resp_valid && dut.io_imem_resp_ready;
    const uint64_t requestPc = dut.io_imem_req_bits_addr;

    if (!c.reset) {
      if (blocked && (!dut.io_imem_req_valid || requestPc != blockedAddress)) {
        ++hits["request_stability_violations"];
        if (IFU_PROTOCOL_CHECK) {
          std::cerr << "ISSUE-IF-001: blocked request withdrawn/changed at cycle " << cycles
                    << " stall=" << c.stall << " outReady=" << c.outReady
                    << " redirect=" << c.redirect << std::endl;
          require(false, "REQUEST_STABILITY_FAILURE");
        }
      }
      const bool shouldRequest = memory.empty() && !c.stall && c.outReady && !c.redirect;
      const bool stale = !memory.empty() && (memory.front().stale || c.redirect);
      const bool shouldReady = !memory.empty() && (stale || (!c.stall && c.outReady));
      const bool shouldDeliver = offered && !stale && !c.stall && c.outReady;
      if (checkReference) {
        require(bool(dut.io_imem_req_valid) == shouldRequest, "REQ_IF_002_006_REQUEST");
        require(requestPc == expectedPc, "REQ_IF_001_003_005_PC");
        require(bool(dut.io_imem_resp_ready) == shouldReady, "REQ_IF_004_005_READY");
        require(bool(dut.io_out_valid) == shouldDeliver, "REQ_IF_004_005_DELIVERY");
        if (dut.io_out_valid) {
          require(!memory.empty(), "UNSOLICITED_DELIVERY");
          require(dut.io_out_pc == memory.front().pc, "REQ_IF_007_PC");
          require(dut.io_out_instr == memory.front().data, "REQ_IF_007_DATA");
        }
      }
      if (dut.io_imem_req_valid && !dut.io_imem_req_ready) ++hits["request_blocked"];
      if (offered && !response) {
        if (c.stall) ++hits["response_stall"];
        if (!c.outReady) ++hits["response_downstream_block"];
      }
      if (memory.empty() && (c.redirect || c.stall || !c.outReady)) {
        const unsigned mask = unsigned(c.redirect) | (unsigned(c.stall) << 1) | (unsigned(!c.outReady) << 2);
        ++hits["gate_" + std::to_string(mask)];
      }
      if (c.redirect) {
        ++hits[memory.empty() ? "redirect_idle" : "redirect_pending"];
        if (response) ++hits["redirect_response"];
        if (!memory.empty()) memory.front().stale = true;
        expectedPc = c.target;
        awaitingTarget = true;
      }
      if (response) {
        require(!memory.empty(), "ENV_UNSOLICITED_RESPONSE");
        ++hits["delay_" + std::to_string(memory.front().delay)];
        ++hits[memory.front().stale ? "discarded" : "delivered"];
        if (memory.front().stale && (c.stall || !c.outReady)) ++hits["discard_blocked"];
        memory.pop_front();
      }
      if (request) {
        // The environment accepts observed requests; it never masks DUT over-issue.
        uint32_t data = uint32_t(requestPc) ^ uint32_t(requestPc >> 32) ^ uint32_t(++serial * 0x9e3779b9ULL);
        memory.push_back({requestPc, cycles + c.latency, data, false, c.latency});
        ++hits["requests"];
        if (requestPc == UINT64_MAX - 3) ++hits["wrap"];
        if (awaitingTarget) ++hits["target_accepted"];
        if (!c.redirect) expectedPc += uint64_t{4};
        awaitingTarget = false;
      }
    } else {
      expectedPc = 0x80000000ULL;
      awaitingTarget = false;
      ++hits["reset"];
    }
    blocked = !c.reset && dut.io_imem_req_valid && !dut.io_imem_req_ready;
    blockedAddress = requestPc;
    dut.clock = 1;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    dut.clock = 0;
    dut.eval(); trace.dump(context.time()); context.timeInc(1);
    ++cycles;
  }

  void drain() {
    Controls c; c.ready = false;
    unsigned steps = 0;
    while (!memory.empty()) {
      require(++steps <= 40, "DRAIN_TIMEOUT");
      tick(c);
    }
  }

  void fetch(unsigned latency = 1) {
    require(memory.empty(), "TEST_EXPECTED_IDLE");
    Controls c; c.latency = latency;
    tick(c);
    require(!memory.empty(), "TEST_REQUEST_NOT_ACCEPTED");
  }

  void idleReset() {
    drain();
    Controls c; c.reset = true; c.ready = false;
    tick(c); tick(c);
  }

  void finish(bool passed, const std::string& failure = "") {
    dut.final();
    context.coveragep()->write("coverage.dat");
    trace.close();
    std::ofstream f("simulation.json");
    f << "{\"status\":\"" << (passed ? "passed" : "failed") << "\",\"cycles\":" << cycles
      << ",\"verification_kind\":\"ltl_simulation\",\"formal_status\":\"not_run\""
      << ",\"protocol_status\":\"" << (hits["request_stability_violations"] ? "failing" : "not_observed") << '"'
      << ",\"failure\":\"" << failure << "\",\"random_seeds\":[24301,1,12648430],\"random_cycles_per_seed\":10000"
      << ",\"scenario_hits\":{";
    bool first = true;
    for (const auto& [key, count] : hits) {
      if (!first) f << ',';
      f << '"' << key << "\":" << count;
      first = false;
    }
    f << "}}\n";
  }
};

int main(int argc, char** argv) {
  const bool reference = !(argc > 1 && std::string(argv[1]) == "--ltl-only");
  Simulation s(reference);
  s.context.commandArgs(argc, argv);
  try {
    s.idleReset();
    Controls c; c.ready = false;
    s.tick(c); s.tick(c); s.tick(c);
    s.fetch(); s.drain(); // Establish first fetch before any redirect.
    // Reproduce the known protocol defect without redefining the contract.
    s.tick(c);
    c.stall = true; s.tick(c);
    c.stall = false; s.tick(c);
    c.outReady = false; s.tick(c);
    c.outReady = true; s.tick(c);
    c.redirect = true; c.target = 0x1000; s.tick(c);
    s.fetch(); s.drain();
    for (unsigned latency : {1U, 2U, 8U, 32U}) { s.fetch(latency); s.drain(); }
    for (unsigned mode = 1; mode <= 3; ++mode) {
      s.fetch();
      Controls b; b.ready = false; b.stall = mode & 1; b.outReady = !(mode & 2);
      s.tick(b); s.tick(b); s.tick(b);
      s.drain(); // Live response must survive every form of backpressure.
      s.fetch();
      b.redirect = true; b.target = 0x2000 + mode * 0x100;
      s.tick(b); // Redirect plus response: drain even with backpressure.
      s.fetch(); s.drain();
      s.fetch(8);
      b.redirect = true; b.target += 0x40;
      s.tick(b); b.redirect = false;
      for (unsigned i = 0; i < 8; ++i) s.tick(b);
      s.drain(); s.fetch(); s.drain();
    }
    for (unsigned mask = 1; mask <= 7; ++mask) {
      Controls g; g.redirect = mask & 1; g.stall = mask & 2; g.outReady = !(mask & 4);
      g.target = 0x4000 + mask * 0x100;
      s.tick(g); s.fetch(); s.drain();
    }
    Controls wrap; wrap.redirect = true; wrap.target = UINT64_MAX - 3;
    s.tick(wrap); s.fetch(); s.drain(); s.fetch(); s.drain();
    for (uint64_t seed : {0x5eedULL, 1ULL, 0xc0ffeeULL}) {
      s.idleReset(); s.fetch(); s.drain();
      std::mt19937_64 rng(seed);
      for (unsigned i = 0; i < 10000; ++i) {
        const uint64_t bits = rng();
        Controls r;
        r.ready = bits & 1; r.stall = (bits & 6) == 6; r.outReady = bits & 8;
        // Avoid unresolved repeated-redirect semantics until a target request fires.
        r.redirect = !s.awaitingTarget && ((bits >> 8) & 31) == 0;
        r.target = rng() & ~uint64_t{3}; r.latency = 1 + ((bits >> 16) % 32);
        s.tick(r);
      }
      s.drain();
      if (s.awaitingTarget) { s.fetch(); s.drain(); }
    }
    s.drain();
    Controls end; end.ready = false; end.outReady = false;
    s.tick(end); s.tick(end); // Drain next-cycle property obligations.
    for (const auto* name : {"reset", "requests", "delivered", "discarded", "redirect_idle",
         "redirect_pending", "redirect_response", "discard_blocked", "wrap", "request_blocked",
         "response_stall", "response_downstream_block", "target_accepted", "delay_1", "delay_2", "delay_8", "delay_32"})
      s.require(s.hits[name] > 0, std::string("UNCOVERED_") + name);
    for (unsigned mask = 1; mask <= 7; ++mask) s.require(s.hits["gate_" + std::to_string(mask)] > 0, "UNCOVERED_GATES");
    s.finish(true);
    std::cout << "PASS functional_checks cycles=" << s.cycles << " protocol_issue_IF_001_observed="
              << s.hits["request_stability_violations"] << " formal_status=not_run" << std::endl;
    return 0;
  } catch (const std::exception& e) {
    std::cerr << e.what() << std::endl;
    s.finish(false, e.what());
    return 1;
  }
}
