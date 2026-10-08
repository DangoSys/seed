# Verification Plans

This directory records how each module's requirements will be verified, their
status, and supporting evidence. Design behavior is defined by
[the specs](../specs/README.md).

## Layout and ownership

```text
docs/verification-plan/
├── README.md
├── templates/
│   └── block-verification-plan.md
└── ifu.md

verification/<block>/             Executable verification inputs
├── README.md                     Run instructions
├── assertion-plan.json           Reviewed properties consumed by iabv
├── job.json                      Generation, backend and mutation configuration
├── manifest.json                 Mill build and emission configuration
├── src/main/scala/               Harness and observation/history registers
└── sim_main.cpp                  Stimulus, memory model and reference model

build/verification/<run-id>/      Generated assertions, reports, logs and waves
```

The executable layout is a convention for future modules; an IFU environment has
not been implemented yet. The existing [PCReg environment](../../verification/pc-reg/README.md)
is an integration example. Generated run artifacts remain outside source control.

## File conventions

- Use one plan per block, with the same basename as its spec:
  `docs/specs/20-blocks/pipeline/ifu.md` maps to `docs/verification-plan/ifu.md`.
- Start with the [block template](templates/block-verification-plan.md). Keep a
  module's requirements, scenarios, coverage and status together in one document.
- Use stable property, test and coverage IDs. One requirement can map to multiple
  checks. Every requirement needs a method and an explicit acceptance criterion.
- Link the plan from the block spec's `Verification` field.
- When the spec changes, review the plan before updating its `Spec revision`.
  Reassess affected results when RTL, properties, harnesses or tool versions change.
- Record ambiguities in the spec's open issues and link them from the plan.
  Proposed interpretations are not acceptance criteria until resolved.

## Relationship to iabv

The Markdown plan is reviewed by engineers. Executable property semantics live
in `verification/<block>/assertion-plan.json`; the current product renders that
supplied JSON into Chisel LTL assertions. Automatic Markdown-to-property planning
is not implemented.

The job links the spec, property plan, harness and backend inputs. Include the
Markdown verification plan in the job's snapshot inputs when implementing the
environment so that run evidence also identifies the reviewed plan. Keep IDs
consistent across the document, JSON, generated properties and reports.

Simulation results establish checks on executed traces. A formal result requires
a separate formal backend, recorded assumptions and proof evidence. The current
product workflow uses Verilator simulation.

## Status values

| Status | Meaning |
| --- | --- |
| `Planned` | Scenario and check defined; implementation pending |
| `Implemented` | Check implemented; no applicable passing run yet |
| `Passing` | Applicable run passed, with linked evidence and recorded baseline |
| `Failing` | Applicable run failed, with linked failure evidence |
| `Waived` | Explicit review decision, with owner, reason and scope in evidence |

`Passing` requires evidence: source/tool revisions or hashes, parameters, random
seeds, command, run report and relevant coverage. A result from an older baseline
remains historical evidence; it does not establish the current baseline's status.
Known defects must remain visible and must not be waived automatically.

## Plan index

| Block | Spec | Plan | Implementation |
| --- | --- | --- | --- |
| IFU | [IFU](../specs/20-blocks/pipeline/ifu.md) | [IFU plan](ifu.md) | Planned |

PCReg plan migration is pending. Its executable environment already exists, but
the currently deleted `pc-reg.md` spec must be restored or its replacement agreed
before reconciling its requirement mappings and job paths.
