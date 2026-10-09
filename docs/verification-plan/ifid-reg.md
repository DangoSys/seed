# IFIDReg Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Passing in simulation; formal pending |
| Spec | [IFIDReg block spec](../specs/20-blocks/pipeline/ifid-reg.md) |
| Spec revision | `0.2` |
| DUT | `src/main/scala/pipeline/IFIDReg.scala`, `IFIDReg` |
| Parameters | Default `SeedParam()` (`vaddrBits=64`) |
| Environment | [verification/ifid-reg](https://github.com/DangoSys/seed/blob/main/verification/ifid-reg/README.md) |
| Last updated | 2026-10-08 |

## 1. Scope and environment

The DUT is the single IF/ID register, with `in`, `enable`, `flush`, `out`, clock
and reset exposed. The ChiselSim test instantiates the DUT directly and checks
all three output fields against an independent Scala state model after each edge.
The iabv harness instantiates the same DUT, samples only public input/output
ports, and receives generated Chisel LTL properties from the reviewed JSON plan.
A C++ driver provides directed and deterministic random input sequences plus an
independent output scoreboard for Verilator. No memory or downstream pipeline
model is needed for this register.

All Boolean control combinations and all 32-bit instruction and 64-bit PC values
are legal inputs. `valid=0` does not constrain payload: capturing an invalid
bundle still copies `pc` and `instr`. A flush clears `valid` while the RTL retains
the payload; these fields are checked because this behavior is stated in the
current block spec. Reset may occur during normal traffic and clears all fields.

## 2. Sampling semantics

For each `next_cycle` property, the antecedent samples control/input values and
the old output before an active edge; the consequent checks the registered output
after that edge. The harness history registers carry the sampled input and old
output to the consequent. Reset is an explicit antecedent in its own property.
Other transition properties require `!reset.asBool`; no implicit property disable
is used. ChiselSim and the C++ reference model apply the same priority:
`reset > flush > enable > hold`.

## 3. Requirement traceability

| Requirement | iabv assertion | ChiselSim scenario and acceptance | Cover |
| --- | --- | --- | --- |
| `REQ-IFID-001` | `REQ_IFID_001_RESET` | Initial and mid-run reset produce `valid=0, pc=0, instr=0` | `COV_IFID_RESET` |
| `REQ-IFID-002` | `REQ_IFID_002_FLUSH`, `REQ_IFID_002_PRIORITY` | Flush alone and with enable clear valid and retain old payload | `COV_IFID_FLUSH_ONLY`, `COV_IFID_FLUSH_PRIORITY` |
| `REQ-IFID-003` | `REQ_IFID_003_CAPTURE` | Enabled capture copies all fields, including an invalid input and boundary payloads | `COV_IFID_CAPTURE_VALID`, `COV_IFID_CAPTURE_INVALID` |
| `REQ-IFID-004` | `REQ_IFID_004_HOLD` | Disabled, unflushed cycle retains all fields for valid and invalid state | `COV_IFID_HOLD_VALID`, `COV_IFID_HOLD_INVALID` |

The seven generated covers each require a measured hit. The C++ driver also
requires every corresponding stimulus class. Directed cycles exercise transitions
between valid and invalid states, changing ignored inputs while holding, flush
priority, full-width values and a mid-run reset. The ChiselSim run adds 1,000
random cycles; the iabv run adds 10,000 (both seed `0x5eed`). The iabv job includes
an exact RTL mutation with its scoreboard disabled, requiring a named assertion
to fail before the job passes.

## 4. Acceptance and limitations

ChiselSim passes only when every cycle matches the independent model. The iabv
job passes only when generation, isolated compilation, baseline simulation,
seven measured covers and mutation detection all pass. Reports retain source
hashes, generated properties, simulation results, coverage and waveforms under
`build/verification/<run>/`. These are simulation checks over finite traces;
`formal_status` remains `not_run`. Integration of IFU/IDU control signals is
outside this block-level environment.

## 5. Run history

| Date | Command | Result and evidence |
| --- | --- | --- |
| 2026-10-08 | `mill ifidRegVerification.test.testOnly framework.seed.verification.IFIDRegChiselSimSpec` | Passed: 1 test, directed cases and 1,000 random cycles (`0x5eed`). |
| 2026-10-08 | `python3 verification/ifid-reg/run.py` | Passed: 10,013 cycles, all seven generated covers hit, flush mutation detected by `REQ_IFID_002_FLUSH`; local report: `build/verification/assertion-20261008-231245-320074/report.json`. |

The report records the exact project input hashes and iabv tool commit
`ae5f7a0013f5c6e9cabc3eedb9b606c9c57b3c23` (tool worktree dirty), plus
Verilator `5.046`. The plan's run-history row and the spec's verification section
were added after that run, so their current file hashes differ from the archived
input hashes; the executable inputs and RTL have not changed.
