# 1.1.B.4 — New diagnostic codes

**Goal:** wire the validator to emit the twelve new diagnostic codes specified in [contracts §6](../../design/v1-1-contracts.md#6-diagnostic-codes-v11-additions), update `docs/v1/design/diagnostics.md` with one entry per code, ship one broken-sample fixture per code under `samples/broken/v1.1/`.

**Reads:** [contracts §6](../../design/v1-1-contracts.md#6-diagnostic-codes-v11-additions), `packages/semantics/src/validator.ts`, `docs/v1/design/diagnostics.md`.
**Blocked by:** 1.1.B.3.
**Blocks:** C1 (its diagnostics build on top), H (completion uses code-action quick-fixes per I3), I3.
**Estimated time:** 1.5–2 days.

## Tests-first

- [x] `packages/semantics/src/__tests__/diagnostics-v1.1.test.ts` — new file. One test per code, asserting that the right fixture produces exactly the expected diagnostic (`code`, `severity`, `source: 'modeler'`, and a roughly-correct message substring). Twelve cases:
  - `ttr/unimported-reference` (Info), `ttr/unused-import` (Warning), `ttr/wildcard-with-no-matches` (Warning), `ttr/duplicate-import` (Warning), `ttr/circular-package-dependency` (Warning), `ttr/package-declaration-mismatch` (Error), `ttr/missing-package-declaration` (Info), `ttr/ambiguous-reference` (Error), `ttr/wrong-file-kind` (Error — already wired in B1.5; verify here too), `ttr/graph-object-not-found` (Warning — placeholder; full validation in C1), `ttr/graph-layout-stale-node` (Warning — placeholder; full validation in C1), `ttr/file-ordering` (Warning).
- [x] Fixtures: one file per code under `samples/broken/v1.1/<code-without-prefix>.ttr` (e.g. `samples/broken/v1.1/unimported-reference.ttr`). Each fixture is the minimal valid project layout that triggers exactly that code.

## Library reference

No external libraries. The diagnostic-emission pattern is already established in `validator.ts`. Look at how `ttr/unresolved-reference` is emitted — same shape, just new codes.

## Implementation tasks

- [x] **B4.1 — Add the twelve codes to the `DiagnosticCode` union.** Search for the existing `'ttr/parse-error' | 'ttr/unknown-property' | ...` union and extend it. Place the new codes in code order; keep `'ttr/*'` prefix.
- [x] **B4.2 — Emit `ttr/package-declaration-mismatch` and `ttr/missing-package-declaration`.** In the validator's per-document pass, compare `document.packageDecl?.name` against the inferred-from-path package name (the latter computed from `documentUri` relative to the project root). Mismatch → Error; missing declaration on a non-root file → Info. The path-inference helper goes in `packages/semantics/src/package-inference.ts`.
- [x] **B4.3 — Emit `ttr/unimported-reference`, `ttr/ambiguous-reference`.** Hook into the resolver's output: when `viaStep === 'fully-qualified'` AND the symbol's package isn't in the document's import set, emit `ttr/unimported-reference` (Info). When the resolver returns `reason: 'ambiguous'`, emit `ttr/ambiguous-reference` (Error). Both at the reference site.
- [x] **B4.4 — Emit `ttr/unused-import`, `ttr/wildcard-with-no-matches`, `ttr/duplicate-import`.** Per-document pass: track which `ImportDecl` IDs were used (extend the resolver to log this, or count post-hoc by iterating refs). Unused → Warning at the import statement. Wildcard whose target package has zero defs → Warning. Same target imported twice → Warning on the second import.
- [x] **B4.5 — Emit `ttr/circular-package-dependency`.** Per-project pass: call `PackageGraphBuilder.findCycles()`. For each cycle, emit one Warning per participating package (at line 1 of an arbitrary file in that package). Message: `"Package X is part of a cycle: X → Y → X. Cycles parse cleanly but make dependency reasoning harder."`.
- [x] **B4.6 — Emit `ttr/file-ordering`.** Per-document pass: walk the AST in source order; record positions of `packageDecl`, `imports`, `schemaDirective`, `graph`, `definitions`. If any out-of-order pair is found (imports after schema, package after imports, etc.), emit one Warning on the offending node citing the canonical order.

  > **Contracts §1.4 note:** The current grammar production `document: packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF` is order-strict (package → imports → schema → defs). Contracts §1.4's "The grammar permits all permutations the productions allow" is out of sync with the grammar. If B4 implements the permissive variant (where the grammar accepts any order and `ttr/file-ordering` is the only warning), that is a deliberate redesign requiring a contract amendment. Do not silently leave the doc out of sync with the grammar.
- [x] **B4.7 — Update `docs/v1/design/diagnostics.md`.** Add one section per new code: trigger, severity, before/after example, fix. Use the existing entries (e.g. `ttr/unresolved-reference`) as a template. Place new codes after the existing v1 entries.
- [x] **B4.8 — Wire the validator into the LSP's publishDiagnostics flow.** Confirm the new codes appear in VS Code's Problems panel when a fixture is opened. (No new LSP code needed if the v1 publishDiagnostics already iterates `Validator.diagnose(...).diagnostics`; just verify.)
- [x] **B4.9 — Carry-over from B3: fix the `workspace/symbol` kind-ranking regression.** Under v1.1's kind-fallback qname rule (contracts §3.1), a `def er2db_relation X` in `schema map` is stored as `map.er2dbRelation.X` — qnames containing the substring `"Relation"`. The LSP's `workspace/symbol` handler uses fuzzysort over `qname` + `name` with `limit: 100` (`packages/lsp/src/server.ts:582–585`), so a query like `"rel"` now fuzzy-matches `er2dbRelation` qnames ahead of actual `relation`-kind defs (whose qnames are `er.entity.<rel_name>`, no `"rel"` substring). The integration test `symbol-indexing-extended.test.ts > workspace/symbol query="rel" includes at least one relation kind entry` fails as a result — and was tracked through reviews 027 + 028 as a known v1.1 cascade.

  Pick one of:
  - **(recommended)** Re-rank by exact kind-name match. After fuzzysort runs, if any results have `entry.kind === query` (or `entry.kind` matches the query verbatim), float them to the top of the returned list. Cheap, surgical, doesn't change the search semantics for queries that aren't kind names.
  - Raise the `limit: 100` — papers over the symptom without fixing the underlying ranking. Acceptable only if the fuzzysort scoring already would put relations in the top 200, which needs verification.
  - Index `kind` as a fuzzysort key alongside `qname`/`name`. Risks new ranking surprises.

  Acceptance: `pnpm --filter @modeler/integration-tests test` reaches 29/29.

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
pnpm -r lint
```

Open one of the broken fixtures in VS Code (manual smoke check) — Problems panel shows the expected code and severity.

## DONE when

- [x] Every checkbox above is ticked.
- [x] Twelve test cases in `diagnostics-v1.1.test.ts` pass.
- [x] Twelve fixtures in `samples/broken/v1.1/` exist and produce exactly the expected diagnostic.
- [x] `docs/v1/design/diagnostics.md` documents every new code.
- [x] `.ttrg`-specific codes (`ttr/graph-object-not-found`, `ttr/graph-layout-stale-node`) are placeholder-wired here (the validator knows the codes; C1 will trigger them from actual `.ttrg` parsing).
