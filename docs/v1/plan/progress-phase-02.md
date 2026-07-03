# Phase 2 — Progress

**Started:** 2026-05-14
**Branch:** `feat/phase-02-core`
**Status:** Complete (with §L deferred)

## Pre-flight
- [x] Confirm Phase 1 acceptance criteria (build, test, lint, typecheck all green)
- [x] Create branch `feat/phase-02-core` from merged Phase 1 PR
- [x] Create `docs/plan/progress-phase-02.md` mirroring section headers (this file)
- [x] Re-read architecture docs (§4.2, §4.3, §4.5, §5, §8.2, §8.3)
- [x] Re-read `packages/grammar/src/TTR.g4`
- [x] Re-read `docs/plan/progress-phase-01.md` "Deferred to Later Phases"
- [x] Open sample files (`samples/v1-metadata/`)

## Section A — AST completion ✅
- [x] A.1 Common AST types
- [x] A.2 Per-kind property and definition types (17 kinds)
- [x] A.3 Walker for value forms
- [x] A.4 Walker for common properties
- [x] A.5 Per-kind walker functions: db kinds
- [x] A.6 Per-kind walker functions: er kinds
- [x] A.7 Per-kind walker functions: map kinds
- [x] A.8 Per-kind walker functions: query and cnc kinds
- [x] A.9 Inline def lists
- [x] A.10 dataType and reference detection
- [x] A.11 Tests + parseDirectory (sample bundle parses without error)

## Section B — Project model and `modeler.toml` ✅
- [x] B.1 Manifest types and TOML parser (smol-toml; kebab-case `require-descriptions` normalized to camelCase)
- [x] B.2 Project root resolution (`findProjectRoot`, `loadProject` in `@modeler/semantics/node-only`)
- [x] B.3 `modeler/getProjectInfo` LSP method (server-stdio wires `loadManifest` callback so the real `modeler.toml` is loaded on `didOpen`; browser worker uses defaults)
- [x] B.4 Sample manifest (`samples/v1-metadata/modeler.toml`)

## Section C — Symbol table ✅
- [x] C.1 Qname structure (positional `<schema>.<namespace>.<...parts>` — review-004 Task 10 fixed namespace detection)
- [x] C.2 Symbol table per-document (entity/attribute, table/column, view/column, procedure/resultColumn)
- [x] C.3 Project symbol table with duplicate detection
- [x] C.4 Stock vocabulary loaded by `server-stdio` on initialize via `@modeler/semantics/node-only` `loadStockVocabularies`. `src/stock/cnc-roles.ttr` rewritten as valid TTR (`schema cnc namespace role` + `def role …`)
- [x] C.5 Incremental rebuild on document change (upsert/remove + on-close cleanup)
- [x] C.6 Tests: 13 symbol-table tests

## Section D — Reference resolver ✅
- [x] D.1 Resolver API with `ResolutionContext` including optional `enclosingQname`
- [x] D.2 Dotted reference resolution
- [x] D.3 Bare-id resolution: enclosing-def scope → schema/namespace → stock-vocab fallback (`cnc.role.<name>`)
- [x] D.4 Tests: 6 resolver tests (`packages/semantics/src/__tests__/resolver.test.ts`)
- [x] Wired into LSP `onDefinition` / `onHover` / `onReferences` so bare refs inside `entity X` resolve to `X`'s attributes

## Section E — Validator ✅
- [x] E.1 Validator API (`validateDocument`, `validateReferences`, `validateProject`)
- [x] E.2 Required-property checks (entity attributes, table columns, column/attribute type)
- [x] E.3 Cross-reference checks via `validateReferences` — emits `ttr/unresolved-reference` (Warning, Error under `lint.strict`)
- [x] E.4 Duplicate-definition checks via `validateProject` — emits `ttr/duplicate-definition`
- [x] E.6 Tests: 7 validator tests covering RequiredPropertyMissing, EntityAttributeNotFound, PrimaryKeyColumnNotFound, UnresolvedReference (warning + strict-mode error), DuplicateDefinition

§E.5 (Empty-block warnings) — folded into E.2; "Entity must have at least one attribute" and "Table must have at least one column" cover the empty-block case.

## Section F — Diagnostic-code expansion ✅
- [x] `DiagnosticCode` enum: `ParseError`, `UnknownProperty` (reserved for future), `UnresolvedReference`, `DuplicateDefinition`, `RequiredPropertyMissing`, `InvalidType` (reserved), `EntityAttributeNotFound`, `PrimaryKeyColumnNotFound`
- [x] LSP propagates `code` + `source: 'modeler'` on every published Diagnostic
- [x] `docs/design/diagnostics.md` updated with the full taxonomy

