# Linter / Formatter / Autofix — Task Management

This is the index for the implementation task lists. Each list below is a self-contained unit of
6–8 tasks. **Work them top to bottom; tasks within a list are ordered (tests first, TDD).**

> **Instruction to every implementer (coding agent or junior dev):**
> 1. Read [`../design.md`](../design.md), [`architecture.md`](../architecture.md), and
>    [`contracts.md`](../contracts.md) before starting a list. Implement to the contracts exactly.
> 2. Do the tasks **in order**. The first tasks in each list write tests; make them fail first,
>    then implement to green.
> 3. **Tick the checkbox the moment a task is done** (`[ ]` → `[x]`) and commit with
>    `Section <phase>.<stage>: <task>`.
> 4. A list is complete only when every box is ticked **and** `pnpm -r build`, `pnpm -r test`,
>    `pnpm -r typecheck`, `pnpm -r lint` are all green.
> 5. Never use `any` outside `generated/**`. Use `.js` extensions on relative imports.

## Progress

### P0 — Lossless CST & trivia (parser) — *blocks all other phases*
- [x] [P0 — CST & trivia foundation](P0-cst.md) — *ai-platform grammar sync deferred (see P0 task 7 note)*

### P1 — Formatter package (`@modeler/format`) — *parallel with P2 after P0*
- [x] [P1a — Extract formatter into `@modeler/format`](P1a-format-extract.md)
- [ ] [P1b — Comment-aware printing + `ttr fmt` CLI + LSP rewire](P1b-format-comments-cli.md)

### P2 — Lint package & rule model (`@modeler/lint`)
- [ ] [P2a — Lint package foundation (types, registry, runner)](P2a-lint-foundation.md)
- [ ] [P2b — Port the 26 checks to rules](P2b-lint-port-rules.md)
- [ ] [P2c — Suppression + LSP rewire + golden test](P2c-lint-suppression-integration.md)

### P3 — Configuration (`.ttrlint.toml`)
- [ ] [P3 — Config schema, precedence, presets, back-compat, watch](P3-config.md)

### P4 — Autofix (`ttr lint --fix`, CodeActions)
- [ ] [P4 — Fix model, re-home quick-fixes, `--fix`, CodeActions](P4-autofix.md)

## Phase gates

A phase is DONE when its lists are fully ticked and the phase's "DONE" in
[`implementation-plan.md`](implementation-plan.md) is met. Do not start a dependent phase before its
pre-flight conditions hold (P0 blocks all; P3 needs P2; P4 needs P0+P2, and P3 for `[fix]`).

## Dependency map

```
P0 ──► P1a ──► P1b
  └──► P2a ──► P2b ──► P2c ──► P3 ──► P4
```
