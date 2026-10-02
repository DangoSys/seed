# IFStage Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IF` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/IFStage.scala` |

## 1. Purpose

产生顺序指令地址、管理一个未完成的 instruction request、在响应到达时形成 `IfId`，并处理 EX 产生的 redirect。

## 2. Interface

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `redirect.valid` | in | 1 | 本周期采用 redirect |
| `redirect.bits` | in | `vaddrBits` | 目标 PC |
| `stall` | in | 1 | 前端不得请求或消费响应 |
| `outReady` | in | 1 | IF/ID 本周期可接收 |
| `imem.req` | out | Decoupled | `{addr}` |
| `imem.resp` | in | Decoupled | `{data[31:0], resp[1:0]}` |
| `out` | out | `IfId` | response 的 PC 和 instruction |

## 3. State

| State | Reset | Set | Clear |
| --- | --- | --- | --- |
| `pc` | 0 | request fire 后加 4；redirect 时写目标 | never |
| `requestPc` | 0 | request fire 时捕获 `pc` | next request |
| `pending` | false | request fire | response fire |
| `discardResponse` | false | pending 时收到 redirect | response fire |

## 4. Requirements

- `REQ-IF-001`: 复位后第一个可接受请求的地址 SHALL 为 0。
- `REQ-IF-002`: IF SHALL 最多保持一个 outstanding instruction request；`req.valid` SHALL 在 `pending=1` 时为 0。
- `REQ-IF-003`: 请求 fire 时 SHALL 捕获 request PC，并将下一顺序 PC 更新为 `pc+4`。
- `REQ-IF-004`: response 只有在 `pending && resp.valid && !stall && outReady && !redirect.valid && !discardResponse` 时才形成 valid `out`。
- `REQ-IF-005`: pending response 期间发生 redirect 时 SHALL 消费并丢弃旧响应，不得写入 IF/ID；PC SHALL 改为 redirect target。
- `REQ-IF-006`: redirect 或 stall 时不得发起新的 instruction request。

## 5. Timing

response 可以任意延迟。`resp.ready` 在 redirect 或 discard 状态下仍可为 1，以便清掉旧事务；正常响应只有在下游可接收时才 ready。

## 6. Known limitations

`resp.resp` 未转成 fetch exception；PC 对齐只通过 JALR bit0 清零和顺序 +4 间接保证，未提供显式 instruction access fault。
