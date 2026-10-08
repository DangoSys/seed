# IFIDReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IFID` |
| Status | `Implemented / simulation verified; formal pending` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/IFIDReg.scala` |

## Interface

| Port | Meaning |
| --- | --- |
| `in` | IF 产生的 `IfId` |
| `enable` | 正常捕获条件 |
| `flush` | 清除当前项的 valid |
| `out` | 注册后的 `IfId` |

## Requirements

- `REQ-IFID-001`: reset SHALL 将整个 bundle 清零。
- `REQ-IFID-002`: `flush` 优先级 SHALL 高于 `enable`；flush 时只需保证 `out.valid=0`，当前 RTL 其他字段保持原值。
- `REQ-IFID-003`: 无 flush 且 enable=1 时 SHALL 捕获完整输入 bundle。
- `REQ-IFID-004`: enable=0 且无 flush 时 SHALL 保持所有字段。

## Verification

The [IFIDReg verification environment](../../../../verification/ifid-reg/README.md)
uses a direct ChiselSim reference-model test and a separate chisel-iabv generated
assertion simulation. Both check all bundle fields for the four requirements;
the iabv job additionally requires seven measured covers and a flush mutation
detected by a named assertion. These are finite simulation results, not a formal
proof. The [verification plan](../../../verification-plan/ifid-reg.md) records
sampling rules, scenarios and evidence.
