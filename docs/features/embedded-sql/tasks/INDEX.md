# Embedded SQL — task index

**Use this index to navigate.** Each linked file is a mini-task-list (6–8 tasks)
for one coding session by one developer/agent. **Check each box the moment the
task is done — do not batch.**

**Read first (normative):**
- [`../embedded-language-blocks.md`](../embedded-language-blocks.md) — **DESIGN**:
  rationale, value-extraction algorithm, golden cases, parser-selection.
- [`../embedded-sql/architecture.md`](../embedded-sql/architecture.md) — components,
  pipeline, host split.
- [`../embedded-sql/contracts.md`](../embedded-sql/contracts.md) — grammar rules,
  `TaggedBlockValue`, `SqlRefModel`, `modeler.toml` schema, identifier folding.
- [`../embedded-sql/plan.md`](../embedded-sql/plan.md) — phases, gates, DoD.

**Conventions:**
- **TDD.** Within a stage, tests are written first and must fail (red) against the
  missing production type — never on a spec bug. Define the tests from DESIGN §9
  and contracts; do not invent behaviour.
- **Docs win.** If a task disagrees with `contracts.md` / `architecture.md` /
  DESIGN, the docs are right — open a discussion before diverging.
- **antlr4ng usage pattern** (from `packages/parser/src/walker.ts`):
  ```ts
  import { CharStream, CommonTokenStream } from 'antlr4ng';
  const input = CharStream.fromString(sqlValue);
  const lexer = new TSqlLexer(input);
  const tokens = new CommonTokenStream(lexer);
  tokens.fill();                 // lexer-first: iterate tokens.getTokens()
  // parser phase: const parser = new TSqlParser(tokens);
  ```
- **No SNAPSHOTs.** Kotlin cross-repo iteration uses `publishToMavenLocal` + a
  temporary `mavenLocal()` reverted before commit.
- Commit style: `Section <X>: <desc>` or `<scope>: <desc>`; commit + push per stage.
- **Grammar regen** after any `TTR.g4` edit: `cd packages/parser && pnpm run prebuild`,
  then `node packages/vscode-ext/scripts/generate-tm-grammar.ts` if highlighting
  tokens changed.

---

## Phase 0 — Spikes & evaluation (GATING) — `packages/sql-spike` (scratch)

All three gates must be green before Phases 2+ start.

| Stage | Mini-task-list | Gate | Status |
|---|---|---|---|
| S0.1 | [`antlr-ng` generates tsql + postgresql](S0.1-antlr-ng-generation.md) | grammars generate & instantiate; case-insensitivity solved | ☐ |
| S0.2 | [Real-query parse + span fidelity](S0.2-parse-and-span-eval.md) | corpus lexes 100% / parses ≥ threshold; tight spans; backlog catalogued | ☐ |
| S0.3 | [Bundle-size + host split](S0.3-bundle-size-eval.md) | lexer fits Worker budget; parser sizes measured | ☐ |

**Phase 0 DoD:** `packages/sql-spike/spike-report.md` records all three gates with
measured numbers + the case-insensitivity choice + the lazy-patch backlog. On
S0.1 hard-fail → fall back to `node-sql-parser` and re-plan Phases 2–4.

## Phase 1 — Tagged-block grammar + value contract — modeler

Independent of SQL grammars; may run in parallel with Phase 0.

| Stage | Mini-task-list | Status |
|---|---|---|
| 1.1 | [Conformance fixtures first (red)](1.1-conformance-fixtures.md) | ☐ |
| 1.2 | [Grammar: TAGGED_BLOCK_LITERAL + embeddedBlock](1.2-grammar.md) | ☐ |
| 1.3 | [TS walker: TaggedBlockValue + tag registry](1.3-ts-walker.md) | ☐ |
| 1.4 | [Kotlin walker mirror + conformance green](1.4-kotlin-walker.md) | ☐ |
| 1.5 | [ttr-writer round-trip + publish](1.5-writer-and-publish.md) | ☐ |

**Phase 1 DoD:** see [`plan.md`](../embedded-sql/plan.md) Phase 1.

## Phase 2 — Lexer-first highlighting — modeler *(post-gate)*

Task lists authored once the Phase 0 gate is green. Planned stages:

| Stage | Mini-task-list | Status |
|---|---|---|
| 2.1 | Vendor + generate tsql/postgresql lexers (pinned) | ☐ (not yet written) |
| 2.2 | SQL lexer service + dialect selection | ☐ (not yet written) |
| 2.3 | Source map + LSP embedded semantic tokens | ☐ (not yet written) |
| 2.4 | VS Code + Designer wiring; broken-SQL highlight test | ☐ (not yet written) |

## Phase 3 — Best-effort semantics — modeler *(post-Phase-2)*

| Stage | Mini-task-list | Status |
|---|---|---|
| 3.1 | SqlRefModel contract + extraction tests | ☐ (not yet written) |
| 3.2 | Error-tolerant parsers + per-dialect adapters | ☐ (not yet written) |
| 3.3 | modeler.toml SQL config + loader | ☐ (not yet written) |
| 3.4 | Resolver + identifier folding + diagnostics | ☐ (not yet written) |
| 3.5 | Param cross-check | ☐ (not yet written) |

## Phase 4 — IDE features — modeler *(post-Phase-3)*

Hover / go-to-def / find-refs / completion / rename. Task lists authored after
Phase 3.
