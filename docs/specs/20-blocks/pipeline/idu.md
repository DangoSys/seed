# IDU Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-ID` |
| Status | `Implemented / Unverified` |
| Revision | `0.2` |
| RTL | `src/main/scala/pipeline/IDU.scala`, `src/main/scala/pipeline/Instruction.scala` |

## 1. Purpose

将 32-bit instruction 解码为 `IdEx` 控制/数据项，生成 RV64 immediate，并从外部提供的 `rs1Data/rs2Data` 形成操作数快照。

`Instruction.scala` 给每条支持的指令定义独立的命名编码，例如 `INST_ADDI`。IDU 对完整的 32-bit 编码做匹配，再按指令名设置控制字段；共享的字段赋值由按运算类别命名的局部方法完成。匹配的编码包括必要的 `funct3` 和 `funct7`/移位高位约束。

## 2. Interface

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `in` | in | `IfId` | valid、PC、instruction |
| `rs1Data/rs2Data` | in | 64 | register file read data |
| `rs1/rs2` | out | 5 | 实际使用的 source register numbers；未使用的源端口为 0 |
| `usesRs1/usesRs2` | out | 1 | hazard detector 使用标记 |
| `out` | out | `IdEx` | decoded operation |

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
