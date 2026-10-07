# seed

`bb-seed` is a single-core RV64 processor written in Chisel. The current RTL baseline is an in-order five-stage pipeline. The project roadmap extends that baseline toward a Linux-capable platform and later PPA optimization in the Buckyball framework.

## Quick start

Install [Mill](https://mill-build.org/) 1.1.10 and JDK 17 or newer.

The build pins Scala 2.13.18 and Chisel 7.16.0, with the matching Chisel compiler plugin. The chisel-iabv verification templates use the same versions. SystemVerilog emission uses Chisel's matching firtool release.

macOS:

```bash
brew install mill
```

Linux:

```bash
curl -L https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/1.1.10/mill-dist-1.1.10-mill.sh -o mill
chmod +x mill
sudo mv mill /usr/local/bin/mill
```

Generate SystemVerilog with:

```bash
mill seed.runMain framework.seed.SeedTop
```

The generated file is written to `build/rtl/Seed.sv`.

## Mill verification adapter

Initialize the pinned chisel-iabv tool checkout:

```bash
git submodule update --init tools/chisel-iabv
```

From the Seed root, with Python 3.10+ on macOS/Linux, run:

```bash
python3 tools/chisel-iabv/src/product/adapters/mill_project.py \
  --manifest verification/seed-mill.json \
  --project-root . \
  --output "build/verification/$(date +%Y%m%d-%H%M%S)" \
  --mode compile_emit
```

Use a new output directory per run. RTL is emitted to `build/rtl/`; reports,
logs and copied artifacts are stored in `build/verification/`. Both are ignored
by Git. The adapter currently compiles and emits SeedTop; PCReg LTL properties
and a formal backend are not yet connected. See [verification setup](verification/README.md).

## Design specifications

The design baseline is maintained in [`docs/specs/`](docs/specs/README.md). The specifications follow a hardware design flow of system requirements, subsystem architecture, block microarchitecture, interface contracts, and verification mapping.

Start with:

- [System requirements](docs/specs/00-system/requirements.md)
- [Current architecture](docs/specs/00-system/architecture.md)
- [Five-stage pipeline](docs/specs/10-subsystems/pipeline.md)
- [Block microarchitecture template](docs/specs/templates/block-microarchitecture.md)

The specs are the intended behavior. RTL changes that alter behavior must update the relevant spec revision and requirement-to-verification mapping.

## Current RTL baseline

| Area | Current baseline |
| --- | --- |
| ISA | A subset of RV64I, RV64M, and RV64W as decoded by `IDStage` |
| Datapath | 64-bit integer datapath and 32 × 64-bit register file |
| Pipeline | In-order, single-issue IF / ID / EX / MEM / WB |
| Hazards | EX/MEM and MEM/WB forwarding, load-use stall, branch/jump flush |
| Memory | Separate instruction/data request-response ports inside the core |
| Bus | Single-beat AXI4 bridge, one outstanding transaction, data priority over instruction fetch |
| Parameters | `xLen=64`, `vaddrBits=64`, `pgIdxBits=12`, default `resetPc=0x0000000080000000` |
| Commit trace | `PipelineCore` exposes retired and retired PC signals for verification |

The current implementation does not yet include CSR or trap handling, privilege modes, virtual memory, caches, atomic instructions, multiple outstanding transactions, or a dedicated multicycle multiply/divide unit. AXI response errors and misaligned accesses are also not converted into architectural exceptions.

The top-level `Seed` exposes `mtip`, `msip`, and `meip` inputs for future integration, but they are currently unused. `cease` is tied low.

## Architecture overview

The implemented boundary is:

```text
                 +-----------------------------+
                 | Seed                        |
  AXI4 master <--| AxiBridge                   |
                 |       ^                 ^   |
                 |       | imem / dmem     |   |
                 |       +-- PipelineCore-+   |
                 +-----------------------------+
```

Inside `PipelineCore`:

```text
IFStage -> IFIDReg -> IDStage -> IDEXReg -> EXStage
        -> EXMEMReg -> MEMStage -> MEMWBReg -> WBStage
```

The existing [architecture diagram](docs/images/architecture.svg) describes a later target architecture and still contains RV32, MMU, CSR, and platform blocks that are not part of the current RTL baseline. It is retained as a roadmap artifact until the architecture is updated.

## Development roadmap

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| 1. Pipeline baseline | RV64I subset, register file, ALU, hazards, simulated memory | Arithmetic, branch, load/store and retire tests pass |
| 2. M extension and traps | Complete M coverage, CSR state, exceptions, `ECALL`, `MRET` | Differential instruction tests and trap tests pass |
| 3. Atomic and bus features | LR/SC, AMO, `FENCE`, `FENCE.I`, robust AXI error handling | Atomicity and AXI protocol tests pass |
| 4. Privilege and virtual memory | M/S/U modes, delegation, Sv39, permissions, page faults | Translation and privilege tests pass |
| 5. Linux boot | Platform devices, OpenSBI, device tree, initramfs | Reach a BusyBox shell and run basic commands |
| 6. PPA optimization | Caches, timing, area and power optimization | PPA reports are reproducible for declared PVT/workloads |

The first phase is the current implementation focus. Linux boot and the later architectural features are planned work, not current acceptance criteria.

## Verification and PPA

Verification will use the `retired` and `retiredPc` observations for instruction-level comparison, then add targeted checks for hazards, redirects, memory backpressure, AXI handshakes, exceptions, and virtual memory as those features land. Each block spec contains its initial verification obligations; the pipeline-level plan is in [`docs/specs/10-subsystems/pipeline.md`](docs/specs/10-subsystems/pipeline.md).

PPA evaluation is planned for the Buckyball flow. Reports must state process, voltage, temperature, clock constraints, SRAM inclusion, workload, and switching assumptions before results are compared.

## References

- [Linux 6.12 RISC-V build configuration](https://github.com/torvalds/linux/blob/v6.12/arch/riscv/Makefile)
- [RISC-V Linux boot requirements](https://docs.kernel.org/arch/riscv/boot.html)
