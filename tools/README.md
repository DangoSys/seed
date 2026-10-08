# Development tools

Seed pins each tool as an independent Git submodule.

| Directory | Repository | Role in Seed |
| --- | --- | --- |
| `chisel-iabv/` | [nick-2001/chisel-iabv](https://github.com/nick-2001/chisel-iabv) | Assertion generation and verification workflow; PCReg job is configured under `verification/pc-reg/` |
| `chisel-reader/` | [nicheng-ai/chisel-reader](https://github.com/nicheng-ai/chisel-reader) | Chisel/SystemVerilog readability and semantic analysis tooling; checkout available, execution integration pending |

From the Seed root:

```bash
git submodule update --init tools/chisel-iabv tools/chisel-reader
```

This initializes the two tool checkouts at the recorded commits. Nested tool
submodules are optional and should be initialized only when a workflow needs them.
Install and configure each tool according to its own documentation before running
it; adding a checkout does not install dependencies or configure model credentials.

- [chisel-iabv product documentation](chisel-iabv/src/product/README.md)
- [ChiselReader documentation](chisel-reader/README.md)

To update a tool, fetch its repository and check out a reviewed commit inside its
submodule, then record the new submodule pointer in Seed. Existing separate local
tool checkouts and their uncommitted changes are independent of these checkouts.
