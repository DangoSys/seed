# SeedParam Configuration Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-CFG-PARAM` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/configs/SeedParam.scala` |

## Parameters

| Parameter | Current value | Constraint | Usage |
| --- | ---: | --- | --- |
| `xLen` | 64 | must equal 64 | integer data path and register file |
| `vaddrBits` | 64 | must equal 64 | PC, addresses, redirect |
| `pgIdxBits` | 12 | must equal 12 | reserved for future paging |
| `resetPc` | `0x0000000080000000` | non-negative and fits `vaddrBits` | initial instruction-fetch address |

The constructor rejects unsupported widths and reset addresses that do not fit `vaddrBits`. A future parameterization change is an architectural change: update immediate generation, masks, AXI data width, register file, tests and all dependent specs together.
