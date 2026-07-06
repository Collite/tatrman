# ttr-translator extraction arc — Tasks overview

> Plan: [plan.md](./plan.md) · Architecture/contracts: [`../../architecture/`](../../architecture/) · Kantheon-side lists: `Collite/kantheon` `docs/implementation/v1/ttr-translator/`.
>
> **Coder protocol** (same as TTR-P): work one stage list top-to-bottom; check `[x]` IMMEDIATELY after each task's verification passes — never batch; blocked ⇒ STOP, record under that list's §Blockers, do not improvise. `[x]` = intent; `/review` verifies against runtime.

## Stage tracker

| Stage | List | Repo | Status |
|---|---|---|---|
| A1 · Proto module + toolchain | [tasks-a1-toolchain-proto.md](./tasks-a1-toolchain-proto.md) | tatrman | [x] |
| A2 · Code move | [tasks-a2-code-move.md](./tasks-a2-code-move.md) | tatrman | [x] (reviewed — [review-064.md](./review-064.md)) |
| A3 · Publish + v0.1.0 | [tasks-a3-publish.md](./tasks-a3-publish.md) | tatrman | [ ] |
| — TTR-P Phase 3 gate opens — | | | |
| B1 · Proto adoption | `docs/implementation/v1/ttr-translator/tasks-b1-proto-adoption.md` | kantheon | [ ] |
| B2 · Translator switch | `docs/implementation/v1/ttr-translator/tasks-b2-translator-switch.md` | kantheon | [ ] |
| B3 · Docs, guards, tags | `docs/implementation/v1/ttr-translator/tasks-b3-docs-tags.md` | kantheon | [ ] |

## Review queue

`/review` after A2 (the move itself — highest-risk diff), after A3 (publish plumbing), and kantheon-side after B2 (the switch). Reviews land here as `review-NNN.md` + `tasks-review-NNN.md` (serial numbering, shared convention).

- **A2 → [review-064.md](./review-064.md) (2026-07-06):** parity proven (byte-identical modulo rename; 34/359 green); F1 (wrong rename token `org.tatrman.query.shared.translator` in contracts §4.2 — would no-op Phase B), F2/F3 (doc nits) all fixed in-review. Phase A clears the DONE bar; A3 may proceed.

## Blockers register

- **A2-1 (RESOLVED — Option A, Bora 2026-07-06)** — the translator's `shared/proto` dep was broader than the plan's "5 protos": it also needs `proteus/v1/translator.proto` (enum stub `Language`/`SqlDialect`) and the hand-written `SchemaCodes.kt` (`plan.v1.parseSchemaCode`/`schemaCodeToToken`). Both added to `ttr-plan-proto`, FQCNs unchanged; jar-guard 5→6; contracts bumped to v2. Full analysis in [tasks-a2-code-move.md](./tasks-a2-code-move.md) §Blockers.
