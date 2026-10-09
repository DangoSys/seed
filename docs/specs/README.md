# Seed Design Specification

Specs describe the intended behavior of the Seed processor; the RTL implements it. When they disagree, record an `ISSUE-*` in the spec instead of silently changing either side.

## Layout

```text
docs/
├── specs/
│   ├── 00-system/       System requirements and architecture
│   ├── 10-subsystems/   Cross-module contracts
│   ├── 20-blocks/       One spec per RTL module
│   └── templates/       Templates for new specs
└── verification-plan/   How each REQ-* is verified, and its status
```

## How to use

- Read top-down: `00-system/`, then `10-subsystems/`, then the module's spec in `20-blocks/`.
- `REQ-*` items are the acceptance criteria. Known problems are listed as `ISSUE-*` at the end of each spec.
- Verification plans live only in [verification-plan](https://github.com/DangoSys/seed/blob/main/docs/verification-plan/README.md), never inside a spec.
- New module: copy `templates/block-microarchitecture.md`, then create its verification plan with the same file name.

## Requirement keywords

Requirements use uppercase keywords, following [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119):

| Keyword | Meaning | If not met |
| --- | --- | --- |
| `SHALL` / `MUST` | Mandatory | A bug; verification fails |
| `SHALL NOT` / `MUST NOT` | Prohibited | A bug; verification fails |
| `SHOULD` | Recommended; may be skipped for a documented reason | Not a bug, but the reason must be recorded |
| `MAY` | Optional | No effect |

Every `SHALL` statement in a `REQ-*` item is checked by its verification plan.

## Modules

- Pipeline: [subsystem](10-subsystems/pipeline.md), [PipelineCore](20-blocks/pipeline/pipeline-core.md), [IFU](20-blocks/pipeline/ifu.md), [IDU](20-blocks/pipeline/idu.md), [EXU](20-blocks/pipeline/exu.md), [MEMU](20-blocks/pipeline/memu.md), [WBU](20-blocks/pipeline/wbu.md), [types](20-blocks/pipeline/pipeline-types.md)
- Pipeline registers: [IF/ID](20-blocks/pipeline/ifid-reg.md), [ID/EX](20-blocks/pipeline/idex-reg.md), [EX/MEM](20-blocks/pipeline/exmem-reg.md), [MEM/WB](20-blocks/pipeline/memwb-reg.md)
- Top and bus: [Seed](20-blocks/top/seed.md), [interface](20-blocks/top/seed-interface.md), [parameters](20-blocks/top/seed-param.md), [AxiBridge](20-blocks/bus/axi-bridge.md)
