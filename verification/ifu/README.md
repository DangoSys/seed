# IFU verification environment

This environment instantiates the real `IFU` and its internal `PCReg`.
It was prepared directly by the coding agent. The existing iabv product renders
the supplied property plan into Chisel LTL and runs the simulation job. A small
project launcher submits the emitted RTL to remote JasperGold; no new agent
framework or automatic environment-planning flow is involved.

## Files

| File | Purpose |
| --- | --- |
| `src/main/scala/IFUVerificationTop.scala` | DUT wiring and independent interface history; no handwritten Chisel properties |
| `assertion-plan.json` | 12 assertions and 23 covers rendered by iabv |
| `sim_main.cpp` | IMEM response scheduler, independent scoreboard, directed cases and random stimulus |
| `sim_protocol.cpp` | Same simulation with strict request-stability checking |
| `job.json`, `job-protocol.json` | Existing product job format |
| `manifest.json` | Mill target and RTL emission |
| `formal/environment.sv` | Two IMEM assumptions, two explicit interface assertions and two covers |
| `formal/run.tcl` | Jasper analysis, property layer binding, reset setup, prove/cover and counterexample export |
| `run_jasper.py` | SSH submission, artifact retrieval, and conservative result summary |

## Simulation

From the Seed root:

```bash
python3 verification/ifu/run.py
```

The job compiles the generated assertions, runs 30,000 random stimulus cycles
across seeds `0x5eed`, `0x1`, `0xc0ffee` plus directed scenarios, checks measured
cover hits, and requires three mutations to fail specific generated assertions
with the reference scoreboard disabled:

- PC increment +4 changed to +8: `REQ_IF_003_ADVANCE_ON_FIRE`.
- Missing stale-response suppression: `REQ_IF_005_DISCARD`.
- Response PC replaced with the advanced PC: `REQ_IF_007_PAIRING`.

The IMEM model answers only accepted requests, at delays of 1..32 cycles, and
holds a response until accepted. Request readiness is independent of DUT pending
state. Distinct instruction patterns and a transaction queue check PC/data pairing.
Reset occurs only when idle; repeated redirects before target acceptance and
redirect before the first post-reset request are excluded pending spec decisions.

The functional job records request-stability violations in `simulation.json`
without treating its functional result as full protocol signoff. Run the strict
protocol check separately:

```bash
python3 verification/ifu/run.py --protocol
```

The current RTL fails at cycle 8 with `ISSUE-IF-001`: a blocked request is withdrawn
when stall rises. The command returns nonzero and preserves the failure log.

## Remote JasperGold

Use the `build/artifacts` directory printed by a completed product run:

```bash
python3 verification/ifu/run_jasper.py \
  --rtl-dir build/verification/<simulation-run>/build/artifacts \
  --host jasper-vm
```

The SSH alias must already be configured. The validated server provides JasperGold
`2024.09p001` at `/tools/jasper_2024/bin/jg`; override the path with `--jg` if needed.
The launcher creates a unique directory under the remote user's
`seed-ifu-verification/`, uploads only the RTL/property bundle and formal files,
and retrieves reports and the request-stability counterexample VCD. Remote run
directories are retained for inspection. It uses at most two local proof jobs and
a 60-second proof time limit configured in the Tcl script.

IMEM inputs are symbolic. The assumptions require response credit from an earlier
accepted request and stability of a response under backpressure. No response-time
bound, readiness fairness, instruction value, stall, or redirect restriction is
assumed. The outstanding-request limit remains a DUT assertion.

`reset reset` initializes the design and proves post-reset behavior. Jasper marks
`REQ_IF_001_RESET_STATE` proven with an unreachable precondition; the summary
excludes it from effective proof credit. Dynamic reset and outstanding traffic
across reset are not formally proven by this task. The first-fetch property does
check the initialized reset PC. Per-cycle redirect updates are checked, but this
does not resolve the open architectural wording about repeated redirects.

The launcher checks that all expected generated properties are present. It returns
1 for counterexamples or incomplete verification, 2 for backend execution failure,
and 0 only for complete passing results with reached antecedents/covers. Raw Jasper
results are retained even when the command fails. The existing product's simulation
report keeps `formal_status: not_run`; formal results are in the separate Jasper run.

## Current evidence

See the [verification plan](../../docs/verification-plan/ifu.md) and the
[2026-10-08 evidence archive](../../docs/reviews/ifu-verification-20261008/README.md).
Functional simulation and all three mutations pass. Jasper provides 12 effective
proofs, one request-stability counterexample, and one reset property excluded for
an inactive antecedent. The module remains partially verified with a known defect.
