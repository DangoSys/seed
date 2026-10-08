# PCReg verification

This directory has two runnable checks with different purposes:

| Check | Source and command | What a pass means |
| --- | --- | --- |
| ChiselSim functional test | `src/test/scala/PCRegChiselSimSpec.scala`; `mill pcRegVerification.test.testOnly framework.seed.verification.PCRegChiselSimSpec -- -DemitVcd=1` | The Scala reference model matched PCReg for directed and deterministic random cycles. |
| Generated assertion simulation | `assertion-plan.json`, `src/main/scala/PCRegVerificationTop.scala`, `sim_main.cpp`; `python3 verification/pc-reg/run.py` | The configured Chisel LTL assertions and covers ran in Verilator, and the configured mutation was detected. |

Both checks are simulations. The assertion job reports `formal_status: not_run`;
there is no formal proof backend for this module yet. The C++ driver remains only
for the current chisel-iabv assertion backend, which requires a C++ testbench.
It is not the PCReg functional-test entry point.

## ChiselSim functional test

Run from the Seed root:

```bash
mill pcRegVerification.test.testOnly \
  framework.seed.verification.PCRegChiselSimSpec -- -DemitVcd=1
```

ChiselSim instantiates `PCReg` directly. The Scala test checks the PC after each
clock step against an independent reference value. It exercises reset, hold,
increment, redirect, redirect priority, 64-bit wrap and 1,000 random cycles with
seed `0x5eed`. The `-DemitVcd=1` option writes `trace.vcd` under
`build/chiselsim/PCRegChiselSimSpec/`; follow the [Surfer guide](SURFER.md) to
open and inspect it. The test result and waveform belong to this ChiselSim run,
not to the assertion job below.

## Generated assertion simulation

Run from the Seed root:

```bash
python3 tools/chisel-iabv/src/product/cli.py verify \
  --project-root . --job verification/pc-reg/job.json
```

`python3 verification/pc-reg/run.py` is an equivalent convenience launcher.
All generation, build, backend, coverage and report logic lives in chisel-iabv
`src/product/`. See its README for dependencies and the job schema. Use `--output`
with a new directory to select a run location.

## Seed-owned inputs

- `job.json`: product configuration, layer bindings and the optional mutation.
- `assertion-plan.json`: explicit property semantics and requirement mappings.
- `src/main/scala/PCRegVerificationTop.scala`: real PCReg instance, ports and history
  registers; it contains no handwritten AssertProperty/CoverProperty calls.
- `sim_main.cpp`: module-specific stimuli and independent reference model.
- `manifest.json`: Mill build/emission entry.
- `reference/PCRegVerificationTop.scala`: the previous manually written LTL harness,
  retained as a reference outside the compiled source directory.

Product calls the existing ca-assertion official LTL renderer to generate Scala
from the supplied plan. It saves the raw response and generated Scala and compiles
the exact result inside a copied project workspace. The original Seed sources
are not instrumented. The plan is project-supplied; no LLM has inferred these
requirements automatically, and the report states `llm_invoked: false`.

## Sampling semantics

The five assertions map to REQ-PC-001 through REQ-PC-005: reset, redirect, +4 modulo
2^64, hold and redirect priority. Next-cycle implication samples inputs before
edge t and checks the PC before edge t+1. History registers preserve the target
and old PC from t. Reset is explicit in each antecedent; `reset_behavior: none`
means no property-disable condition, including for the reset property itself.

Six generated cover properties require nonzero measured hits. The C++ testbench
runs directed reset/priority/hold/changing-target/wrap cases and 10,000 deterministic
random cycles (seed `0x5eed`), then drains pending next-cycle checks. Arbitrary
redirect addresses are permitted by the PCReg block spec. The reference model
checks the PC after each edge.

The mutation configuration changes a copied DUT SV increment from +4 to +8 and
passes `--ltl-only` to disable the reference scoreboard for that run. Product
requires failure specifically at `REQ_PC_003_ADVANCE`. Original RTL is unchanged.

## Evidence

A run directory contains:

- `report.json`: provenance, requirement mapping, coverage and mutation outcome.
- `generation/raw-response.json`, `generation/generated.scala`: tool output.
- `workspace/`: isolated build inputs and generated harness.
- `build/report.json`: Mill result and input/artifact hashes.
- `baseline/simulation.json`, `coverage.dat`, `wave.vcd`: passing simulation evidence.
- `mutation-0/simulation.log`: expected assertion failure.

`passed` means the configured assertion simulation and mutation check passed.
It is not formal proof; `formal_status` remains `not_run`. REQ-PC-006 remains
structural review, and delayed instruction responses belong to IFU validation.
