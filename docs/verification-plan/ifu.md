# IFU Verification Plan

| Field | Value |
| --- | --- |
| Plan revision | `0.1` |
| Plan status | Draft; all verification items planned |
| Spec | [IFU](../specs/20-blocks/pipeline/ifu.md) |
| Spec revision | `0.2` |
| DUT | [IFU.scala](../../src/main/scala/pipeline/IFU.scala), class `framework.seed.pipeline.IFStage` |
| Parameters | `SeedParam()`: 64-bit addresses, 32-bit instructions, reset PC `0x80000000` |
| Environment | `verification/ifu/` (planned) |
| Owner / reviewer | TBD |
| Last updated | 2026-10-08 |

## 1. Scope and baseline

Verify IFU's request/response behavior with the real PCReg child: request limits,
sequential addresses, redirect handling, response discard, backpressure and
PC/instruction pairing. Drive the block interface directly; IF/ID storage,
branch target calculation and AXI conversion belong to integration verification.

The first implementation will use iabv-generated Chisel LTL checks and a Verilator
reference scoreboard. No IFU harness, JSON property plan or run evidence exists
yet. Formal proof is future work. Spec questions in section 8 block acceptance of
the affected scenarios even if the current implementation can be observed.

## 2. Environment

- Instantiate `IFStage` in a proposed `IFUVerificationTop` and expose its interface.
- Drive request readiness, stall, downstream readiness and redirects independently.
- Use a registered instruction-memory model with configurable response latency.
  Associate each accepted request with its address and a reproducible data word;
  include varied data patterns to detect instruction mispairing.
- Maintain an independent transaction scoreboard from sampled interface events.
  Track outstanding count, captured address, expected data, redirect invalidation,
  and whether a response was delivered or discarded. Detect a second request
  before completion rather than preventing the memory model from accepting it.
- Track expected next PC from reset, redirect and request handshakes. Do not use
  DUT `pending`, `requestPc` or `discardResponse` to compute expected results.
- Use harness history registers for previous-cycle observations. Document any
  later internal observation mechanism; external transaction checks remain the
  reference.

Planned files follow the [directory convention](README.md): `assertion-plan.json`,
`job.json`, `manifest.json`, Scala harness, `sim_main.cpp` and run documentation.
Use the supplied-plan iabv mode and include this document and the design spec in
the job's snapshot. Generated Scala, reports and waves go into the run directory.

## 3. Environment constraints

| ID | Rule | Basis / limitation |
| --- | --- | --- |
| `ENV-IF-01` | Return exactly one response per accepted request, in order; no unsolicited responses | Legal memory model; separately detect DUT over-issue |
| `ENV-IF-02` | Offer a response no earlier than the cycle after request acceptance | Spec section 3; no same-cycle completion |
| `ENV-IF-03` | Once a response is offered, hold its valid, data and response code until accepted | Memory-side handshake contract |
| `ENV-IF-04` | Allow stall, outReady, request ready and redirect to vary while a request is blocked | Exercise the known request withdrawal issue; do not constrain it away |
| `ENV-IF-05` | Baseline functional runs use successful memory response codes | Error-to-exception behavior remains `ISSUE-IF-002` |
| `ENV-IF-06` | For initial reset tests, reset both DUT and memory model and begin a new scoreboard epoch | Proposed environment policy; reset with an outstanding transaction awaits `ISSUE-IF-007` |

Finite response delays and watchdogs are test settings, not guarantees that
memory always responds within a fixed bound. A future liveness proof would need
explicit response/readiness fairness assumptions. Address-alignment exceptions
are not checked while `ISSUE-IF-003` remains unresolved.

## 4. Sampling and property semantics

- Sample a cycle's inputs and combinational outputs immediately before its rising
  edge. `req.fire` and `resp.fire` refer to that sample. Update the memory model
  and scoreboard after evaluating the sample.
- Same-cycle checks compare output valid/data and handshake eligibility in that
  sample. Next-cycle checks compare state resulting from the previous edge.
- Capture request PC at `req.fire`; compare with `out.pc` on delivery. The fetch
  PC may already have advanced when the response arrives.
- Redirect sampled with a response invalidates it for delivery in that same
  cycle. Track discard until the response handshake completes.
- Clear model/history validity at reset. Functional checks apply outside reset;
  explicit reset checks examine the state after a reset edge. Initial register
  values must not substitute for exercising reset.
- Do not require request/output valid to be low throughout asserted reset unless
  the spec defines that behavior; see `ISSUE-IF-007`.
