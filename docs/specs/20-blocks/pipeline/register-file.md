# RegisterFile Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-REGFILE` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/RegisterFile.scala` |
| Parent | [PipelineCore](pipeline-core.md) |
| Verification | [RegisterFile verification plan](../../../verification-plan/register-file.md) |

## 1. Purpose and boundary

`RegisterFile` 保存 32 个 `xLen` 位整数寄存器，提供两个组合读端口和一个时钟沿写端口。当前 `xLen=64`。IDU 提供源寄存器编号并接收读值；WBU 通过 `PipelineCore` 提供写回信息。译码、数据冒险检测和 EX forwarding 不属于本模块。

## 2. Interface and timing

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `rs1`, `rs2` | in | 5 each | 两个读地址 |
| `rs1Data`, `rs2Data` | out | `xLen` each | 当前读值，组合输出 |
| `writeEnable` | in | 1 | 写使能 |
| `writeAddr` | in | 5 | 写地址 |
| `writeData` | in | `xLen` | 写数据 |

复位将全部 32 个存储项清零。写入在时钟沿发生；未写入的项保持原值。模块没有同周期写后读旁路：在写入时钟沿之前，读地址与写地址相同仍读到旧值；时钟沿之后读到新值。

## 3. Requirements

- `REQ-REGFILE-001`: reset SHALL 将全部寄存器清零。
- `REQ-REGFILE-002`: `rs1Data/rs2Data` SHALL 分别组合读取 `rs1/rs2` 对应的当前存储值；地址为 0 时 SHALL 恒为 0。
- `REQ-REGFILE-003`: `writeEnable=1 && writeAddr!=0` 时，下一时钟沿后 `writeAddr` 指向的寄存器 SHALL 等于 `writeData`。
- `REQ-REGFILE-004`: `writeEnable=0` 或 `writeAddr=0` 时，全部寄存器 SHALL 保持原值。

## 4. Known integration limitation

`ISSUE-REGFILE-001`: 当前模块不处理同周期 WB 写入与 ID 读取同一非零寄存器的旁路。若 ID 在 WB 写入的同一周期捕获旧值，而该写入项在下一周期已离开 MEM/WB，EX forwarding 也无法修正这个值。这是现有流水线的数据相关风险；本次模块拆分保持原有时序，后续应由寄存器堆写读旁路或流水线级控制解决。
