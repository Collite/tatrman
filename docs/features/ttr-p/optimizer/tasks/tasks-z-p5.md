# Z-P5 — Language surface & B-T9 amendment implementation

> Pre-flight: Z-P4 done; TTR-P grammar window (spec-version bump per S6). Grammar work follows `CLAUDE.md` §Grammar regeneration + the TTR-P grammar conventions (`TTRP.g4` beside `TTR.g4`). DoD: [`../plan.md`](../plan.md) §Z-P5.

## S1 · Grammar delta, model fields, Z-off degradation {#s1}

Verify: TTR-P parser + front-half test suites green; **full v1 golden corpus byte-identical** (the regression gate).

- [ ] **T1 (tests first).** Parser tests (TTR-P grammar suite): `"container crunch prefer erp_pg { … } parses with strength HINT"` · `"container prep together { … } parses, no target"` · `"container prep together prefer pg"` · `"bare container parses (grouping-only)"` · `"target keeps parsing exactly as v1"` (reuse three existing v1 corpus files verbatim as fixtures) · negative: `"prefer without engine ref = syntax error with position"`.
- [ ] **T2 (tests first).** Model/ingest tests (front-half suite): `"target ⇒ strength DIRECTIVE, together=false"` (v1-ingest rule, contracts §1) · `"prefer ⇒ HINT"` · `"together flag independent of placement"`.
- [ ] **T3 (tests first).** Z-off degradation tests: with `[ttrp] optimize=off` — `"prefer erp_pg compiles exactly as target erp_pg"` (**assert the two programs' bundles are byte-identical**, GI-22) · `"grouping-only container contents resolve via bare-target default"` · `"grouping-only without default ⇒ TTRP-OPT-020 with container name and 'add target/prefer or set [ttrp] bare-target' suggestion"`.
- [ ] **T4.** Extend `TTRP.g4` per contracts §1 EBNF (`prefer`, `together` keywords; keyword table update per S16); regenerate per procedure; TextMate/highlighting entries for both keywords.
- [ ] **T5.** Extend the Container model with the three fields (contracts §1) + the ingest rule; thread through the graph builder; update `ttrp/getGraph` payload (containers carry `strength`/`together`/`derived`).
- [ ] **T6.** Implement Z-off lowering in the placement-check stage: HINT→DIRECTIVE rewrite; grouping-only fallthrough to defaults; OPT-020 emission.
- [ ] **T7.** Run the **whole v1 golden emit corpus** — zero diffs (all-directive special case proof); spec-version bump recorded (S6). Commit `Z-P5.S1: prefer/together grammar + model + Z-off degradation`.

## S2 · Formatter, LSP, problem-builder mapping {#s2}

Verify: front-half + LSP + `ttr-optimizer` suites green.

- [ ] **T1 (tests first).** Formatter tests: canonical ordering `container <name> together prefer <e>` (together before placement — fix the order, both author orders accepted, formatter normalizes) · fragment-container form untouched interiors (C2-f holds).
- [ ] **T2 (tests first).** LSP tests (integration-harness pattern, `tests/` conventions): hover on `prefer` shows "placement hint — optimizer may deviate (penalty from profile); without optimizer behaves as target" · rename of an engine ref inside `prefer` works like `target` (same ref-kind) · diagnostics OPT-020 surfaces with range on the container header.
- [ ] **T3 (tests first).** `ProblemBuilderMappingTest` (in `ttr-optimizer`, against the real graph model now — replace `FixtureGraph` usages *only where the real model is available*; keep the seam for pure-solver tests): DIRECTIVE→pins, HINT→hints with profile penalty, together→cohesion groups, grouping-only→nothing, no containers→all movable. (Same assertions as Z-P2.S1-T1, now through real syntax — copy the test, swap the source.)
- [ ] **T4.** Implement formatter + LSP surface changes; wire `OpGraphSource` production implementation (adapter over the front-half graph — the Z-P0 seam's real backing).
- [ ] **T5.** Property test (front-half): for randomly generated small programs (existing generator if present; else 10 hand fixtures), Z-off bundle output is invariant under `prefer`↔`target` textual swap.
- [ ] **T6.** Designer-side ticket filed (not this repo's blocker): container-role affordances per skin (directive solid / hint tinted / grouping thin / derived dashed-lock) — reference C1 skin-roster leftover. Record ticket link here: ______
- [ ] **T7.** Green; tracker; commit `Z-P5.S2: formatter/LSP surface + real graph mapping`.