## Section G — go-to-definition ✅
- [x] `definitionProvider: true`
- [x] `findNodeAtPosition` walks top-level + nested defs and references, picks the smallest enclosing range
- [x] Cmd-click on a reference follows it via the Resolver; on a def, returns the def's canonical location
- [x] Integration test: `textDocument/definition` on entity name and on bare-id reference both return non-null locations

## Section H — find-references ✅
- [x] H.1 `ReferenceIndex`: incrementally maintained reverse index built from `Resolver` results during `upsertDocument`
- [x] H.2 `onReferences` resolves the cursor → qname → `refIndex.findByQname(qname)`; includes declaration when `context.includeDeclaration`
- [x] Integration test asserts ≥2 locations for the attribute referenced by `nameAttribute`

## Section I — hover ✅
- [x] I.1 Hover content formatter (qname, kind, description, source file:line) as markdown
- [x] I.2 `onHover` follows references via the Resolver
- [x] I.3 Integration test asserts non-null hover containing the qname

## Section J — workspace symbols ✅
- [x] `workspaceSymbolProvider: true`
- [x] fuzzysort installed in `@modeler/lsp` dependencies
- [x] Empty query returns first 100 symbols; non-empty query uses fuzzysort over `qname` + `name`
- [x] Integration test asserts `er.entity.artikl` is matched by query `"art"`

## Section K — Semantic tokens ✅
- [x] `semanticTokensProvider` capability with 9-type / 3-modifier legend
- [x] `textDocument/semanticTokens/full` emits one `class`+`declaration` token per def name (top-level and nested), using the def name's column on its opening line — not the def's full span (review-004 fix)
- [x] Integration test asserts the first token's length equals the entity name's length

## Section L — `parse-recovery-info` emission
- [x] Completed in Phase 3.I (2026-05-16). `RecoveryReportingStrategy` subclass of `DefaultErrorStrategy` overrides `recover` and `recoverInline`; after parsing, its `recoveryEvents` are converted to `ParseError` entries with code `ttr/parse-recovery-info` and severity `info`. Recovery-fixtures tests now assert `ttr/parse-recovery-info` presence on all recoverable inputs.

## Section M — VS Code smoke test
- [x] Completed in Phase 3.J (2026-05-17). `@vscode/test-electron` harness in `packages/vscode-ext/src/test/`, smoke tests TC1–TC5, `test:smoke` script, `vscode-smoke` CI job added.

## Section N — Documentation + progress
- [x] `docs/design/diagnostics.md` covers all Phase-2 codes
- [x] `docs/plan/progress-phase-02.md` reflects on-disk state (this file)
- [ ] `packages/semantics/README.md` — write when Phase 3 starts touching it
- [ ] `packages/lsp/README.md` v2 surface — write when Phase 3 starts touching it

## Test Results (2026-05-16)
```
pnpm -r build:      ✅
pnpm -r test:       ✅
  packages/parser:     24 tests (Phase 3.I: added 5 ttr/parse-recovery-info assertions)
  packages/semantics:  48 tests
  packages/lsp:         4 tests
  packages/vscode-ext:  6 tests (generator)
  tests/integration:   28 tests
pnpm -r lint:       ✅
pnpm -r typecheck:  ✅
```

## Key decisions
- Split `project-node.ts` (Node-only `fs`/`path`) from `project.ts` (browser-safe). `@modeler/semantics/node-only` exposes the Node-only API.
- `createServerConnection(connection, opts?)` accepts `loadManifest` and `loadStock` callbacks. `server-stdio.ts` wires both via `@modeler/semantics/node-only`; `server-browser.ts` leaves them undefined (browser worker has no fs/path).
- `ReferenceIndex` is built incrementally as `upsertDocument` runs, using the resolver's results — so adding/removing a document automatically refreshes which references point at which targets.
- The resolver's `ResolutionContext` carries an optional `enclosingQname`. Bare-id refs attached to entity/table/view/procedure defs resolve their enclosing scope first, then schema-and-namespace, then `cnc.role.<name>` for stock-vocab matches.

## Deferred to later phases
| Item | Target |
|------|--------|
| `parse-recovery-info` emission (DefaultErrorStrategy subclass) | Completed in Phase 3.I (2026-05-16) |
| VS Code `@vscode/test-electron` smoke test | Completed in Phase 3.J (2026-05-17) |
| `packages/semantics/README.md` | Completed in Phase 3.K (2026-05-17) |
| `packages/lsp/README.md` v2 surface doc | Completed in Phase 3.K (2026-05-17) |
| Indexing relations/queries/roles/er2db_* as separate symbol-table entries | Completed in Phase 3.H (2026-05-16) |
