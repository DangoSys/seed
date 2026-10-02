# [Interface name] Interface Specification

| Field | Value |
| --- | --- |
| Interface ID | `SEED-IF-XXX` |
| Status | `Draft` |
| Revision | `0.1` |
| Producer / consumer | TBD |
| Clock domain | TBD |

## Signal table

| Signal | Direction from producer | Width | Reset value | Meaning |
| --- | --- | ---: | ---: | --- |
| `valid` | out | 1 | 0 | Payload is valid |
| `ready` | in | 1 | 0/1 | Consumer accepts payload |
| `bits...` | out | ... | ... | Payload |

## Protocol rules

- Transfer occurs only on `valid && ready` at a rising clock edge.
- A producer SHALL hold `valid` and all payload fields stable while `valid=1` and `ready=0`.
- Define whether requests may be issued back-to-back, whether responses may be reordered, and the maximum number of outstanding transactions.
- Define response error codes, cancellation, reset during an outstanding transaction, and clock-domain crossing requirements.

## Timing examples

Use a cycle table or waveform for request, backpressure, response and reset cases.
