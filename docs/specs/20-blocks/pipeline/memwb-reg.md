# MEMWBReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-MEMWB` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/MEMWBReg.scala` |

## Requirements

- `REQ-MEMWB-001`: reset SHALL 将 `MemWb` 清零。
- `REQ-MEMWB-002`: 每个时钟周期 SHALL 捕获 `in` 的完整 bundle。
- `REQ-MEMWB-003`: PipelineCore 在 memory stall 周期 SHALL 将输入 valid 清零，避免旧结果重复写回或重复 retire。
