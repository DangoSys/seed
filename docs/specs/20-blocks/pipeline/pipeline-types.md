# Pipeline Types and Internal Interface Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-IF-PIPE-TYPES` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/PipelineTypes.scala` |

## 1. Purpose

定义五级流水线之间的 bundle、IF/DMEM request/response 和 retire 观察接口。字段含义是跨 module 的契约；字段增加、删除或重解释需要同步更新对应 block spec 和验证环境。

## 2. External pipeline memory interfaces

### 2.1 Instruction memory

| Field | Direction | Width | Semantics |
| --- | --- | ---: | --- |
| `req.valid/ready` | producer/consumer | 1 | request transfer |
| `req.bits.addr` | core → memory | 64 | instruction byte address |
| `resp.valid/ready` | memory/core | 1 | response transfer |
| `resp.bits.data` | memory → core | 32 | instruction word |
| `resp.bits.resp` | memory → core | 2 | response code, currently not trapped |

### 2.2 Data memory

| Field | Direction | Width | Semantics |
| --- | --- | ---: | --- |
| `req.bits.addr` | core → memory | 64 | byte address |
| `req.bits.wdata` | core → memory | 64 | shifted store data |
| `req.bits.mask` | core → memory | 8 | byte write enables |
| `req.bits.isWrite` | core → memory | 1 | write/read selector |
| `resp.bits.rdata` | memory → core | 64 | returned beat |
| `resp.bits.resp` | memory → core | 2 | response code, currently not trapped |

Both interfaces use `Decoupled`. A transfer occurs only on `valid && ready` at the rising edge. Payload must remain stable while `valid=1 && ready=0`.

## 3. Pipeline bundles

| Bundle | Producer → consumer | Required fields |
| --- | --- | --- |
| `IfId` | IF → ID | `valid`, `pc`, `instr` |
| `IdEx` | ID → EX | PC, source/destination register numbers and values, immediate, ALU/memory/control bits |
| `ExMem` | EX → MEM | PC, ALU result, store data, destination, memory controls, issued/done/load data state |
| `MemWb` | MEM → WB | `valid`, `pc`, result, destination, regWrite |

`valid=0` denotes a bubble. Consumers SHALL ignore payload fields of a bubble.

## 4. Field invariants

- `IfId.instr` is always 32 bit and its PC is the address captured with the request.
- `IdEx.rs1Val/rs2Val` are register-file snapshots; EX may replace them through forwarding.
- `ExMem.memIssued` and `memDone` are meaningful only for a valid memory item.
- `MemWb.result` is the final ALU or load value presented to WB.
- `rd=0` is legal as a decoded field but must never cause a register write.

## 5. Revision policy

A bundle field is an interface change even when the Chisel type remains synthesizable. Update the producer spec, consumer spec, subsystem spec, and directed/temporal checks in the same change.
