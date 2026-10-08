# PCReg Assertion job

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
This is not formal proof; `formal_status` remains `not_run`. REQ-PC-006 remains
structural review, and delayed instruction responses belong to IFStage validation.
