# F · Exposure & Orchestration — Options Catalogue

> **Status: diverged and CONVERGED 2026-07-12 in one pass — RS-27..30 in the control room §7.** F entered almost fully determined by RS-3..26; each fork below records its options and its resolution. This workstream answers **Q-4 (=RQ-4, the pre-debut gate)** and **Q-5 (=RQ-5)**, and supplies the `mcp-surface.md` §2.2 rows the RO-25 contract reserved.
> Standing rule inherited: the MCP surface extends additively under J-v2 (GI-2); every door gets H-2 identity (bearer-only, OBO, fail-closed).

---

## 1. What RS-3..26 already fixed

The doors can only expose what the services promise — and after E, the promises are crisp: the resolver core is **deterministic** (RS-23), grounding is **deterministic rules-first** (D inherited), fuzzy/nlp are deterministic primitives, and everything generative lives in the kantheon Resolving Agent, which is *not* a server component. That yields F's organizing line: **doors expose the deterministic; agents own the generative.**

## 2. F1 · Does `resolve` become a door? (Q-4/RQ-4)

**F1-α — no door; primitives suffice.** Third parties hand-roll call #1 from `meta.search` + `fuzzy.match` + `grounding.*`.
Buys: smallest surface; no new conformance burden.
Costs: hand-rolling call #1 means every third-party agent re-implements span gating, registry semantics, thresholds, clarification logic — the exact know-how the reference stack took years to tune; they will do it worse, and the ecosystem's answer quality becomes vendor-variable — against the conformance suite's whole purpose.

**F1-β — the `resolve` door: the deterministic core, exposed.** `ttr-resolver`'s core becomes an MCP tool (capability family `resolve.*:v1`; exact tool ids via the naming ledger at planning): text (or spans) in → deterministic bindings + universal spans + clarification options with signed resume tokens out. **The Resolving Agent's LLM ladder is never a door** — the door line is the determinism line.
Buys: third parties get call #1 as a governed, deterministic, provenance-carrying service they *cannot* hand-roll equivalently; RS-23 removed the old hesitation (the door can *promise* determinism); resume tokens are MCP-friendly (opaque strings, stateless); refusal-over-guess becomes contract-assertable at the door.
Costs: extends the RO-25 surface pre-debut (exactly why Q-4 had to close now); resolve fixtures join the conformance core tier; H-2 + OBO on one more door.

**F1-γ — composite `understand` door (resolve + ground in one call).** Server-side orchestration of the full cascade.
Buys: one call for lazy integrators.
Costs: the server starts owning turn logic (GroundingContext assembly needs turn state — `reference_datetime`, locale, here-ref — that only the agent has; passing it in is possible but the composition policy — what to ground, what's load-bearing — is agent judgment per the DFP cascade's own design); duplicates the Resolving Agent's job on the wrong side of the line. Additive later if third-party friction proves it out.

**F1-δ — resolution inside `query` (the weird one).** The query door accepts raw text.
Costs: collapses the two-call thesis — the seam that makes intent auditable. Recorded to mark why the seam exists.

**DECIDED — RS-27: β.** The door line = the determinism line; γ additive-later; Q-4 closed pre-debut as required.

## 3. F2 · Grounding tool shape

**F2-α — kind-named tools** (`grounding.time:v1`, `grounding.geo:v1`, `grounding.money:v1`-class, + capability/status surfaced). Matches the reserved `grounding.*:v1` id pattern; agents pick tools by name — LLM tool-selection ergonomics favor named tools over enum params; RS-20's checklist adds one tool per admitted grounder (additive under GI-2).
**F2-β — one generic `ground(kind, …)` tool.** Mirrors the generic proto; smallest tool list; costs tool-selection clarity and per-kind schema documentation.
**F2-γ — composite cascade tool.** F1-γ's argument again, smaller.

**DECIDED — RS-28: α** — kind-named tools riding the generic proto (the wrapper stays generic inside; the *names* are ergonomic outside); grounding fixtures join the suite when the tools land (mcp-surface §6 already schedules this).

## 4. F3 · Who orchestrates call #1

**F3-α — nobody; docs only.** Each vendor invents the cascade.
**F3-β — the resolve door + grounding primitives, with the canonical cascade documented and fixture-backed.** Third parties: call `resolve` (deterministic bindings + universal spans + kinds) → call `grounding.*` per span with their own GroundingContext → proceed to call #2. The conformance suite's fixtures (`calls:` assertions — the schema already reserves them) *are* the documentation of the canonical order. The Resolving Agent remains the reference orchestration for those who want the LLM ladder — kantheon code, not contract.
**F3-γ — server-side composite** (= F1-γ, rejected above, additive-later).

**DECIDED — RS-29: β** — composition is agent work; the contract gives deterministic pieces + a fixture-documented canonical cascade.

## 5. F4 · Parity & conformance mechanics (Q-5/RQ-5)

Three corpus tiers, resolved as a composition:

- **Gating, service-level (open, in-repo):** the ENTITIES_ONLY binding corpus + the grounding eval set (109 bulk + 21 E2E) + B's match-quality fixtures (Q-17) — self-contained, no DFP dependency, run in CI. **This is the SV-P3 "parity demonstrated" instrument for the deterministic core.**
- **Gating, E2E core tier (hand-authored):** resolution/grounding conversation fixtures join the RO-25 core tier (~25–40 fixtures @100%) — authored with the reference Golem at SV-P4 as already scheduled; the refusal-over-guess and clarification round-trip cases live here.
- **Non-gating, extended tier (pilot-derived):** the anonymized pilot conversation corpus (RO-19 ask ③ — the DFP conversation) scores the E2E parity claim across the RS-23 split; explicitly *scored, not gating* (RO-25's tier design absorbs the access risk).

**DECIDED — RS-30** — Q-5 closed: parity is measurable without DFP (service-level + core tier); the pilot corpus upgrades confidence but gates nothing.

## 6. Deliverable into the pinned surface

`mcp-surface.md` §2.2 gains (wording finalized in design.md): the `resolve` door row (`resolve.*:v1`, OBO, resume-token semantics, refusal-over-guess assertion) and the three grounding tool rows (`grounding.{time,geo,money}:v1`, capability/GetStatus surfacing, conditional-on-capability geo fixtures per RS-19). Both arrive under the J-v2 additive rules; no existing id changes.

## 7. Threads

- **F-T1 · Naming ledger entries:** exact tool/capability ids (`resolve.entities`? `resolve.bind`?) — planning + naming ledger; the family reservations are what this design pins.
- **F-T2 · Identity:** H-2 verbatim on both doors; the resolve door's per-user identity matters (clarification options may reflect user-visible vocabulary; RS-17's visible-by-declaration posture applies and is documented at the door too).
- **F-T3 · Parse passthrough:** the door response keeps `parse` (E-T1) — third parties get the NLP analysis without a second call; capability matrix (RS-7) echoed so consumers see what backed it.
