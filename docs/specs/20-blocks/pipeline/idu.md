# IDU Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-ID` |
| Status | `Implemented / Unverified` |
| Revision | `0.4` |
| RTL | `src/main/scala/pipeline/IDU.scala`, `src/main/scala/pipeline/Instruction.scala` |

## 1. Purpose

将 32-bit instruction 解码为 `IdEx` 控制/数据项，生成 RV64 immediate，并从外部提供的 `rs1Data/rs2Data` 形成操作数快照。

`Instruction.scala` 给每条支持的指令定义独立的命名编码，例如 `INST_ADDI`。IDU 对完整的 32-bit 编码做匹配，再按指令名设置控制字段；共享的字段赋值由按运算类别命名的局部方法完成。匹配的编码包括必要的 `funct3` 和 `funct7`/移位高位约束。

## 2. Interface

### 2.1 Inputs and register-file addresses

`io.in` 是来自 [`IFIDReg.io.out`](ifid-reg.md) 的 `IfId` bundle，由以下三个信号组成；跨模块的类型约定见 [Pipeline Types](pipeline-types.md)。IDU 对这些输入进行组合译码，本接口没有独立的 `ready` 或 `enable` 握手。

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `in.valid` | in | 1 | IF/ID 流水项有效标记；`1` 表示 `in.pc` 和 `in.instr` 属于一条待译码指令，`0` 表示 bubble，下游应忽略其载荷 |
| `in.pc` | in | `p.vaddrBits`（当前 64） | `in.instr` 对应指令的地址；IDU 将其传入 `out.pc`，供后续计算 PC 相对结果或跳转目标 |
| `in.instr` | in | 32 | 原始指令编码；IDU 从中匹配指令、提取 `rd/rs1/rs2` 并生成立即数和控制信号 |
| `rs1Data/rs2Data` | in | 64 | register file read data |
| `rs1/rs2` | out | 5 | 实际使用的 source register numbers；未使用的源端口为 0 |
| `usesRs1/usesRs2` | out | 1 | hazard detector 使用标记 |

当 `in.valid=0` 时，`in.pc` 和 `in.instr` 在接口上仍有位值，但没有指令语义。`IDU` 输出 `out.valid=0`，并将 `rs1/rs2` 和 `usesRs1/usesRs2` 清零；`out.pc` 仍直接复制 `in.pc`，其值同样应被忽略。

### 2.2 `out: IdEx` 解码结果

`io.out` 送往 `IDEXReg.io.in`，其 `IdEx` bundle 包含以下全部字段。位宽由 `SeedParam` 决定；当前 `p.vaddrBits=p.xLen=64`。

