# EXMEMReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-EXMEM` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/EXMEMReg.scala` |

## Purpose

保存 EX 结果和数据事务状态，使 MEM 可以跨周期等待外部响应。

## Requirements

- `REQ-EXMEM-001`: reset SHALL 将 `ExMem` 清零。
- `REQ-EXMEM-002`: enable=1 时 SHALL 捕获 `in`；enable=0 时 SHALL 保持原 bundle。
- `REQ-EXMEM-003`: `setMemIssued` SHALL 将 `memIssued` 置 1，且不得清除地址、mask 或控制字段。
- `REQ-EXMEM-004`: `setMemDone` SHALL 将 `memDone` 置 1 并捕获 `loadData`。
- `REQ-EXMEM-005`: 同周期事件的语义必须在 PipelineCore 中保持：memory request fire 设置 issued，response fire 设置 done/data；下一次 advance 才可替换该项。
