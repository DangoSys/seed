# Seed 设计规格文档

本目录是 Seed 处理器的设计规格基线。文档描述**期望行为**，RTL 是该行为的实现；当二者不一致时，必须通过变更记录或偏差记录处理，不能只修改注释掩盖差异。

## 文档层级

| 层级 | 目录 | 内容 | 典型评审人 |
| --- | --- | --- | --- |
| L0 | `00-system/` | 产品/系统需求、ISA、外部可见行为 | 架构、软件、验证 |
| L1 | `10-subsystems/` | 五级流水线等子系统的结构和跨模块契约 | 微架构、RTL、验证 |
| L2 | `20-blocks/` | 单个 RTL module 的 block microarchitecture spec | RTL、验证、集成 |
| T | `templates/` | 新 spec 的模板和规范 | 全体 |
| A | `99-archive/` | 已废弃但需要保留的基线 | 项目负责人 |

## 变更状态

- **Draft**：作者整理中，不得作为实现验收依据。
- **In Review**：功能、接口和验证负责人正在评审。
- **Baselined**：已冻结，可作为 RTL 和验证的验收依据；变更必须增加 revision。
- **Implemented**：RTL 已实现，但不代表验证通过。
- **Verified**：需求、接口和覆盖率门槛均已满足。
- **Deprecated**：仍可追溯，但不再用于新实现。

当前首批文档大多标为 `Implemented / Unverified`：它们是根据现有 Chisel RTL 建立的事实基线，不把尚未实现的 CSR、异常、MMU 或原子操作写成既成事实。

## 编写约定

1. 规范性要求使用 `SHALL`、`SHOULD`、`MAY`；每条可验收要求必须有唯一 ID，例如 `REQ-IF-001`。
2. 接口表给出方向、位宽、时序、握手、复位值和错误语义；仅写端口名不足以构成接口规格。
3. 所有时序行为使用时钟边沿描述。`valid` 只有在对应 payload 有效时才有意义；`ready` 低时，发送方 SHALL 保持 payload 和 `valid` 稳定。
4. 每个 block spec 都要链接到 RTL 和验证入口。需求到测试、断言、覆盖率的映射在验证计划中维护。
5. 文件名不带版本号。版本在文档头部维护，Git history 保存正文变更。
6. 一个行为完整且可独立验证的 RTL module 对应一份 spec。简单的流水寄存器允许使用简化版，但仍须定义 enable、flush、复位和优先级。

## 设计事实和已知偏差

- 当前参数固定为 `xLen=64`、`vaddrBits=64`、`pgIdxBits=12`。
- 当前实现是 RV64 五级流水线，支持一部分 RV64I/RV64M 和 RV64W 指令；CSR、特权级、异常、中断、虚拟内存、缓存和原子指令尚未接入。
- 指令和数据路径各自对接一个 `Decoupled` 请求/响应端口，但通过 `AxiBridge` 共享单个 AXI4 master；同一时刻最多一个外部事务。
- 数据存取响应中的 `resp` 字段目前没有转化为异常；对齐检查也尚未实现。
- `docs/images/architecture.svg` 是历史/目标架构图，仍包含 RV32、MMU 和 CSR 等未实现内容。实现基线以本目录的 `00-system/` 和当前 RTL 为准，图纸将在架构冻结时更新。

## 模块索引

### 五级流水线

- [Pipeline subsystem](10-subsystems/pipeline.md)
- [PipelineCore](20-blocks/pipeline/pipeline-core.md)
- [IFStage](20-blocks/pipeline/if-stage.md)
- [IDStage](20-blocks/pipeline/id-stage.md)
- [EXStage](20-blocks/pipeline/ex-stage.md)
- [MEMStage](20-blocks/pipeline/mem-stage.md)
- [WBStage](20-blocks/pipeline/wb-stage.md)
- [IF/ID register](20-blocks/pipeline/ifid-reg.md)
- [ID/EX register](20-blocks/pipeline/idex-reg.md)
- [EX/MEM register](20-blocks/pipeline/exmem-reg.md)
- [MEM/WB register](20-blocks/pipeline/memwb-reg.md)
- [Pipeline types and internal interfaces](20-blocks/pipeline/pipeline-types.md)

### 顶层和总线

- [AxiBridge](20-blocks/bus/axi-bridge.md)
- [Seed top](20-blocks/top/seed.md)
- [Seed external interface](20-blocks/top/seed-interface.md)
- [Seed parameters](20-blocks/top/seed-param.md)

## 新增模块的最小流程

1. 从 `templates/block-microarchitecture.md` 复制一份，填写 Spec ID 和 RTL 路径。
2. 先写外部接口和可观察行为，再写实现结构；不要从代码逐行翻译成说明。
3. 为每条 `REQ-*` 增加验证方法和验收条件。
4. 完成 RTL、单元测试、子系统测试和评审后，将状态推进到 `Baselined` 或 `Verified`。
