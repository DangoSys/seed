# IFIDReg verification

This directory has two executable, independent simulation modes for the real
`IFIDReg`. Their common specification is the [IFIDReg block spec](../../docs/specs/20-blocks/pipeline/ifid-reg.md),
with requirement mapping in the [verification plan](../../docs/verification-plan/ifid-reg.md).

| Mode | Command from Seed root | Acceptance |
| --- | --- | --- |
| ChiselSim functional test | `mill ifidRegVerification.test.testOnly framework.seed.verification.IFIDRegChiselSimSpec -- -DemitVcd=1` | Scala model matches all three fields after every edge. |
| iabv generated assertion simulation | `python3 verification/ifid-reg/run.py` | Generated LTL assertions, seven covers, independent C++ scoreboard and a mutation all pass in Verilator. |

The ChiselSim test drives the DUT directly through directed and 1,000 random
cycles (seed `0x5eed`). Its optional VCD is under
`build/chiselsim/IFIDRegChiselSimSpec/`. The iabv job uses the project-supplied
`assertion-plan.json` and generates assertion/cover Scala in an isolated copy of
the project. `src/main/scala/IFIDRegVerificationTop.scala` supplies only DUT wiring
and sampled interface history; `sim_main.cpp` supplies directed and 10,000 random
cycles (seed `0x5eed`). `job.json` binds emitted assertion/cover layers and checks
an exact SV mutation. `manifest.json` selects the Mill build and emitter target.

The launcher is equivalent to:

```bash
python3 tools/chisel-iabv/src/product/cli.py verify \
  --project-root . --job verification/ifid-reg/job.json
```

Pass means simulation evidence for the exercised traces. The iabv report records
`formal_status: not_run`; neither mode proves the block for all input sequences.
Each iabv run writes a new directory under `build/verification/` with generated
properties, build and run reports, coverage and a waveform. Use `--output` with a
new directory to choose a run location.
