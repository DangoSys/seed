# IFU (`Instruction Fetch Unit`) Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-IF` |
| Status | `Implemented / Unverified` |
| Revision | `0.2` |
| RTL | `src/main/scala/pipeline/IFU.scala`, module `IFStage` |
| Parent | [PipelineCore](pipeline-core.md) |
| Children | [PCReg](pc-reg.md) |
| Verification | [IFU verification plan](../../../verification-plan/ifu.md) |
| Last updated | 2026-10-08 |

## 1. Purpose and boundary

IFU 按 PC 向指令存储器发起取指请求，把返回的 32 位指令与其 PC 交给 IF/ID，并在 EX 改道时丢弃旧路径上的指令。

IFU 负责：

- 通过 `PCReg` 维护取指 PC：复位值、顺序 `+4`、改道。
- 管理至多一笔未完成的取指请求。
- 将有效响应送往 IF/ID，将过期响应排空。

IFU 不负责：

- 分支判断和目标计算（`EXStage`）。
- `stall`、`flush` 的产生（`PipelineCore`）。
- 指令保存（`IFIDReg`），译码（`IDStage`）。
- 总线仲裁和 AXI 协议转换（`AxiBridge`）。

## 2. Interface

时钟和复位为模块隐式 `clock`、`reset`（同步复位）。宽度中的 `vaddrBits` 当前为 64。

| Port | Dir | Width | Description |
| --- | --- | ---: | --- |
| `redirect.valid` | in | 1 | 本周期改道 |
| `redirect.bits` | in | `vaddrBits` | 改道目标 PC；仅 `redirect.valid=1` 时有效 |
| `stall` | in | 1 | 前端暂停：不得发新请求，不得向 IF/ID 送指令 |
| `outReady` | in | 1 | IF/ID 本周期可接收 |
| `imem.req.valid` | out | 1 | 取指请求有效 |
| `imem.req.ready` | in | 1 | 指令存储器接受请求 |
| `imem.req.bits.addr` | out | `vaddrBits` | 取指地址 |
| `imem.resp.valid` | in | 1 | 指令返回有效 |
| `imem.resp.ready` | out | 1 | IFU 接受响应 |
| `imem.resp.bits.data` | in | 32 | 指令 |
| `imem.resp.bits.resp` | in | 2 | 总线响应码；当前未使用，见 `ISSUE-IF-002` |
| `out.valid` | out | 1 | 本周期向 IF/ID 送出一条有效指令 |
| `out.pc` | out | `vaddrBits` | 该指令的 PC |
| `out.instr` | out | 32 | 该指令 |

`out` 为组合输出，与 `imem.resp` 同周期，由 IF/ID 在同一时钟沿采样；`out.valid=0` 时 `out.pc`、`out.instr` 无意义。

## 3. Timing and handshake

- 请求在 `imem.req.valid && imem.req.ready` 的周期被接受（`req.fire`）；响应在 `imem.resp.valid && imem.resp.ready` 的周期被接受（`resp.fire`）。
- 响应最早在 `req.fire` 的下一周期被接受；存储器延迟可以任意长。
- 同周期优先级：redirect > stall/`outReady=0` > 正常取指。

| 本周期条件 | 发新请求 | 接受响应 | 送往 IF/ID |
| --- | --- | --- | --- |
| redirect | 否 | 是（若有在途请求），并丢弃 | 否 |
| 在途请求已作废 | 否 | 是，并丢弃 | 否 |
| stall 或 `outReady=0` | 否 | 否，响应保持在接口上 | 否 |
| 正常、无在途请求 | 是 | — | 否 |
| 正常、有在途请求 | 否 | 是 | 是 |

## 4. Reset

复位后：PC 为 `SeedParam.resetPc`（默认 `0x80000000`），无在途请求，无待丢弃响应，`out.valid=0`。复位释放后的第一个周期，若无 stall、redirect 且 `outReady=1`，IFU 以 `resetPc` 发出请求。

## 5. Requirements

- `REQ-IF-001`: 复位后第一个被接受的取指请求的地址 SHALL 为 `SeedParam.resetPc`。
- `REQ-IF-002`: 任一时刻在途取指请求 SHALL 不超过一笔；存在在途请求时 `imem.req.valid` SHALL 为 0。
- `REQ-IF-003`: 无 redirect 时，每次 `req.fire` 后，下一笔请求的地址 SHALL 为本次地址 `+4`（按 `vaddrBits` 截断）。
- `REQ-IF-004`: `out.valid` SHALL 仅在接受一笔未作废的响应、且本周期无 stall、无 redirect、`outReady=1` 时为 1；每笔请求至多产生一次 `out.valid=1`。
- `REQ-IF-005`: redirect 时若有在途请求，其响应 SHALL 被接受且不送往 IF/ID；redirect 后的第一笔请求地址 SHALL 为 `redirect.bits`。
- `REQ-IF-006`: redirect、stall 或 `outReady=0` 的周期 SHALL 不发起新请求。
- `REQ-IF-007`: `out.valid=1` 时，`out.pc` SHALL 等于该响应对应请求的地址，`out.instr` SHALL 等于 `imem.resp.bits.data`。

## 6. Internal state

| State | Reset | Meaning |
| --- | --- | --- |
| `PCReg.pc` | `resetPc` | 下一笔请求的地址；见 [PCReg](pc-reg.md) |
| `pending` | 0 | 有一笔请求已被接受、响应未接受 |
| `requestPc` | 0 | 在途请求的地址，用作 `out.pc` |
| `discardResponse` | 0 | 在途请求已因 redirect 作废 |

PC 在 `req.fire` 时前进，不等待响应返回。

## 7. Performance

至多一笔在途请求。存储器单周期返回时，吞吐率为每 2 周期一条指令；延迟为 N 周期时，为每 N+1 周期一条。

## 8. Open issues

| ID | Issue | Status |
| --- | --- | --- |
| `ISSUE-IF-001` | `imem.req.valid` 在 `req.fire` 前可能因 stall、redirect 或 `outReady` 变化而撤回，不满足 [编写约定](../../README.md) 第 3 条；`AxiBridge` 将其直接映射为 AXI `ARVALID`，违反 AXI 协议。 | Open |
| `ISSUE-IF-002` | `imem.resp.bits.resp` 未转为 instruction access fault。 | Open |
| `ISSUE-IF-003` | 无取指地址对齐检查；JALR 只清 bit0，bit1 为 1 时不产生 instruction-address-misaligned 异常。 | Open |
| `ISSUE-IF-004` | 单笔在途请求限制吞吐率，接 ICache 时需重新评估。 | Open |
| `ISSUE-IF-005` | 若复位后的首笔请求被接受前发生 redirect，`REQ-IF-001` 的 resetPc 要求与 `REQ-IF-005` 的改道目标要求如何适用，尚需明确。 | Open |
| `ISSUE-IF-006` | 在下一笔请求被接受前发生多次 redirect 时，应选择哪个目标，尚需明确。 | Open |
| `ISSUE-IF-007` | 复位时在途请求和未接受响应如何取消或排空，以及复位期间是否接受接口握手，尚需明确。 | Open |
