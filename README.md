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

Initialize the pinned tool checkouts:

```bash
git submodule update --init tools/chisel-iabv tools/chisel-reader
```

[Development tools](tools/README.md) are pinned Git submodules.
`tools/chisel-reader/` provides the ChiselReader source/SV readability tooling;
its execution is not yet connected to the Seed verification workflow.

Assertion tool code lives under `tools/chisel-iabv/`: `src/product/` contains the project
adapter, and `src/ca-assertion/` contains the assertion-generation code.
Seed does not maintain a separate `src/ca-assertion/` copy.

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
by Git. The manifest above compiles and emits SeedTop. For PCReg Assertion generation from its supplied plan, LTL simulation and a
mutation check, run `python3 verification/pc-reg/run.py` (a launcher for the
chisel-iabv product workflow).
A formal backend is not yet connected. See [verification setup](verification/README.md).

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

## References

- [Linux 6.12 RISC-V build configuration](https://github.com/torvalds/linux/blob/v6.12/arch/riscv/Makefile)
- [RISC-V Linux boot requirements](https://docs.kernel.org/arch/riscv/boot.html)
