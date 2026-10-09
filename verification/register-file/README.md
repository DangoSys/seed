# RegisterFile generated assertions

This directory owns an independent iabv job for the real `RegisterFile`.
Its source of truth is the [block spec](../../docs/specs/20-blocks/pipeline/register-file.md)
and [verification plan](../../docs/verification-plan/register-file.md).

From the Seed root:

```bash
python3 verification/register-file/run.py
```

`assertion-plan.json` maps four requirements to eight generated assertions and
eight covers. The harness holds a reference state derived only from public
write inputs. `sim_main.cpp` uses a separate C++ array to check both read
ports before and after writes, scans all 32 addresses, resets after writes and
drives 5,000 random cycles. `job.json` binds the LTL layers and requires an SV
read-port mutation to trip `REQ_REGFILE_003_WRITE1`.

Each run creates a report, generated source, waveform and coverage in
`build/verification/`. A passed run is simulation evidence; formal proof is
not part of this job.
