# EXU Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-EX` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/EXU.scala` |

## 1. Purpose

执行 RV64 ALU/M 运算，选择立即数或 PC，执行 EX/MEM 与 MEM/WB 旁路，并解析 branch/jump 目标。

## 2. Interface

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `in` | in | `IdEx` | decoded item |
| `exmem` | in | `ExMem` | newest forwarding source |
| `memwb` | in | `MemWb` | older forwarding source |
| `out` | out | `ExMem` | ALU/memory control result |
| `redirect` | out | Valid(`vaddrBits`) | taken control transfer |

## 3. Forwarding rules

对于每个源操作数：

1. `rs=0` 返回 0；
2. valid、regWrite、非 load 且 `rd==rs` 的 EX/MEM 值优先；
3. 其次使用 valid、regWrite 且 `rd==rs` 的 MEM/WB result；
4. 否则使用 ID 阶段读出的值。

load 的 EX/MEM 值不会被旁路，因此由 PipelineCore 的 load-use stall 处理。

## 4. ALU and branch requirements

- `REQ-EX-001`: 64-bit add/sub/logical/shift/compare SHALL 按 `AluOp` 产生结果。
- `REQ-EX-002`: RV64W 操作 SHALL 在低 32 bit 运算后 sign-extend 到 64 bit；unsigned word 运算按 RTL 的 `unsignedOp` 选择。
- `REQ-EX-003`: MUL/MULH/MULHSU/MULHU/DIV/DIVU/REM/REMU SHALL 使用 RISC-V 定义的 signedness 和除零结果。
- `REQ-EX-004`: taken branch/jump SHALL 令 `redirect.valid=1`；普通指令和 not-taken branch SHALL 为 0。
- `REQ-EX-005`: JAL/JALR 写回值 SHALL 为 `pc+4`；redirect target SHALL 为 PC-relative target 或 `rs1+imm`，最后清除 bit0。

## 5. Output semantics

`out.memDone` 对非 memory 指令为 1；对 load/store 为 0。`out.memIssued` 每次新形成的 memory item 置 0。store data 使用旁路后的 `rs2`。

## 6. Limitations

除法和乘法在当前 RTL 中是组合表达式，尚未实现真正的多周期 MulDiv。branch exception、misalignment 和 overflow trap 未实现。
