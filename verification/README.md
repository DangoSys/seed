# Verification setup

Seed owns its RTL, specs and project manifests. The tool implementation comes
from the fixed Git submodule commit at `tools/chisel-iabv`.

## Setup

After cloning Seed, or in an existing clone: `git submodule update --init tools/chisel-iabv`.
The Mill adapter only needs this tool submodule; nested benchmark submodules
are optional. The submodule uses GitHub SSH access. Install Python 3.10+, Mill 1.1.10 and JDK 17+
on macOS/Linux. No additional Python packages are required by the adapter.

Run the command in the root README from the Seed repository root.
`seed-mill.json` selects `seed.compile` and `seed.runMain framework.seed.SeedTop`.
Use `--mode compile` to skip emission. Each report directory must be new;
do not run concurrent builds in this checkout.

## Outputs

- `build/rtl/`: generated RTL and verification layers.
- `build/verification/<run>/report.json`: stage results and input/artifact hashes.
- `build/verification/<run>/compile.log`, `emit.log`: build logs.
- `build/verification/<run>/artifacts/`: copied output bundle.

Old files from the previous `build/` emission location are not inputs to the new
flow. The adapter refuses unchanged files in `build/rtl/` from an earlier run;
inspect such files if an emitter fails to refresh its complete output.

`passed` means the requested compile/emission stages succeeded. `formal_status`
remains `not_run`. The current manifest builds the processor top including PCReg;
a separate PCReg property harness and formal backend are future integration work.

## Updating the tool

Fetch and check out a reviewed tool commit inside `tools/chisel-iabv`, rerun the
Seed manifest, then commit the updated submodule pointer in Seed. The repository
pins a commit rather than following a moving branch automatically.
