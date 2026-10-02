# PipelineCore Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-PIPECORE` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/PipelineCore.scala` |
| Verification | TBD |

## 1. Purpose and boundary

`PipelineCore` 实例化五个 stage、四个流水寄存器、32×64 integer register file，并连接 IF/DMEM 请求/响应端口。AXI 协议不属于本 block，由 `AxiBridge` 负责。

## 2. Interface

| Interface | Direction | Protocol |
| --- | --- | --- |
| `io.imem.req` | output | `Decoupled[PipeIMemReq]` |
| `io.imem.resp` | input | `Decoupled[PipeIMemResp]` |
| `io.dmem.req` | output | `Decoupled[PipeDMemReq]` |
| `io.dmem.resp` | input | `Decoupled[PipeDMemResp]` |
| `io.retired` | output | one-cycle commit observation |
| `io.retiredPc` | output | PC associated with `retired` |

## 3. Requirements

- `REQ-PIPECORE-001`: 对每个 valid 指令，core SHALL 按 IF→ID→EX→MEM→WB 顺序推进，除非 stall 或 flush 条件阻止推进。
- `REQ-PIPECORE-002`: core SHALL 对 EX/MEM 非 load 和 MEM/WB 提供旁路，且 EX/MEM 优先级更高。
- `REQ-PIPECORE-003`: load-use 条件 SHALL 只冻结前端并向 ID/EX 插入一个 bubble。
- `REQ-PIPECORE-004`: 未完成的 EX/MEM memory transaction SHALL 保持到 response fire；期间不得接受年轻指令进入 EX/MEM。
- `REQ-PIPECORE-005`: taken redirect SHALL 清除 IF/ID 和 ID/EX 的 `valid`；若同时 `memStall`，当前实现 SHALL 暂不 redirect。
- `REQ-PIPECORE-006`: WB SHALL 只在 `valid && regWrite && rd!=0` 时写寄存器；`retired` SHALL 等于 `MemWb.valid`。

## 4. Register file

寄存器数组在 reset 时清零。ID 读 `rs1/rs2` 时对寄存器 0 强制返回 0；WB 对 `rd=0` 不产生写入。当前未实现同周期写后读的独立 bypass，因为 EX 还有 MEM/WB forwarding。

## 5. Control implementation

| Signal | Meaning |
| --- | --- |
| `loadUse` | ID 使用 ID/EX load 的 destination |
| `memStall` | EX/MEM memory item 未完成 |
| `redirect` | EX redirect 且无 `memStall` |
| `frontStall` | `loadUse || memStall` |

## 6. Known limitations

- 没有 exception kill、CSR 或精确 trap 状态。
- `Pipe*Resp.resp` 被传递但未消费。
- 未识别 instruction 仍可产生 retire。
- 仅支持一个整数流水和一个共享 memory master。

## 7. Verification acceptance

至少覆盖：依赖链、load-use、load/store delayed response、taken redirect 与 pending IF response、memory stall 与 branch 同时发生、x0 写入抑制和连续 retire PC。
