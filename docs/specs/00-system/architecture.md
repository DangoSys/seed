# Seed MVP Architecture Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-SYS-ARCH` |
| Status | `Baselined for current RTL / Review required` |
| Revision | `0.1` |
| Related | [`requirements.md`](requirements.md), [`pipeline.md`](../10-subsystems/pipeline.md) |

## 1. Architectural boundary

当前实现由一个 `PipelineCore`、一个 `AxiBridge` 和一个 `Seed` 顶层组成：

```text
                    +-----------------------------+
                    | Seed                         |
  AXI4 master <---->| AxiBridge                    |
                    |   ^                       ^  |
                    |   | imem/dmem             |  |
                    |   +---- PipelineCore <---+  |
                    +-----------------------------+
```

`PipelineCore` 负责寄存器文件、五级流水线、旁路、load-use stall、memory stall 和 branch redirect。`AxiBridge` 负责 IF/DMEM 端口到单个 AXI4 master 的仲裁和事务保持。

## 2. Pipeline organization

```text
IFU -> IFIDReg -> IDU -> IDEXReg -> EXU -> EXMEMReg -> MEMU -> MEMWBReg -> WBU
```

- 每个 stage 在一个时钟周期内完成组合计算；相邻阶段通过独立寄存器隔离。
- EX 产生 redirect，目标地址在 redirect.valid 周期有效。
- MEM 访问可以跨多个周期；EX/MEM 寄存器保留请求状态和 load data。
- WB 在 `MemWb` 被观察到时写回整数寄存器并产生 retire。

## 3. Global control priority

按架构意图，控制优先级如下：

1. 复位（由外部顶层/Chisel reset 提供）。
2. 正在进行的数据 memory transaction：冻结 EX/MEM、停止前端和 ID/EX，清除该周期 WB valid。
3. branch/jump redirect：重定向 PC，flush IF/ID 与 ID/EX。
4. load-use：停止 IF 和 IF/ID，使 ID/EX 接收 bubble。
5. 正常推进。

当 `memStall` 与 redirect 同时发生时，当前 RTL 屏蔽 redirect；该选择避免 MEM 事务被年轻指令改写，但需要专门测试并在后续异常模型确定后复审。

## 4. State and data width

| Item | Value |
| --- | --- |
| `xLen` | 64 |
| `vaddrBits` | 64 |
| instruction | 32 bit |
| integer registers | 32 × 64 bit |
| PC increment | 4 byte |
| reset PC | `0x0000000080000000` by default |
| data bus | 64 bit, byte mask |
| page index parameter | 12 bit, currently unused |

## 5. Current exclusions

当前架构不包括 CSR/trap 控制、特权级、MMU/TLB、cache、原子操作、多发出、分支预测和外部中断消费。顶层虽然暴露 `mtip/msip/meip`，但当前 `Seed` 不使用这些输入；`cease` 恒为 0。

## 6. Architectural risks to close

- 错误/非法指令和 AXI error response 尚无可观察异常语义。
- 访问未对齐时，当前 mask/shift 逻辑会继续发出请求；是否支持跨 beat 访问尚未定义。
- 分支 redirect 在 data stall 期间被屏蔽，需在异常和精确提交语义冻结时重新确认。
- README 和历史 SVG 的 RV32/MMU/CSR 描述与当前 RTL 不一致，应在架构评审后统一。
