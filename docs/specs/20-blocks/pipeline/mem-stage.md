# MEMU Block Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-MEM` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/pipeline/MEMU.scala` |

## 1. Purpose

把 EX/MEM 中的 memory item 转为一个 64-bit、带 byte mask 的请求；等待响应；从返回 beat 中提取并扩展 load value。

## 2. Interface

| Port | Direction | Meaning |
| --- | --- | --- |
| `in` | in | `ExMem` transaction state |
| `dmem.req` | out | `{addr,wdata,mask,isWrite}` |
| `dmem.resp` | in | `{rdata,resp}` |
| `advance` | out | EX/MEM 可捕获下一项 |
| `out` | out | `MemWb` |

## 3. Request formation

- 地址为 `in.aluResult`。
- store data 左移 `addr[2:0] * 8`。
- byte mask：`1 << addr[2:0]`；half/word 分别按 `addr[2:1]`/`addr[2]` 对齐；doubleword 为 `0xff`。
- `isWrite` 等于 `memWrite`。
- 请求只在 `in.valid && (memRead || memWrite) && !memIssued` 时有效。

## 4. Requirements

- `REQ-MEM-001`: memory request fire 后，`advance` SHALL 保持 0，直到对应 response fire。
- `REQ-MEM-002`: 一个 EX/MEM memory item SHALL 最多产生一次请求 fire。
- `REQ-MEM-003`: load byte/half/word/doubleword SHALL 从 64-bit response 中按地址低位选择，并根据 `loadUnsigned` 扩展。
- `REQ-MEM-004`: 非 memory item SHALL 立即 `advance=1`，并将 ALU result 送至 WB。
- `REQ-MEM-005`: 当前实现 SHALL 保持 response `resp` 字段，但不把 error response 变成处理器异常；该限制必须在系统验证环境中显式假设。

## 5. Completion behavior

`advance = !hasMem || in.memDone`。PipelineCore 在请求 fire 时设置 `memIssued`，在 response fire 时设置 `memDone` 和 `loadData`。store 的 response data 被忽略，但 B response 仍必须完成事务。

## 6. Limitations

未对未对齐访问作 fault 或跨 beat 拆分；地址和 mask 组合逻辑假定平台接受该访问形式。
