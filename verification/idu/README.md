# IDU generated assertions

This directory owns an independent iabv job for the real `IDU`. Its source of
truth is the [IDU spec](../../docs/specs/20-blocks/pipeline/idu.md) and the
[verification plan](../../docs/verification-plan/idu.md).

From the Seed root:

```bash
python3 verification/idu/run.py
```

`assertion-plan.json` maps the seven block requirements to 20 generated
assertions and 15 covers. The harness supplies only DUT wiring and input-derived
instruction classes. `sim_main.cpp` drives directed and 5,000 random instruction
words and checks public interface fields independently. `job.json` binds both
emitted LTL layers and requires a mutation to trip `REQ_ID_001_VALID`.

Each run creates a report, generated source, waveform and coverage in
`build/verification/`. A passed run is simulation evidence; formal proof is
not part of this job.
