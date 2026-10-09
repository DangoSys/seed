# IFIDReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IFID` |
| Status | `Implemented / simulation verified; formal pending` |
| Revision | `0.2` |
| RTL | `src/main/scala/pipeline/IFIDReg.scala`, module `IFIDReg` |
| Parent | [PipelineCore](pipeline-core.md) |
| Verification | [IFIDReg verification plan](https://github.com/DangoSys/seed/blob/main/docs/verification-plan/ifid-reg.md) |
| Last updated | 2026-10-09 |

## 1. Purpose and boundary

`IFIDReg` is the pipeline register between IF and ID. It holds one fetched instruction and its PC for one or more cycles, and invalidates it on a pipeline flush.

`IFIDReg` is responsible for:

- Capturing the IF output when enabled.
- Holding its contents while the front end is stalled.
- Invalidating its entry on flush.

`IFIDReg` is not responsible for:

- Generating `enable` and `flush` (`PipelineCore`).
- The handshake with IF; `PipelineCore` drives `IFU.outReady` from `enable`.
- Decoding the instruction (`IDU`).

## 2. Interface

Clock and reset are the implicit module `clock` and `reset` (synchronous). `vaddrBits` is currently 64.

| Port | Dir | Width | Description |
| --- | --- | ---: | --- |
| `in.valid` | in | 1 | IF delivers a valid instruction this cycle |
| `in.pc` | in | `vaddrBits` | PC of the instruction |
| `in.instr` | in | 32 | Instruction word |
| `enable` | in | 1 | Capture `in` at the next clock edge |
| `flush` | in | 1 | Invalidate the stored entry at the next clock edge |
| `out.valid` | out | 1 | The stored entry is a valid instruction |
| `out.pc` | out | `vaddrBits` | Stored PC |
| `out.instr` | out | 32 | Stored instruction |

`out` is driven directly by the register, so a value captured at a clock edge appears on `out` in the following cycle. When `out.valid=0`, `out.pc` and `out.instr` carry no meaning for downstream logic.

## 3. Timing and priority

The register updates at each rising clock edge according to the highest-priority condition:

| Priority | Condition | Next `valid` | Next `pc`, `instr` |
| --- | --- | --- | --- |
| 1 | `reset` | 0 | 0 |
| 2 | `flush=1` | 0 | Hold |
| 3 | `enable=1` | `in.valid` | `in.pc`, `in.instr` |
| 4 | Otherwise | Hold | Hold |

With `enable=1`, an input with `in.valid=0` is captured as a bubble. This is the expected behavior when ID advances and IF has no instruction.

Integration in `PipelineCore`: `enable = !frontStall && !redirect` and `flush = redirect`, so `flush` and `enable` are never both high in the current core. The priority above still applies if they are.

## 4. Reset

During and after reset, all fields are 0, so `out.valid=0`. The first capture can occur at the first clock edge after reset is released.

## 5. Requirements

- `REQ-IFID-001`: Reset SHALL set `valid`, `pc` and `instr` to 0.
- `REQ-IFID-002`: When `flush=1`, regardless of `enable`, the next `out.valid` SHALL be 0 and `out.pc` and `out.instr` SHALL retain their previous values.
- `REQ-IFID-003`: When `flush=0` and `enable=1`, the next `out` SHALL equal the current `in` in all fields.
- `REQ-IFID-004`: When `flush=0` and `enable=0`, the next `out` SHALL equal the current `out` in all fields.

## 6. Internal state

| State | Reset | Write condition |
| --- | --- | --- |
| `reg.valid` | 0 | Cleared on `flush`; loaded from `in.valid` on `enable` |
| `reg.pc` | 0 | Loaded from `in.pc` on `enable` without `flush` |
| `reg.instr` | 0 | Loaded from `in.instr` on `enable` without `flush` |

## 7. Performance

One cycle of latency from `in` to `out`. No combinational path from inputs to outputs.

## 8. Open issues and change log

| ID | Issue / change | Status |
| --- | --- | --- |
| `CHG-IFID-002` | Revision 0.2: restructured to the block template; `REQ-IFID-002` now states payload retention on flush as normative, matching the RTL and the existing verification checks. | Done |
