# AxiBridge Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-AXI` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/top/AxiBridge.scala` |

## 1. Purpose

将 PipelineCore 的 instruction/data request/response 端口桥接到单个 64-bit AXI4 master。该 bridge 只允许一个 outstanding transaction，并对数据请求优先仲裁。

## 2. Transaction and arbitration

| Condition | Action |
| --- | --- |
| `dmem.req.valid` | data request wins over IF |
| data read | AR → `readWait` → R → dmem response |
| data write | AW 和 W 同周期 fire → `writeWait` → B → dmem response |
| instruction read | AR → `readWait` → R → imem response |
| idle | 可接受一个 transaction |

AXI ID 固定为 0；`len=0`；`burst=1`；读写 size 由 data mask 推导，instruction size 固定为 2（4 bytes）。

## 3. Requirements

- `REQ-AXI-001`: bridge SHALL 在非 idle 状态禁止新的 AR/AW/W transaction。
- `REQ-AXI-002`: 当数据和指令请求同时 valid 时，数据请求 SHALL 优先。
- `REQ-AXI-003`: 请求只有在 AXI 通道 fire 时才向相应 pipeline 端口返回 ready。
- `REQ-AXI-004`: read response SHALL 按地址 bit2 从 64-bit R data 选择 32-bit instruction，并向 dmem 返回按 byte offset 右移后的 64-bit data。
- `REQ-AXI-005`: write response SHALL 通过 dmem response 完成 store transaction；返回 data 固定为 0。
- `REQ-AXI-006`: bridge SHALL 保持 AXI response ready 依赖于下游 response ready，不得丢弃 backpressure 下的响应。
- `REQ-AXI-007`: 当前 bridge SHALL 只支持单 beat、ID=0、单 outstanding；不支持 response reorder 或 burst。

## 4. Error semantics

AXI `RRESP/BRESP` 被映射到 `Pipe*Resp.resp`，但 PipelineCore 当前不消费该字段。因此平台验证必须覆盖 OKAY response；error-to-trap 是后续系统需求。

## 5. Known limitations

写事务要求 AW 和 W 在同周期同时 fire；这是当前实现的接口约束。AXI 允许的独立 AW/W 到达顺序未在 bridge 中支持。
