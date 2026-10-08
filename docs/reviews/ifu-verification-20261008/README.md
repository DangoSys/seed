# IFU verification evidence — 2026-10-08

## Scope

Real `IFStage` plus its internal `PCReg`, with project-owned interface monitors.
The existing iabv renderer generated 12 Chisel LTL assertions and 23 covers.
Input/source hashes in the reports identify the executed snapshots, including
uncommitted inputs. The plan's later status update is not part of those snapshots.
Paths in archived text use `$SEED_ROOT`; [evidence.json](evidence.json) records
original and archived file hashes. No source RTL was changed to obtain these results.

The result is **partial verification with a confirmed protocol defect**.

## E1: Functional simulation

Command: `python3 verification/ifu/run.py`.

- [Product report](simulation-report.json), [simulation counters](simulation-result.json).
- 30,193 total cycles, including 10,000 random cycles for each of seeds
  `0x5eed`, `0x1`, `0xc0ffee` and directed scenarios.
- All 23 generated cover properties hit; successful functional assertions and
  independent scoreboard checks under the documented environment.
- Three isolated mutations detected by generated assertions with the scoreboard
  disabled: [wrong increment](mutation-0.log), [stale response delivery](mutation-1.log),
  [wrong response PC](mutation-2.log).
- The functional run observed 823 request-stability violations. Its configured
  property checks pass, but that result does not establish protocol compliance.

## E2: Protocol simulation

Command: `python3 verification/ifu/run.py --protocol`.

[Product report](protocol-report.json) and [failure log](protocol-simulation.log).
The command returned 1. At cycle 8, a pending request offer was withdrawn when
stall became high, before request acceptance. This reproduces `ISSUE-IF-001`.
The early failure means this run did not execute the configured random campaign.

## E3: JasperGold

Command:

```bash
python3 verification/ifu/run_jasper.py \
  --rtl-dir build/verification/assertion-20261008-165718-611344/build/artifacts \
  --host jasper-vm
```

JasperGold `2024.09p001`, invoked through the configured SSH alias. The launcher
returned 1 because a real counterexample exists.

- [Conservative summary](jasper-summary.json), [raw results](jasper-results.json),
  [text report](jasper-report.txt), [bundle hashes](jasper-inputs.sha256.json).
- Raw result: 13 assertions proven, one assertion with a counterexample;
  40 cover goals reached, one unreachable (includes Jasper precondition goals).
- Effective proof credit: **12 assertions**, comprising 11 generated functional
  assertions and the independent outstanding-request-limit assertion.
- `REQ_IF_001_RESET_STATE` is excluded: `reset reset` initializes the design and
  deasserts reset during proof, leaving this property's trigger unreachable.
  Its dynamic-reset evidence is simulation only.
- Both IMEM assumptions are explicit in the [formal environment](../../../verification/ifu/formal/environment.sv):
  response credit and response stability. There is no finite response-delay bound
  or readiness/fairness restriction. All 23 generated covers are reachable.

### Request-stability counterexample

Property: `IFUVerificationTop.formal_environment.CHK_IF_REQUEST_STABILITY`.
The [counterexample waveform](request-stability.vcd) shows:

| Sample | req.valid | req.ready | stall | outReady | redirect.valid |
| --- | ---: | ---: | ---: | ---: | ---: |
| t | 1 | 0 | 0 | 1 | 0 |
| t+1 | 0 | 1 | 1 | 0 | 0 |

The offer was not accepted at t. It disappears at t+1 because IFU recomputes valid
from stall/outReady, violating the specified hold-until-accepted contract.
The block's request-gating requirement and the interface stability contract need
a design/spec resolution before signoff. This task reports the defect without
changing either intended behavior or the implementation.