- The current product accepts same-cycle and next-cycle properties. Represent
  multi-cycle transaction history with explicit monitor state and sampled checks;
  do not claim unbounded sequence checking or formal eventuality.

## 5. Requirement traceability

IDs below are proposed implementation labels. Every row is `Planned`; an open
question is not an implicit waiver or a completed test.

| Requirement / source | Property / test ID | Method | Scenario and acceptance check | Coverage ID | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| `REQ-IF-001`, section 4 | `REQ_IF_001_RESET_FETCH` | Assertion + scoreboard | Idle reset, then delayed acceptance; first accepted address is resetPc without intervening redirect; no stale output after reset | `COV_IF_RESET` | Planned | — |
| `REQ-IF-002` | `REQ_IF_002_SINGLE_PENDING` | Assertion + scoreboard | Delay response; outstanding count stays in 0..1 and request valid is low while outstanding | `COV_IF_DELAYED` | Planned | — |
| `REQ-IF-003` | `REQ_IF_003_SEQUENCE` | Assertion + scoreboard | Consecutive requests without intervening reset/redirect advance by 4 modulo address width | `COV_IF_SEQUENCE`, `COV_IF_WRAP` | Planned | — |
| `REQ-IF-003`, section 6 | `REQ_IF_003_ADVANCE_ON_FIRE` | Assertion + scoreboard | Block request ready; without reset/redirect, next PC advances only on handshake | `COV_IF_REQ_BLOCKED` | Planned | — |
| `REQ-IF-004` | `REQ_IF_004_DELIVERY` | Assertion + scoreboard | Output valid only for a live response handshake with no stall/redirect and outReady high; each transaction delivered at most once | `COV_IF_SEQUENCE`, `COV_IF_RESP_BLOCKED` | Planned | — |
| `REQ-IF-004`, section 3 | `REQ_IF_004_LIVE_READY` | Assertion + scoreboard | Live pending response accepted and delivered when unblocked; stall or outReady low prevents live-response acceptance | `COV_IF_RESP_BLOCKED` | Planned | — |
| `REQ-IF-005` | `REQ_IF_005_TARGET` | Assertion + scoreboard | Redirect while idle/pending; after old response drains, first accepted request uses target; repeated redirects await clarification | `COV_IF_REDIRECT_IDLE`, `COV_IF_REDIRECT_PENDING` | Planned | — |
| `REQ-IF-005` | `REQ_IF_005_DISCARD` | Assertion + scoreboard | Redirect with pending response, including same-cycle completion: accept stale response without delivery; drain despite stall/outReady | `COV_IF_REDIRECT_RESPONSE`, `COV_IF_DISCARD_BLOCKED` | Planned | — |
| `REQ-IF-006` | `REQ_IF_006_REQUEST_GATE` | Assertion + simulation | Redirect, stall or outReady low suppresses new request valid; exercise combinations | `COV_IF_REQUEST_GATES` | Planned | — |
| `REQ-IF-007` | `REQ_IF_007_PAIRING` | Assertion + scoreboard | Every delivered instruction has its accepted request's PC and matching data, including after discard | `COV_IF_PAIRING` | Planned | — |
| Interface contract; `ISSUE-IF-001` | `CHK_IF_REQUEST_STABILITY` | Protocol monitor | Request valid/address remain stable until accepted; vary stall/redirect/outReady while ready is low; report known conflict | `COV_IF_REQ_WITHDRAWAL` | Planned | — |

The request-stability monitor currently has no dedicated normative `REQ-*` ID.
Resolve its conflict with redirect/request gating and assign a spec requirement
before encoding it as an iabv property; do not invent a requirement in the JSON.

## 6. Coverage and stimulus

Every coverage row is planned. Require at least one measured hit for each resolved
bin, including completion after a blocked/discarded transaction. A printed
scenario name or stimulus attempt does not establish that a cover condition fired.

