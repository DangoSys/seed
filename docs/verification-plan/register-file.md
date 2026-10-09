# RegisterFile Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Planned |
| Spec | [RegisterFile](../specs/20-blocks/pipeline/register-file.md) |
| Spec revision | `0.1` |
| DUT | `src/main/scala/pipeline/RegisterFile.scala`, `RegisterFile` |
| Parameters | `xLen=64` |
| Environment | `verification/register-file/` (planned) |

## 1. Scope

对两个独立读端口、单写端口、复位、`x0` 和保持行为做模块级检查。`PipelineCore` 的依赖冒险和 forwarding 另行验证。

## 2. Method and acceptance

使用独立的软件寄存器数组作为参考模型，在时钟沿更新期望状态，并在读地址变化及每个时钟沿后比较两路读值。

| Requirement | Check | Scenario | Status |
| --- | --- | --- | --- |
| `REQ-REGFILE-001` | `REGFILE_RESET` | 写入多个非零地址后复位，扫描全部地址 | Planned |
| `REQ-REGFILE-002` | `REGFILE_READ` | 两路同址、异址及 `x0` 组合读取 | Planned |
| `REQ-REGFILE-003` | `REGFILE_WRITE` | 非零地址写入后在两路读端口观察新值 | Planned |
| `REQ-REGFILE-004` | `REGFILE_HOLD` | 写使能关闭及写 `x0` 时扫描保持值 | Planned |

验收条件是全部场景通过且每个地址至少被读写一次。当前尚未建立可执行验证环境；没有通过记录。
