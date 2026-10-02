# IDStage Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-ID` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/IDStage.scala` |

## 1. Purpose

将 32-bit instruction 解码为 `IdEx` 控制/数据项，生成 RV64 immediate，并从外部提供的 `rs1Data/rs2Data` 形成操作数快照。

## 2. Interface

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `in` | in | `IfId` | valid、PC、instruction |
| `rs1Data/rs2Data` | in | 64 | register file read data |
| `rs1/rs2` | out | 5 | source register numbers |
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
- `REQ-ID-006`: 未支持 opcode 当前 SHALL 输出默认控制（不产生 memory 或 register write），但仍保留 `valid`；illegal instruction trap 是后续需求。

## 5. Reset and invalid behavior

本模块无独立状态。invalid `IfId` 产生 invalid `IdEx`，其余控制字段为零默认值。
