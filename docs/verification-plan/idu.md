# IDU Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Implemented; passing simulation on the recorded baseline |
| Spec | [IDU](../specs/20-blocks/pipeline/idu.md) |
| Spec revision | `0.4` |
| DUT | `src/main/scala/pipeline/IDU.scala`, `IDU` |
| Parameters | `xLen=vaddrBits=64` |
| Environment | [`verification/idu/`](../../verification/idu/README.md) |

## Scope and method

The iabv job renders the reviewed JSON plan into official Chisel LTL in an
isolated project copy. The harness instantiates the real IDU and derives
instruction classes from the public instruction bits. The C++ driver exercises
all supported instruction families, reserved encodings, bubbles and 5,000
random instruction words. A separate interface scoreboard checks field
pass-through and source-address gating on every cycle.

| Requirement | Assertion IDs | Directed scenarios |
| --- | --- | --- |
| `REQ-ID-001` | `REQ_ID_001_*` | valid, bubble, PC and raw data fields |
| `REQ-ID-002` | `REQ_ID_002_*` | U, J, I, S, B and RV64/RV64W shift immediates |
| `REQ-ID-003` | `REQ_ID_003_*` | every legal load/store size |
| `REQ-ID-004` | `REQ_ID_004_UNSIGNED` | signed and unsigned loads |
| `REQ-ID-005` | `REQ_ID_005_USAGE` | branch, load/store, ALU and word operations |
| `REQ-ID-006` | `REQ_ID_006_UNSUPPORTED` | unknown opcode and reserved funct3/funct7 |
| `REQ-ID-007` | `REQ_ID_007_ADDRESSES` | unused sources and a used x0 source |

Acceptance requires all assertions to hold, every declared cover to be hit,
the reference checks to pass, and the `drop_decode_valid` SV mutation to fail
at `REQ_ID_001_VALID`. The report must record `formal_status: not_run`;
simulation covers the executed inputs only. No pipeline integration claim is
made for downstream forwarding or load-use control.

## Evidence

Run `python3 verification/idu/run.py` from the repository root. The run stores
the generated Scala, emitted SV, coverage, waveform, exact source hashes and
report under a new `build/verification/assertion-*` directory. The fixed random
seed is `0x5eed` (`24301`).
