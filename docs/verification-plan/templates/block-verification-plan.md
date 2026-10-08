# <Block> Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Draft |
| Spec | Link to the block spec, relative to the finished plan |
| Spec revision | Revision reviewed for this plan |
| DUT | Scala file and actual module class |
| Parameters | Configurations covered by this plan |
| Environment | `verification/<block>/` (planned or implemented) |
| Owner / reviewer | TBD |
| Last updated | YYYY-MM-DD |

## 1. Scope and baseline

Define the block boundary, included child modules and integration obligations.
Distinguish planned simulation checks from any future formal proof.

## 2. Environment

Describe stimulus, memory/bus models, scoreboard and observation points. Explain
how expected behavior is derived independently of DUT internal control signals.

## 3. Environment constraints

List legal input behavior, reset handling and response ordering. Separate
protocol assumptions from finite test settings and unresolved spec questions.
Do not assume the DUT behavior that a property is intended to check.

## 4. Sampling and property semantics

Define sampling edges, same-cycle versus next-cycle checks, history validity,
reset disabling, and explicit reset checks. Define how transactions surviving
multiple cycles are tracked and when their checks are cancelled.

## 5. Requirement traceability

| Requirement / source | Property / test ID | Method | Scenario and acceptance check | Coverage ID | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| `REQ-XXX-001` | `REQ_XXX_001_RESET` | Assertion + simulation | Explicit expected outcome and timing | `COV_XXX_RESET` | Planned | — |

Include every requirement. Add rows for interface contracts identified by spec
section; assign normative requirement IDs in the spec before encoding those rows
in a tool that requires `requirement_id`.

## 6. Coverage and stimulus

| Coverage ID | Scenario / meaningful cross | Required hits | Status |
| --- | --- | --- | --- |
| `COV_XXX_RESET` | Reset and subsequent legal operation | At least 1 | Planned |

Define directed cases, random configuration/seeds, antecedent activation,
completion observations and relevant boundary combinations. A timeout identifies
an incomplete test; it is not a proof of bounded response time.

## 7. Acceptance and evidence

Specify assertion, scoreboard and measured coverage gates. Include a deliberate
mutation or negative control to establish that generated assertions are active.
Record baseline hashes, tool versions, seeds, reports, logs and unresolved issues.
Aggregate completion requires all applicable checks and explicit review of gaps.

## 8. Open items

| ID / spec issue | Question or gap | Affected checks | Resolution / status |
| --- | --- | --- | --- |
| TBD | Record an ambiguity without choosing a new requirement | TBD | Open |

## 9. Run history

| Run / date | Baseline | Command / seed | Result | Evidence |
| --- | --- | --- | --- | --- |
| — | — | — | Not run | — |
