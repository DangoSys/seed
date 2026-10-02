# [Block name] Block Microarchitecture Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-BLK-XXX` |
| Status | `Draft` |
| Revision | `0.1` |
| Owner | TBD |
| RTL | `src/main/scala/...` |
| Verification | `src/test/...` or TBD |
| Reviewers | TBD |
| Last updated | YYYY-MM-DD |

## 1. Scope

### 1.1 Purpose

本 block 解决什么问题，向哪个 subsystem 提供什么能力。

### 1.2 In scope

- `REQ-XXX-001`: ... SHALL ...

### 1.3 Out of scope

明确本 block 不处理的功能，以及由哪个 block 负责。

## 2. Context and dependencies

给出上游/下游、时钟复位、配置参数和外部协议。图中标出数据路径和控制路径。

## 3. Interface specification

### 3.1 Ports

| Port | Direction | Width | Clock/reset | Description |
| --- | --- | ---: | --- | --- |
| `...` | in/out | ... | ... | ... |

### 3.2 Handshake and timing

定义 `valid/ready`、请求保持、响应消费、backpressure、同周期事件的优先级。

### 3.3 Reset behavior

定义复位类型、复位期间输出、释放后的第一个周期。

## 4. Functional requirements

使用可测试、不可歧义的规范性句子。每条要求给唯一 ID。

## 5. Microarchitecture

描述状态机、数据选择、流水行为、仲裁、旁路、锁存条件和资源限制。伪代码或时序表优先于大段实现描述。

## 6. State, storage and registers

列出寄存器、状态、写入条件、复位值和保持条件。CSR 或 memory-mapped register 另附完整位域表。

## 7. Errors, exceptions and observability

定义错误输入、错误响应、异常上报、trace/retire 观察点。若当前未实现，写成明确的限制和后续需求。

## 8. Performance and PPA assumptions

说明延迟、吞吐率、Outstanding 数量、频率/面积预算和假设。

## 9. Verification plan

| Requirement | Simulation | Assertion/formal | Coverage/acceptance |
| --- | --- | --- | --- |
| `REQ-XXX-001` | ... | ... | ... |

## 10. Integration checklist

- [ ] 接口字段和上层连接一致
- [ ] Reset、stall、flush 优先级已评审
- [ ] 错误/非法输入行为已定义
- [ ] 需求均有验证映射
- [ ] RTL、测试和 spec revision 已同步

## 11. Open issues and change log

| ID | Issue/change | Owner | Status |
| --- | --- | --- | --- |
| `ISSUE-XXX-001` | ... | ... | Open |
