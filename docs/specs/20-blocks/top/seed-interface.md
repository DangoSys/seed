# Seed External Interface Specification

| Field | Value |
| --- | --- |
| Spec ID | `SEED-IF-TOP` |
| Status | `Implemented / Unverified` |
| Revision | `0.1` |
| RTL | `src/main/scala/interfaces/SeedInterface.scala` |

## 1. AXI master interface

The top-level AXI master is 64 bit and uses five independent AXI4 channels. The bridge currently permits one outstanding transaction and uses ID 0 for all requests.

### Write address (`aw`)

`addr[63:0]`, `size[2:0]`, `len[7:0]`, `burst[1:0]`, `id[3:0]`. Current values are single beat (`len=0`), INCR burst (`burst=1`), and ID 0.

### Write data (`w`)

`data[63:0]`, `strb[7:0]`, `last`. Current writes set `last=1` and require AW/W fire in the same cycle.

### Write response (`b`)

`resp[1:0]`, `id[3:0]`. The bridge forwards `resp` to the pipeline data response; the core does not yet raise a store exception.

### Read address (`ar`)

`addr[63:0]`, `size[2:0]`, `len[7:0]`, `burst[1:0]`, `id[3:0]`. Instruction reads use size 2; data size derives from byte mask.

### Read data (`r`)

`data[63:0]`, `resp[1:0]`, `last`, `id[3:0]`. The bridge uses the lower/upper 32-bit half for instruction fetch based on address bit 2 and shifts data loads by byte offset.

## 2. Interrupt inputs

| Input | Current behavior | Future owner |
| --- | --- | --- |
| `mtip` | unused | CSR/trap controller |
| `msip` | unused | CSR/trap controller |
| `meip` | unused | CSR/trap controller |

Integration tests SHALL drive these inputs to known values but SHALL not expect a current architectural effect.

## 3. `cease`

`cease` is currently tied low. A future halt/debug/finish protocol must define its timing and ownership before the signal is used by a platform.
