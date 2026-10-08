# Five-Stage Pipeline Subsystem Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-SS-PIPE` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/*.scala` |
| Parent | [`SEED-SYS-ARCH`](../00-system/architecture.md) |

## 1. Scope

定义 `PipelineCore` 内 IF、ID、EX、MEM、WB 五个阶段、四个流水寄存器、整数寄存器文件和全局 hazard 控制之间的契约。

## 2. Stage responsibilities

| Stage | Responsibility | State boundary |
| --- | --- | --- |
| PC/IF | `PCReg` 维护取指地址；IF 产生请求、消费响应、处理 redirect | `IFIDReg` |
| ID | 译码、立即数生成、整数寄存器读、使用源寄存器标记 | `IDEXReg` |
| EX | 旁路、ALU/M、branch/jump resolution、形成 memory request | `EXMEMReg` |
| MEM | 保持单个数据事务、形成 load value、等待响应 | `MEMWBReg` |
| WB | 写回整数寄存器、产生 retire trace | register file/outputs |

## 3. Pipeline transaction model

- `valid=0` 的流水项是 bubble；bubble 不得写寄存器、发起 memory transaction 或产生 retire。
- 非 memory 指令通常每周期推进一项。
- load/store 在 EX/MEM 中设置 `memIssued` 和 `memDone`。请求 fire 后，EX/MEM 必须保持地址、mask、store data 和控制位，直到 response fire。
- WB 没有 ready 信号；当 `MemWb.valid=1` 时，该项在该周期被视为 retired。

## 4. Hazard behavior

### 4.1 Forwarding

EX 优先使用 EX/MEM 的非 load 写回值，其次是 MEM/WB 的 result，最后使用 ID 阶段读出的寄存器值。`rd=0` 永不旁路。

### 4.2 Load-use

若 ID 当前项使用的 `rs1/rs2` 等于 ID/EX 中尚未完成的 load 的 `rd`，则：

- IF 不发出新请求；
- IF/ID 保持；
- ID/EX 捕获 bubble；
- 下一周期允许 load 继续向后推进。

### 4.3 Memory stall

当 EX/MEM 中的 load/store 尚未完成：

- IF 和 IF/ID 停止；
- ID/EX 保持；
- EX/MEM 保持；
- MEM/WB 注入 invalid；
- 数据 response fire 后，事务可推进至 WB。

### 4.4 Redirect

EX 的 taken branch/jump 产生 redirect。若没有 `memStall`，IF 采用目标 PC，IF/ID 和 ID/EX 的 `valid` 被清除。redirect 周期不发起新的 IF 请求。

## 5. Required invariants

- `x0` 读值始终为零。
- 未 fire 的 `Decoupled` 请求在 ready 变高前保持地址和 payload。
- 一个 EX/MEM memory 项最多产生一个请求 fire。
- `memDone` 之前不得向 WB 推进 memory 项。
- flush 后的年轻指令不得产生 regWrite、memory request 或 retire。
- 同一周期 data request 优先于 instruction request。

## 6. Verification plan

| Area | Directed scenarios | Assertions |
| --- | --- | --- |
| Data hazard | ALU-ALU、load-use、x0、连续依赖 | forwarding correctness、stall length |
| Control hazard | taken/not-taken branch、JAL/JALR | flush and target PC |
| Memory | delayed request/response、backpressure、byte masks | one outstanding、state hold |
| Retirement | bubbles、store、load、branch | retire valid/PC sequence |
| Reset | reset during idle and pending transaction | no spurious request/retire |
