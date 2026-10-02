# Seed Top-Level Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-TOP` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/top/Seed.scala` |

## 1. Boundary

`Seed` 实例化 `PipelineCore` 和 `AxiBridge`，将内部 IF/DMEM 端口连接到 AXI master，并暴露平台接口。

| Port | Direction | Current behavior |
| --- | --- | --- |
| `axi` | master | single outstanding AXI4 |
| `mtip` | input | reserved; unused |
| `msip` | input | reserved; unused |
| `meip` | input | reserved; unused |
| `cease` | output | tied to 0 |

## 2. Requirements

- `REQ-TOP-001`: top SHALL preserve all AXI valid/ready and response backpressure semantics of `AxiBridge`。
- `REQ-TOP-002`: top SHALL not alter `retired` semantics of PipelineCore；当前 top 未将 retire 引出外部。
- `REQ-TOP-003`: `cease` 在当前 MVP SHALL 为 0；停止/退出协议需另立规格。
- `REQ-TOP-004`: interrupt inputs SHALL remain reserved until CSR/trap controller is integrated；接入前不得宣称中断已支持。
