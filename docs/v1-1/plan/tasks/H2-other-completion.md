# 1.1.H.2 — Property name / schema kind / def kind / package name completion

**Goal:** the four "non-reference" completion contexts return useful suggestions: property names inside a `def` body, schema kinds after `schema`, def kinds after `def`, package names inside `package` and `import` statements.

**Reads:** `packages/grammar/src/TTR.g4` (per-kind property maps), `packages/lsp/src/completion-reference.ts` (from H1).
**Blocked by:** 1.1.H.1.
**Blocks:** H3.
**Estimated time:** 1.5 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/completion-property.test.ts` — unit. Cases:
  - Inside `def entity X { <CURSOR> }`: returns `description`, `tags`, `labelPlural`, `nameAttribute`, `codeAttribute`, `aliases`, `attributes`, `roles`, `displayLabel`, `search`. The `search` block itself offers: `keywords`, `patterns`, `descriptions`, `examples`, `aliases`, `searchable`, `fuzzy`. (Source: the grammar's `entityProperty` rule.)
  - Inside `def column X { <CURSOR> }`: returns `description`, `tags`, `type`, `optional`, `isKey`, `indexed`, `search`.
  - Inside `def table X { <CURSOR> }`: returns table properties.
  - The candidate list excludes properties already present in the current def body (no duplicates).
- [ ] `packages/lsp/src/__tests__/completion-schema-def-kind.test.ts` — unit. Cases:
  - After `schema <CURSOR>`: returns `db`, `er`, `map`, `query`, `cnc`.
  - After `def <CURSOR>`: returns the 17 def-kind keywords.
- [ ] `packages/lsp/src/__tests__/completion-package-name.test.ts` — unit. Cases:
  - Inside `package <CURSOR>` (new file): returns the inferred package name (from path) as the top suggestion; lists all distinct project packages as alternatives.
  - Inside `import <CURSOR>`: returns all distinct project packages with their child symbol counts in `detail`.
  - Inside `import com.<CURSOR>`: filters to packages starting with `com.`.

## Library reference

Same `vscode-languageserver` completion API as H1. The per-kind property maps live in the grammar; you don't need to hand-build them — extract from the `*Property` rules at build time and emit a typed map.

## Implementation tasks

- [ ] **H2.1 — Build the per-kind property map.** Add `packages/grammar/scripts/extract-property-map.ts` that parses `TTR.g4`, walks each `<kind>Property` rule, and emits `packages/grammar/src/generated/property-map.ts` exporting `Record<DefinitionKind, string[]>`. Run as part of `pnpm --filter @modeler/grammar prebuild`.
- [ ] **H2.2 — Implement `getPropertyNameCompletions(position, document, kind)`.** New file `packages/lsp/src/completion-property.ts`. Determines the enclosing def kind from the parse tree; returns the property names from the map, minus those already present in the def body. `CompletionItem.kind = Property`; `detail` is the property's value type (e.g. "string", "list of ids", "localized string").
- [ ] **H2.3 — Implement `getSchemaCodeCompletions(position, document)`.** Returns the five `schemaCode` keywords as `CompletionItem.kind = Keyword`. Triggered only after a `schema` keyword.
- [ ] **H2.4 — Implement `getDefKindCompletions(position, document)`.** Returns the 17 def-kind keywords (`entity`, `table`, `column`, etc.). Filters by the file's `schema` kind when known (e.g. inside a `.ttr` with `schema er`, suggest `entity`, `attribute`, `relation`; suppress `table`, `column`).
- [ ] **H2.5 — Implement `getPackageNameCompletions(position, document, projectSymbols, documentUri, projectRoot)`.** For `package` keyword: the inferred-from-path name first, then other distinct project packages. For `import`: every distinct package; if there's a partial prefix at the cursor, filter to matches.
- [ ] **H2.6 — Dispatch in `connection.onCompletion`.** Update the handler from H1 to route to the right helper based on cursor context. The order matters: more-specific position checks first (reference position, then property-name position, then schema/def-kind, then package-name); fall through if none match.

## Verify by running

```bash
pnpm --filter @modeler/grammar prebuild
pnpm --filter @modeler/lsp test
pnpm --filter @modeler/integration-tests test
pnpm -r typecheck
```

Each unit test file green; manual smoke in VS Code: typing `def entity X { ` shows the entity property list.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `property-map.ts` is generated from the grammar; CI fails if it drifts from `TTR.g4`.
- [ ] All four non-reference completion contexts work in VS Code with the right candidate sets.
- [ ] No symbols / settings work yet — that's H3.
