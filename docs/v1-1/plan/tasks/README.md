# v1.1 — Task lists

**Status:** v1, 2026-05-18. This document is the **overall task-management document** for v1.1. It does not contain implementation tasks itself. Implementation tasks live in numbered files in this directory. Each is a mini-task-list of 6–8 tasks max, structured TDD-first (tests defined before implementation), with embedded library references.

## Reading list (do this once, before starting any task file)

1. **[`docs/v1-1/design/v1-1-contracts.md`](../../design/v1-1-contracts.md)** — every type, every grammar token, every LSP method shape, every diagnostic code. Single source of truth for shapes; mini-task-lists never override it.
2. **[`docs/v1-1/design/v1.1-packages-and-graphs.md`](../../design/v1.1-packages-and-graphs.md)** — design rationale, decisions table (B1–B13), open questions (all resolved per the brainstorm).
3. **[`docs/v1-1/design/grammar-v1-1-changes.md`](../../design/grammar-v1-1-changes.md)** — ai-platform coordination doc; describes the grammar diff at the level the Kotlin parser maintainer needs.
4. **[`docs/v1-1/plan/implementation-plan-v1.1.md`](../implementation-plan-v1.1.md)** — phased plan with sub-phase table, deliverables, acceptance criteria, risks.
5. **[`docs/v1/design/architecture.md`](../../../v1/design/architecture.md)** — v1 architecture; especially §4.2 (parser), §4.3 (semantics), §4.5 (LSP), §6 (`.ttrl` — being removed in v1.1).
6. **[`docs/v1/design/diagnostics.md`](../../../v1/design/diagnostics.md)** — existing diagnostic taxonomy. v1.1 adds twelve new codes (contracts §6).
7. **[`CLAUDE.md`](../../../../CLAUDE.md)** — invariants. Note especially: text-is-canonical, one-LSP-across-hosts, source-locations-on-every-node, `SourceLocation` ANTLR-style, `vscode-languageserver` deep import.

## Sub-phase index

| Sub-phase | Mini-task-list                                       | Subject                                                                   |
| --------- | ---------------------------------------------------- | ------------------------------------------------------------------------- |
| 1.1.A     | [`A-grammar.md`](A-grammar.md)                       | Grammar additions (`package`, `import`, `graph` keywords + rules)         |
| 1.1.B.1   | [`B1-ast-extension.md`](B1-ast-extension.md)         | AST nodes: `PackageDecl`, `ImportDecl`, `GraphBlock`                      |
| 1.1.B.2   | [`B2-symbol-table.md`](B2-symbol-table.md)           | Package-aware symbol table; package-prefixed qnames                       |
| 1.1.B.3   | [`B3-resolver.md`](B3-resolver.md)                   | Six-step resolver chain; `PackageGraph` module                            |
| 1.1.B.4   | [`B4-diagnostics.md`](B4-diagnostics.md)             | Eight new diagnostic codes + validator hookups                            |
| 1.1.C.1   | [`C1-ttrg-parsing.md`](C1-ttrg-parsing.md)           | `.ttrg` file-kind dispatch; `Graph` AST validation; edge inclusion       |
| 1.1.C.2   | [`C2-ttrg-lsp-methods.md`](C2-ttrg-lsp-methods.md)   | Six new LSP custom methods; three updated existing methods                |
| 1.1.D     | [`D-vscode-ext.md`](D-vscode-ext.md)                 | VS Code: `.ttrg` language registration; TextMate; `.ttrl` removal         |
| 1.1.E.1   | [`E1-graph-picker.md`](E1-graph-picker.md)           | Entry modes: open existing graph, browse project graphs                   |
| 1.1.E.2   | [`E2-create-wizard.md`](E2-create-wizard.md)         | Create-new-graph multi-step wizard (5 steps)                              |
| 1.1.E.3   | [`E3-canvas-affordances.md`](E3-canvas-affordances.md) | Add-object, remove-object, extend-imports, missing-objects badge        |
| 1.1.E.4   | [`E4-reducer-layout.md`](E4-reducer-layout.md)       | Reducer rewire; per-graph layout persistence; schema toggle decision      |
| 1.1.F     | [`F-migration-cli.md`](F-migration-cli.md)           | `modeler migrate-to-packages` CLI                                         |
| 1.1.G     | [`G-samples-docs.md`](G-samples-docs.md)             | Migrate samples; update architecture.md; finalise grammar diff            |
| 1.1.H.1   | [`H1-reference-completion.md`](H1-reference-completion.md) | Reference + auto-import completion                                    |
| 1.1.H.2   | [`H2-other-completion.md`](H2-other-completion.md)   | Property name, schema kind, def kind, package name completion             |
| 1.1.H.3   | [`H3-symbols-settings.md`](H3-symbols-settings.md)   | `textDocument/documentSymbol`; `workspace/symbol`; completion settings    |
| 1.1.I.1   | [`I1-rename.md`](I1-rename.md)                       | `textDocument/rename`; `.ttrg` propagation; rename-package                |
| 1.1.I.2   | [`I2-formatting.md`](I2-formatting.md)               | `textDocument/formatting`; pretty-print rules + settings                  |
| 1.1.I.3   | [`I3-code-actions.md`](I3-code-actions.md)           | Quick-fixes for 4 diagnostics; extract-to-file refactor                   |
| 1.1.I.4   | [`I4-codelens-semantic.md`](I4-codelens-semantic.md) | Code lens (refs / files-in-package); semantic tokens enrichment           |

