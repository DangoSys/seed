# Seed System Requirements Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-SYS-REQ` |
| Status | `Baselined for MVP / Implementation in progress` |
| Revision | `0.1` |
| Source | Root `README.md`, current RTL, project MVP |
| Last updated | 2026-10-02 |

## 1. Scope

Seed 是一个以 Chisel 实现的单核 RV64 处理器。本版规格定义五级、顺序发射、单发射流水线的 MVP 行为。它不提前承诺尚未实现的操作系统支持功能。

## 2. Architectural requirements

| ID | Requirement | Status |
| --- | --- | --- |
| `REQ-SYS-001` | 处理器 SHALL 使用 64-bit 整数寄存器和 64-bit 虚拟地址字段。 | Implemented |
| `REQ-SYS-002` | 处理器 SHALL 采用 IF/ID/EX/MEM/WB 五级顺序流水线。 | Implemented |
| `REQ-SYS-003` | `x0` SHALL 读为零，且任何指令 SHALL 不得通过 WB 修改 `x0`。 | Implemented |
| `REQ-SYS-004` | 当前 MVP SHALL 支持已在 `IDStage` 解码表中的 RV64I、RV64M 和 RV64W 指令。 | Partially implemented |
| `REQ-SYS-005` | 当前 MVP SHALL 支持最多一个未完成的指令或数据外部事务。 | Implemented |
| `REQ-SYS-006` | 分支和跳转 SHALL 在 EX 阶段解析；taken redirect SHALL flush IF/ID 和 ID/EX 中的年轻指令。 | Implemented |
| `REQ-SYS-007` | load-use 依赖 SHALL 产生一个前端停顿和一个 ID/EX bubble。 | Implemented |
| `REQ-SYS-008` | 外部数据访问 SHALL 支持 byte、halfword、word 和 doubleword mask；load SHALL 按有符号/无符号扩展。 | Implemented |
| `REQ-SYS-009` | 处理器 SHALL 提供提交观察点 `retired` 和 `retiredPc`。 | Implemented |
| `REQ-SYS-010` | CSR、特权级、异常/中断、MMU、cache、A/D bit 和原子操作不属于当前实现基线。 | Explicit limitation |

## 3. Software-visible behavior

- 指令宽度固定为 32 bit，PC 按 4 byte 递增。
- JAL/JALR 的 link 值为 `PC+4`。
- JALR 目标地址 bit 0 清零；其他对齐和访问权限检查当前未实现。
- 未识别 opcode 在当前 RTL 中不会产生 trap；它会以 `valid` 进入流水线并最终产生一次 retire 观察。该行为是已知限制，后续必须增加 illegal-instruction 处理。

## 4. Memory model and platform assumptions

- IF 和数据路径由 `AxiBridge` 共享一个 AXI4 master。
- 访问是单 beat、固定 ID=0、`len=0`、INCR burst；一次最多一个 outstanding transaction。
- AXI `resp` 错误字段当前未转换为处理器异常；平台需在 MVP 验证中提供 OKAY 响应。
- 当前未提供 cache、TLB、页表遍历或多 master 原子性保证。

## 5. Acceptance criteria

1. RV64I/RV64M/RV64W 指令级测试能够观察到正确的 retire PC 和寄存器结果。
2. 分支 taken/not-taken、JAL、JALR、load-use、load/store backpressure 和 AXI response backpressure 均有 directed test。
3. 设计在无外部错误响应、单 outstanding 平台约束下通过子系统验收。

## 6. Deferred requirements

- `REQ-SYS-D001`: CSR、trap、`ECALL`、`MRET` 和机器计时器中断。
- `REQ-SYS-D002`: S/U mode、Sv39、权限检查、页故障和 `SFENCE.VMA`。
- `REQ-SYS-D003`: LR/SC、AMO、`FENCE`、`FENCE.I`。
- `REQ-SYS-D004`: cache、预取、多个 outstanding transaction 和多 master 一致性。
