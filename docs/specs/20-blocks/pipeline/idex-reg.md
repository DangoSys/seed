# IDEXReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IDEX` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/IDEXReg.scala` |

## Requirements

- `REQ-IDEX-001`: reset SHALL 将 `IdEx` 清零。
- `REQ-IDEX-002`: `flush` 优先级 SHALL 高于 `enable`，flush 时 SHALL 清除 `out.valid`。
- `REQ-IDEX-003`: enable=1 且无 flush 时 SHALL 捕获完整 `IdEx` bundle。
- `REQ-IDEX-004`: enable=0 且无 flush 时 SHALL 保持全部字段。
- `REQ-IDEX-005`: PipelineCore 对 load-use 和 redirect 注入的 bubble SHALL 通过 `in.valid=0` 到达本寄存器。
