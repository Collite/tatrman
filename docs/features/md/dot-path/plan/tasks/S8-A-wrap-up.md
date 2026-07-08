# S8-A — docs sync, publish, arc review

Goal: close the arc cleanly: documentation truth, published artifacts, serial review.

Prereq: S0–S7 all checked in INDEX.md.

## Tasks

- [ ] **S8-A1 — docs sync.** `docs/features/md/design.md` §11: Layer B dot-path half marked
  implemented → link `dot-path/`; `docs/features/md/dot-path-sugar.md` status line → "implemented
  — see dot-path/ artifacts"; `docs/ttr-p/design/00-control-room.md` D-h register updated (md
  sugar landed; algebra half still deferred); `docs/ttr-p/language-design.md` §14 D-h row —
  amend "arrives later" → shipped-in-md-sugar-arc note. Grep for stale "D3 (own session, parked)"
  references.
- [ ] **S8-A2 — CLAUDE.md.** Add `ttr-md-resolver` and `ttr-md-agent` to the Kotlin-module
  overview and the dependency-graph notes; one line on the float-literal rule (digits both sides;
  parser-composed) under conventions — future grammar editors must know.
- [ ] **S8-A3 — clean-checkout verification.** Fresh clone: `pnpm install && pnpm -r typecheck &&
  pnpm -r lint && pnpm -r build && pnpm -r test`; `./gradlew build`; conformance harness;
  S4-B/S5-B E2E suites. All green with zero local state.
- [ ] **S8-A4 — publish.** Per PUBLISHING.md: tag `kotlin/v<x.y.z>` (or the per-artifact tags) so
  `ttr-md-resolver`, updated `ttr-semantics`, `ttr-metadata` ship; `kotlin-translator/v*`
  lockstep tag for the translator + plan-proto if S4/S5 changed wire-adjacent code. Record
  versions here.
- [ ] **S8-A5 — arc review.** Run the `/review` cadence: `review-NNN.md` +
  `tasks-review-NNN.md` (serial numbering continues the project-wide sequence) under this arc's
  `plan/` — verify progress claims against runtime (checkboxes are intent, not truth), with
  special attention to: R23 shadowing determinism, the S2-B search bound, S5 strict-LHS
  negatives, S6 fingerprint properties.
- [ ] **S8-A6 — deferred register.** Confirm `../implementation-plan.md` §"Deferred follow-ups"
  matches reality (TS/LSP arc, with-blocks, tuples, per-expression asof, safe nav, scientific
  notation, MOLAP); each with a one-line seat description. Close review findings or move them
  here with owners.

## Coder notes

_(empty — versions + review number land here)_

## References

- PUBLISHING.md · `docs/grammar-master/` (if S0's version needs a consumer note) · the repo's
  phase-review cadence (CLAUDE.md §Phase review cadence).