| Coverage ID | Required scenario / bins |
| --- | --- |
| `COV_IF_RESET` | Idle reset, delayed first acceptance, first fetch at resetPc |
| `COV_IF_DELAYED` | Response delays of 1, 2, 8 and 32 cycles; eventual delivery for each |
| `COV_IF_SEQUENCE` | At least two consecutive completed sequential transactions |
| `COV_IF_WRAP` | Fetch at aligned address `2^64 - 4`, then fetch at zero |
| `COV_IF_REQ_BLOCKED` | Request ready low for 1 and multiple cycles, followed by acceptance |
| `COV_IF_RESP_BLOCKED` | Live response held by stall alone, outReady low alone, and both; each releases and delivers once |
| `COV_IF_REDIRECT_IDLE` | Redirect without pending request, then accept target request |
| `COV_IF_REDIRECT_PENDING` | Redirect during delayed response, discard it, then complete target fetch |
| `COV_IF_REDIRECT_RESPONSE` | Redirect and response handshake in same cycle; stale output suppressed |
| `COV_IF_DISCARD_BLOCKED` | Stale response drains with stall alone, outReady low alone, and both |
| `COV_IF_REQUEST_GATES` | All seven nonempty combinations of redirect, stall and outReady low while idle |
| `COV_IF_PAIRING` | Distinct PC/data pairs before and after redirect; no stale or duplicate delivery |
| `COV_IF_REQ_WITHDRAWAL` | Blocked request followed by stall, outReady low or redirect; observe each with protocol monitor |
| `COV_IF_REDIRECT_REPEAT` | Multiple redirects before next request, idle and pending; expected result awaits `ISSUE-IF-006` |
| `COV_IF_RESET_REDIRECT` | Redirect after reset but before first accepted request; expected result awaits `ISSUE-IF-005` |
| `COV_IF_RESET_PENDING` | Reset while awaiting/holding response; expected result awaits `ISSUE-IF-007` |

Run directed cases first, then at least 10,000 stimulus cycles for each proposed
seed `0x5eed`, `0x1`, and `0xc0ffee`. Vary memory latency over 1..32 cycles and
independently vary control/readiness signals. Drain outstanding traffic and
pending next-cycle checks at the end. Record watchdog expiry as an incomplete or
failed run, never a pass. These settings provide finite simulation coverage only.

## 7. Acceptance and evidence

1. Review property semantics against the spec; resolve questions affecting each
   property and reconcile known protocol conflicts.
2. Compile the generated harness and bind both assertion and cover layers in
   Verilator. Inspect generated properties and compiled layer inputs.
3. Require zero unexpected assertion or scoreboard failures and all resolved
   coverage bins hit. Measure antecedent activation to expose vacuity.
4. Exercise planned mutations in isolated RTL copies: wrong sequential increment
   (sequence check), missing stale-response suppression (discard check), and wrong
   output PC (pairing check). Each must fail its intended generated assertion
   with the reference scoreboard disabled for that mutation run. Build failures
   and unrelated crashes do not count as detection.
5. Preserve a negative protocol test for `ISSUE-IF-001`. While a required contract
   fails or an acceptance question remains unresolved, report partial verification;
   full signoff requires resolution or an explicit, scoped review waiver.
6. Record Seed and iabv revisions/hashes, dirty-source hashes where applicable,
   plan/spec versions, parameters, commands, seeds, generated property mappings,
   layer bindings, measured coverage, mutation failures and report/log paths.

Formal status remains `not_run` for simulation evidence. Local artifacts belong
under `build/verification/<run-id>/`; shared evidence needs a retained CI artifact
or an intentional review archive, rather than another user's local path.

## 8. Open items

| ID / source | Question or gap | Effect / resolution needed |
| --- | --- | --- |
| `ISSUE-IF-001` | Request withdrawal conflicts with interface stability | Preserve protocol scenario; agree contract/design resolution before signoff |
| `ISSUE-IF-002` | Response errors do not become access faults | Successful responses in baseline; exception behavior uncovered |
| `ISSUE-IF-003` | Address-alignment exceptions absent | Functional address checks do not establish architectural exception handling |
| `ISSUE-IF-004` | Single outstanding request limits throughput | ICache throughput redesign outside scope |
| `ISSUE-IF-005` | Redirect before first accepted request after reset | Clarify reset-first-address versus redirect-target requirements |
| `ISSUE-IF-006` | Multiple redirects before next request | Confirm target selection; latest sampled target is a proposal, not a baselined requirement |
| `ISSUE-IF-007` | Reset with outstanding response and interface activity during reset | Define cancellation/draining and handshake acceptance; agree scoreboard epoch policy |
| `PLAN-IF-001` | Referenced PCReg child spec currently missing | Restore it or agree replacement before closing child traceability; existing PCReg job references missing path too |
| `PLAN-IF-002` | Harness, JSON plan, job, monitors and mutations absent | Implement after plan review; all checks remain Planned |

## 9. Run history

| Run / date | Baseline | Command / seed | Result | Evidence |
| --- | --- | --- | --- | --- |
| — | IFU spec 0.2; plan 0.1 | — | Not run | — |