| Field | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `out.valid` | out | 1 | 直接复制 `in.valid`；`0` 表示 bubble |
| `out.pc` | out | `p.vaddrBits`（64） | 当前指令地址，直接复制 `in.pc` |
| `out.rs1` | out | 5 | 第一源寄存器编号；指令不读取 `rs1` 时为 0 |
| `out.rs2` | out | 5 | 第二源寄存器编号；指令不读取 `rs2` 时为 0 |
| `out.rd` | out | 5 | `in.instr[11:7]` 的目标寄存器编号；是否写入由 `regWrite` 决定 |
| `out.rs1Val` | out | `p.xLen`（64） | `rs1Data` 的读值快照；仅在使用 `rs1` 时有操作数意义 |
| `out.rs2Val` | out | `p.xLen`（64） | `rs2Data` 的读值快照；仅在使用 `rs2` 时有操作数意义 |
| `out.imm` | out | `p.xLen`（64） | 所选指令立即数，或移位指令的移位量；无需立即数时为 0 |
| `out.aluOp` | out | 5 | `AluOp` 编码，选择 EX 的算术/逻辑/M 运算；默认编码 0 为 `add` |
| `out.aluSrcImm` | out | 1 | `1` 时 EX 的第二操作数取 `imm`，否则取 `rs2Val`（可由 EX 旁路修正） |
| `out.usePc` | out | 1 | `1` 时 EX 的第一操作数取 `pc`；当前用于 AUIPC |
| `out.regWrite` | out | 1 | 该指令请求将结果写回 `rd`；最终写入还受 `valid` 和 `rd != 0` 约束 |
| `out.memRead` | out | 1 | LOAD 请求数据存储器读取 |
| `out.memWrite` | out | 1 | STORE 请求数据存储器写入 |
| `out.memToReg` | out | 1 | 写回值来自 LOAD 数据；随 `memRead` 对 LOAD 置 1 |
| `out.branch` | out | 1 | 条件分支标记；EX 依据 `branchFunct3` 和源操作数判断是否跳转 |
| `out.jump` | out | 1 | 无条件跳转标记；JAL/JALR 置 1 |
| `out.jalr` | out | 1 | `1` 表示 JALR 目标地址由第一源操作数（经 EX 旁路后）加 `imm` 形成；JAL 为 0 |
| `out.branchFunct3` | out | 3 | 直接取 `in.instr[14:12]`；仅对条件分支用于区分 BEQ/BNE/BLT/BGE/BLTU/BGEU |
| `out.memSize` | out | 3 | 访存宽度：0/1/2/3 分别表示 byte/halfword/word/doubleword；非访存指令默认 2 |
| `out.loadUnsigned` | out | 1 | 无符号 LOAD 标记，要求 MEM 将读取结果零扩展；有符号 LOAD 为 0 |
| `out.wordOp` | out | 1 | RV64 W 指令标记：使用低 32 位并将结果符号扩展至 64 位 |
| `out.unsignedOp` | out | 1 | 无符号 M 运算标记，供 EX 处理 MULHU、DIVU/REMU 及对应 W 形式 |

除表中注明的直接复制字段和 `memSize` 默认值外，未匹配的控制字段默认为 0。`out.valid=0` 时，下游必须忽略整个 `out` 的载荷；当前未识别的编码仍保留 `out.valid=1`，但不发起寄存器写入或访存。

## 3. Decode coverage

| Class | Opcode | Current support |
| --- | --- | --- |
| LUI/AUIPC | `0110111/0010111` | yes |
| JAL/JALR | `1101111/1100111` | yes |
| conditional branch | `1100011` | BEQ/BNE/BLT/BGE/BLTU/BGEU condition field |
| load/store | `0000011/0100011` | byte/half/word/doubleword size fields |
| RV64I immediate | `0010011` | arithmetic, compare, logical, shifts |
| RV64I/M register | `0110011` | arithmetic, logical, M operations |
| RV64W/M word | `0011011/0111011` | implemented cases in RTL |
| CSR/system/atomic | other | not implemented |

## 4. Requirements

- `REQ-ID-001`: `out.valid` SHALL equal `in.valid`。
- `REQ-ID-002`: 所有 immediate SHALL 按 RV64 sign-extension 和指令格式生成。
- `REQ-ID-003`: load/store 的 `memSize` SHALL 编码为 0/1/2/3 对应 byte/half/word/doubleword。
- `REQ-ID-004`: load 的 `loadUnsigned` SHALL 由 funct3 bit2 决定；load result 在 MEM 阶段扩展。
- `REQ-ID-005`: `usesRs1/usesRs2` SHALL 只对当前指令真正读取的源寄存器置 1，供 load-use detector 使用。
- `REQ-ID-006`: 未支持的指令编码（包括已知 opcode 下不合法的 `funct3`/`funct7` 组合）SHALL 输出默认控制，不产生 memory 或 register write，但仍保留 `valid`；illegal instruction trap 是后续需求。
- `REQ-ID-007`: 当前 valid 指令不使用的源寄存器端口 SHALL 输出地址 0，避免立即数字段被当作源寄存器传递至 EX forwarding。

## 5. Reset and invalid behavior

本模块无独立状态。invalid `IfId` 产生 invalid `IdEx`，不启动任何译码控制，`rs1/rs2` 输出 0。`pc`、`rd` 和 `branchFunct3` 等原始字段仍可携带输入值；下游以 `valid` 判断其有效性。
