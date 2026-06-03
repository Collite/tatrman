# v2.1 — per-section task lists

Detailed task lists for a developer (junior-friendly) executing each section of the v2.1 inline-mappings feature. Each doc lists specific files, line numbers, code snippets, exact commands, and verification steps.

Read these in order:

1. [section-B-grammar.md](section-B-grammar.md) — Grammar additions, regen the parser, regen the TextMate grammar.
2. [section-C-parser-ast.md](section-C-parser-ast.md) — AST node types + walker (TDD-first).
3. [section-D-synthesizer.md](section-D-synthesizer.md) — Convert inline `mapping:` AST into synthesized `er2db_*` symbols.
4. [section-E-conflict-validator.md](section-E-conflict-validator.md) — `ttr/duplicate-mapping` diagnostic for inline ↔ explicit collisions.
5. [section-F-integration-tests.md](section-F-integration-tests.md) — Rewrite `samples/2.1/` + end-to-end integration tests.
6. [section-G-ai-platform.md](section-G-ai-platform.md) — Mirror Sections C–E on the Kotlin side in `~/Dev/ai-platform`.
7. [section-H-wrap-up.md](section-H-wrap-up.md) — Progress log, final verification, PR descriptions.

Before starting any section, read:

- [`../../design/v2.1-inline-mappings.md`](../../design/v2.1-inline-mappings.md) — the design contract (decisions, four surface forms, semantic rules, conflict semantics).
- [`../../design/grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md) — the ai-platform-facing grammar contract.
- [`../implementation-plan-v2.1.md`](../implementation-plan-v2.1.md) — the high-level plan, dependencies, acceptance summary, and risks.
- [`/CLAUDE.md`](/CLAUDE.md) — repo-wide invariants. In particular: the ANTLR-style `SourceLocation` rule (`endColumn = stopToken.column + stopTokenLength`) and the "put new LSP feature tests in `tests/integration/`, not in `packages/lsp/__tests__/`" rule.

## How to work through a section

Each task doc is a checklist. The intended workflow:

1. **Read the entire doc once** before touching code. Tasks reference each other across the doc — don't start editing in the middle.
2. **TDD where called out.** Sections C, D, E start with a "Write the failing tests first" task. Follow it — implementing then writing tests after-the-fact regularly produces tests that pass trivially.
3. **Confirm line numbers.** Every line-number reference is a *planning snapshot* — confirm by reading the surrounding code before you edit. If the line moved, fix-up is fine; if the rule it points at is gone, stop and check the design has changed.
4. **Commit per section** with the message `Section <X>: <short description>` per `CLAUDE.md` convention. Don't squash sections together — preserve the per-section history.
5. **Run the verification block** at the bottom of every doc before considering the section done. The "all green" gates are real — don't move on while typecheck is red.

## When you're stuck

- **Grammar / parser surprises** — re-read [`grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md). It's the contract; if the code disagrees, the code is wrong.
- **Synthesizer surprises** — re-read design doc §4 (semantic model) and grammar-changes §4 (semantic-table representation). The "synthesize into project table, not document tables" rule (C6) is the single most load-bearing decision.
- **Cross-package collision questions** — design doc §4.2. Synthesized qnames use the host file's package, not the `map` schema's.
- **Anything else** — ask Bora. Open questions tracked in design doc §9.
