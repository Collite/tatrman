# ttr-translator extraction arc — Tasks overview

> Plan: [plan.md](./plan.md) · Architecture/contracts: [`../../architecture/`](../../architecture/) · Kantheon-side lists: `Collite/kantheon` `docs/implementation/v1/ttr-translator/`.
>
> **Coder protocol** (same as TTR-P): work one stage list top-to-bottom; check `[x]` IMMEDIATELY after each task's verification passes — never batch; blocked ⇒ STOP, record under that list's §Blockers, do not improvise. `[x]` = intent; `/review` verifies against runtime.

## Stage tracker

| Stage | List | Repo | Status |
|---|---|---|---|
| A1 · Proto module + toolchain | [tasks-a1-toolchain-proto.md](./tasks-a1-toolchain-proto.md) | tatrman | [ ] |
| A2 · Code move | [tasks-a2-code-move.md](./tasks-a2-code-move.md) | tatrman | [ ] |
| A3 · Publish + v0.1.0 | [tasks-a3-publish.md](./tasks-a3-publish.md) | tatrman | [ ] |
| — TTR-P Phase 3 gate opens — | | | |
| B1 · Proto adoption | `docs/implementation/v1/ttr-translator/tasks-b1-proto-adoption.md` | kantheon | [ ] |
| B2 · Translator switch | `docs/implementation/v1/ttr-translator/tasks-b2-translator-switch.md` | kantheon | [ ] |
| B3 · Docs, guards, tags | `docs/implementation/v1/ttr-translator/tasks-b3-docs-tags.md` | kantheon | [ ] |

## Review queue

`/review` after A2 (the move itself — highest-risk diff), after A3 (publish plumbing), and kantheon-side after B2 (the switch). Reviews land here as `review-NNN.md` + `tasks-review-NNN.md` (serial numbering, shared convention).

## Blockers register

_(empty)_
