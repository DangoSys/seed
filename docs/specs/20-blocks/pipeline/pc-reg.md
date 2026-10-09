# PCReg Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-PC` |
| Status | `Implemented / simulation verified; formal pending` |
| Revision | `0.3` |
| RTL | `src/main/scala/pipeline/PCReg.scala` |
| Parent | [IFU](ifu.md) |

## 1. Purpose and boundary

`PCReg` 保存 instruction-fetch frontend 的当前 PC，并在时钟上升沿选择下一 PC。它不解析分支条件、不管理 instruction response，也不记录 pending request；这些行为由流水线其他模块处理。

## 2. Interface

| Port | Direction | Width | Meaning |
| --- | --- | ---: | --- |
| `redirect.valid` | in | 1 | 本周期存在 taken branch/jump target |
| `redirect.bits` | in | `vaddrBits` | redirect target |
| `advance` | in | 1 | 当前 instruction request 已 fire |
| `pc` | out | `vaddrBits` | 当前 PC，供 IF 请求使用 |

## 3. State transition

```text
if redirect.valid: state' = redirect.bits
else if advance:  state' = state + 4
else:             state' = state
```

`redirect` 优先级高于 `advance`，`advance` 优先级高于保持。所有更新发生在时钟上升沿；`pc` 在一个周期内保持稳定。

## 4. Requirements

- `REQ-PC-001`: reset 后 `pc` SHALL 为 `SeedParam.resetPc`（`0x80000000`）。
- `REQ-PC-002`: `redirect.valid=1` 时，下一周期 `pc` SHALL 等于 `redirect.bits`。
- `REQ-PC-003`: `redirect.valid=0 && advance=1` 时，下一周期 `pc` SHALL 等于当前 `pc + 4`，并按 `vaddrBits` 截断溢出位。
- `REQ-PC-004`: `redirect.valid=0 && advance=0` 时，`pc` SHALL 保持不变。
- `REQ-PC-005`: `redirect.valid=1 && advance=1` 时，redirect SHALL 胜出，下一周期不得执行顺序递增。
- `REQ-PC-006`: `PCReg` SHALL 不产生 instruction request，不保存 request response，也不修改 `requestPc` 或 `pending`。

## 5. IFU integration

当前 `IFU` 的集成方式是：

- `io.imem.req.bits.addr := pcReg.io.pc`；
- `pcReg.io.advance := io.imem.req.fire`；
- `requestPc` 在 request fire 时捕获旧 `pc`；
- `pending` 和 `discardResponse` 继续由 `IFU` 管理。

因此 PC 在请求被接受时前进，而不是等 instruction response 返回后才前进。response 延迟不会改变顺序 PC；redirect 会丢弃旧响应并覆盖下一取指地址。

## 6. Verification plan

| Scenario | Expected result |
| --- | --- |
| reset | `pc=SeedParam.resetPc` (`0x80000000`) |
| advance | sequential `pc+4` increment |
| no advance | hold |
| redirect while idle | target captured |
| redirect with advance | redirect wins |
| IF response delayed | PC remains at post-request value; response PC comes from IFU `requestPc` |

## 7. Executable verification

The [verification setup](https://github.com/DangoSys/seed/blob/main/verification/pc-reg/README.md) has two
simulation checks. The ChiselSim Scala test drives PCReg directly and checks an
independent PC reference model over directed and deterministic random cycles.
The separate chisel-iabv job maps REQ-PC-001 through REQ-PC-005 to a supplied
property plan, generates Chisel LTL assertions, and runs them with six measured
cover points in Verilator. Its +4-to-+8 mutation must fail the advance assertion.

These results establish simulation coverage of the exercised scenarios; no
formal proof has run. REQ-PC-006 remains a structural review obligation. The
response-delay scenario in section 6 belongs to IFU integration and is not
exercised here.
