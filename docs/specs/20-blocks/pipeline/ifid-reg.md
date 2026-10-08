# IFID Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IFID` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/IFID.scala` |

## Interface

| Port | Meaning |
| --- | --- |
| `in` | IF 产生的 `IfIdBundle` |
| `enable` | 正常捕获条件 |
| `flush` | 清除当前项的 valid |
| `out` | 注册后的 `IfIdBundle` |

## Requirements

- `REQ-IFID-001`: reset SHALL 将整个 bundle 清零。
- `REQ-IFID-002`: `flush` 优先级 SHALL 高于 `enable`；flush 时只需保证 `out.valid=0`，当前 RTL 其他字段保持原值。
- `REQ-IFID-003`: 无 flush 且 enable=1 时 SHALL 捕获完整输入 bundle。
- `REQ-IFID-004`: enable=0 且无 flush 时 SHALL 保持所有字段。
