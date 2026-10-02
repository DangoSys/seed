# WBStage Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-WB` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/WBStage.scala` |

## 1. Purpose

从 MEM/WB 选择已完成的 result，产生 register file write enable 和提交观察点。

## 2. Requirements

- `REQ-WB-001`: `writeValid` SHALL 等于 `in.valid && in.regWrite && in.rd != 0`。
- `REQ-WB-002`: `writeAddr` SHALL 等于 `in.rd`，`writeData` SHALL 等于 `in.result`。
- `REQ-WB-003`: `retired` SHALL 等于 `in.valid`，`retiredPc` SHALL 等于 `in.pc`，包括 store、branch 和不写寄存器的指令。
- `REQ-WB-004`: WB SHALL 不修改 x0。

## 3. Limitations

当前 retire 观察点表示“进入 WB 的有效流水项”，不是带 exception/commit kill 语义的 architectural commit。异常体系引入后需要重新定义。
