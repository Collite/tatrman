# Embedded SQL — task index

**Use this index to navigate.** Each linked file is a mini-task-list (6–8 tasks)
for one coding session by one developer/agent. **Check each box the moment the
task is done — do not batch.**

**Read first (normative):**
- [`../embedded-language-blocks.md`](../embedded-language-blocks.md) — **DESIGN**:
  rationale, value-extraction algorithm, golden cases, parser-selection.
- [`../spike-report.md`](../spike-report.md) — **SPIKE**: Phase 0 results, numbers.
- [`../architecture.md`](../architecture.md) — components, pipeline, host split.
- [`../contracts.md`](../contracts.md) — grammar rules, `TaggedBlockValue`,
  `maskPlaceholders`, `SqlRefModel`, `modeler.toml` schema, identifier folding.
- [`../plan.md`](../plan.md) — phases, gates, DoD.

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
| S0.1 | [`antlr-ng` generates tsql + postgresql](S0.1-antlr-ng-generation.md) | grammars generate & instantiate; case-insensitivity solved | ✅ PASS |
| S0.2 | [Real-query parse + span fidelity](S0.2-parse-and-span-eval.md) | corpus lexes 100% / parses ≥ threshold; tight spans; backlog catalogued | ✅ PASS |
| S0.3 | [Bundle-size + host split](S0.3-bundle-size-eval.md) | lexer fits Worker budget; parser sizes measured | ✅ PASS |

**Phase 0 gate: 🟢 GO** — ANTLR approach confirmed; `node-sql-parser` (E8)
fallback not triggered. Report: [`../spike-report.md`](../spike-report.md)
(the scratch `packages/sql-spike` was deleted at close-out per S0.3.6 — re-create
it from the report's recorded commands/SHA in [`PINNED.md`](PINNED.md) to
reproduce).

Headline findings carried forward:
- **Case-insensitivity** works natively via the grammars' `caseInsensitive`
  lexer option — no fallback needed (S0.1).
- **TTR `{param}` placeholders** are the *only* lex blocker; fixed by a
  span-preserving pre-pass (`maskPlaceholders`) — a **required Phase 2 carrier
  component**, not a grammar patch (S0.2).
- **lexer-only-in-browser confirmed**: both lexers +155 KB gz (~+49% Worker);
  both parsers +839 KB gz → parsers stay desktop-only (S0.3).

**Phase 0 DoD:** `spike-report.md` records all three gates with measured numbers,
the case-insensitivity choice, and the lazy-patch backlog. On S0.1 hard-fail →
fall back to `node-sql-parser` and re-plan Phases 2–4. **(Did not trigger.)**

## Phase 1 — Tagged-block grammar + value contract — modeler

Independent of SQL grammars; may run in parallel with Phase 0.

| Stage | Mini-task-list | Status |
|---|---|---|
| 1.1 | [Conformance fixtures first (red)](1.1-conformance-fixtures.md) | ✅ |
| 1.2 | [Grammar: TAGGED_BLOCK_LITERAL + embeddedBlock](1.2-grammar.md) | ✅ |
| 1.3 | [TS walker: TaggedBlockValue + tag registry](1.3-ts-walker.md) | ✅ |
| 1.4 | [Kotlin walker mirror + conformance green](1.4-kotlin-walker.md) | ✅ |
| 1.5 | [ttr-writer round-trip + publish](1.5-writer-and-publish.md) | ✅ render + round-trip + Maven Local 0.5.0; tag-publish pending |

**Phase 1 DoD:** see [`plan.md`](../plan.md) Phase 1. Code complete at `0.5.0`
(TS+Kotlin parsers, writer round-trip, 48-fixture conformance green, smoke-
published to Maven Local). **Remaining:** push the `kotlin/v0.5.0` bundle tag to
publish to GitHub Packages, then the ai-platform follow-up (bump `tatrman-modeler`
to `0.5.0`, add the `TaggedBlockValue` branch where `sourceText` is consumed,
read `language`/`dialect` from the node — DESIGN §10).

## Phase 2 — Lexer-first highlighting — modeler

**Pre-flight:** Phase 0 gate ✅; Phase 1 merged. New package `@modeler/sql`.

| Stage | Mini-task-list | Status |
|---|---|---|
| 2.1 | [Scaffold `@modeler/sql`: vendor + generate lexers/parsers](2.1-sql-package-and-generate.md) | ✅ |
| 2.2 | [`maskPlaceholders` span-preserving pre-pass (TDD)](2.2-mask-placeholders.md) | ✅ |
| 2.3 | [SQL lexer service + dialect selection + source map](2.3-lexer-service-and-sourcemap.md) | ✅ |
| 2.4 | [LSP embedded semantic tokens](2.4-lsp-semantic-tokens.md) | ✅ |
| 2.5 | [VS Code + Designer wiring; bundle + broken-SQL guards](2.5-host-wiring-and-bundle.md) | ✅ bundle guard @ 600 KB gz (actual ≈498); host eyeball pending |

**Phase 2 DoD:** keyword/literal/comment/operator/variable/`{param}` colouring
merged into the LSP semantic-token response, positioned via the §8 source map;
broken SQL still colours (lexer-first); untagged/non-SQL blocks untouched; Worker
bundle lexer-only (≈498 KB gz, guard at 600 KB catches a parser leak). Bare
identifiers (table/column → `class`/`property`) deferred to Phase 3.
**Manual eyeball still owed (2.5.1/2.5.4):** F5 VS Code + Designer screenshots for
the PR. Bundle ceiling set to 600 KB pending owner confirmation.

## Phase 3 — Best-effort semantics — modeler

**Pre-flight:** Phase 2 merged.

| Stage | Mini-task-list | Status |
|---|---|---|
| 3.1 | [`SqlRefModel` contract + adapter tests (red)](3.1-sqlrefmodel-tests.md) | ✅ |
| 3.2 | [Error-tolerant parsers + per-dialect adapters](3.2-parsers-and-adapters.md) | ✅ tsql + postgres adapters green; corpus 100% |
| 3.3 | [`modeler.toml` SQL config + loader](3.3-modeler-toml-config.md) | ✅ |
| 3.4 | [Resolver + identifier folding + diagnostics](3.4-resolver-and-diagnostics.md) | ✅ resolver + 4 diag codes; corpus FP pass (masked-param FP fixed) |
| 3.5 | [`parameters` cross-check](3.5-param-cross-check.md) | ☐ |

## Phase 4 — IDE features — modeler

**Pre-flight:** Phase 3 merged. Desktop-only.

| Stage | Mini-task-list | Status |
|---|---|---|
| 4.1 | [Hover (column type/description)](4.1-hover.md) | ☐ |
| 4.2 | [Go-to-definition (SQL ref → TTR db def)](4.2-go-to-definition.md) | ☐ |
| 4.3 | [Find-references (TTR db symbol → SQL usages)](4.3-find-references.md) | ☐ |
| 4.4 | [Completion (table/column names in SQL)](4.4-completion.md) | ☐ |
| 4.5 | [Rename across the boundary (edit-mode dependent)](4.5-rename.md) | ☐ |
