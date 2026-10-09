# RegisterFile Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Implemented; passing simulation on the recorded baseline |
| Spec | [RegisterFile](../specs/20-blocks/pipeline/register-file.md) |
| Spec revision | `0.1` |
| DUT | `src/main/scala/pipeline/RegisterFile.scala`, `RegisterFile` |
| Parameters | `xLen=64` |
| Environment | [`verification/register-file/`](../../verification/register-file/README.md) |

## 1. Scope

对两个独立读端口、单写端口、复位、`x0` 和保持行为做模块级检查。`PipelineCore` 的依赖冒险和 forwarding 另行验证。

## 2. Method and acceptance

使用独立的软件寄存器数组作为参考模型，在时钟沿更新期望状态，并在读地址变化及每个时钟沿后比较两路读值。iabv 根据经过审阅的 JSON 计划生成 Chisel LTL；harness 另从公开写端口维护参考状态，断言两路读值及写入/保持的时序行为。

| Requirement | Check | Scenario | Status |
| --- | --- | --- | --- |
| `REQ-REGFILE-001` | `REQ_REGFILE_001_RESET` | 写入多个非零地址后复位，扫描全部地址 | Implemented |
| `REQ-REGFILE-002` | `REQ_REGFILE_002_*` | 两路同址、异址及 `x0` 组合读取 | Implemented |
| `REQ-REGFILE-003` | `REQ_REGFILE_003_*` | 非零地址写入前后在两路读端口观察旧值、新值 | Implemented |
| `REQ-REGFILE-004` | `REQ_REGFILE_004_*` | 写使能关闭及写 `x0` 时扫描保持值 | Implemented |

验收条件是全部断言和软件参考模型检查通过、8 个 cover 全部命中、遍历全部非零写地址并在 reset 后扫描全部 32 个读地址、`force_read_port_one_zero` 突变触发指定 LTL 断言。驱动包含 5,000 个随机周期，固定 seed `0x5eed` (`24301`)。从仓库根目录运行 `python3 verification/register-file/run.py`；每次运行在 `build/verification/assertion-*` 下保存生成代码、SV、波形、覆盖率、源文件哈希和报告。此环境仅提供仿真证据；报告中的 `formal_status` 应为 `not_run`。