## Pre-flight (do once before starting 1.1.A)

- [ ] Confirm v1 (Phase 5) shipped and is on `main`.
- [ ] Branch `feat/v1.1-packages` from latest `main`.
- [ ] Create `docs/v1-1/plan/progress-phase-v1.1.md` mirroring this index; tick boxes there as sub-phases complete.
- [ ] Confirm Context7 MCP responds (`mcp__context7__resolve-library-id { libraryName: "antlr4ng", query: "lexer rules" }`).
- [ ] Read the reading list above.
- [ ] Read [`reviews.md`](../../../../reviews.md) — the `/review` cadence applies to v1.1 the same way it did to v1; reviews and task lists land in `docs/v1-1/implementation/`, continuing the serial numbering from where v1 left off (`review-025` onward).

## Contract amendment discipline

If a mini-task-list reveals a contract that needs to change, the implementer edits [`docs/v1-1/design/v1-1-contracts.md`](../../design/v1-1-contracts.md) *first*, the mini-task-list *second*, the implementation *third*. Do not patch around a contract divergence. Bump the version note at the top of the contracts file from v1 → v2 and add a one-line changelog entry naming each shape change.

## Critical path and parallelism

Dependency map per [`implementation-plan-v1.1.md`](../implementation-plan-v1.1.md):

```
A ──┬── B1 ── B2 ── B3 ── B4 ──┬── C1 ── C2 ── E1 ── E2 ── E3 ── E4
    │                          ├── F  ── G
    │                          ├── H1 ── H2 ── H3
    │                          └── I1 ── I2 ── I3 ── I4
    └── D
```

**Critical path:** A → B1 → B2 → B3 → C1 → C2 → E1 → E2 → E3 → E4 (≈ 5–6 weeks).
**Parallel tracks once B4 lands:** F → G (cleanup); H1 → H2 → H3 (productivity); I1 → I2 → I3 → I4 (polish); D (anytime after A).

## DONE when

- [ ] Every box in every mini-task-list is ticked.
- [ ] `progress-phase-v1.1.md` mirrors this state on disk.
- [ ] [`implementation-plan-v1.1.md`](../implementation-plan-v1.1.md) §"Acceptance summary" is fully satisfied.
- [ ] [`docs/v1/design/architecture.md`](../../../v1/design/architecture.md) is updated per 1.1.G to reflect v1.1's shape.
- [ ] ai-platform's parser maintainer has signed off on [`docs/v1-1/design/grammar-v1-1-changes.md`](../../design/grammar-v1-1-changes.md).
