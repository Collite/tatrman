# Feature: `search` block rework + fuzzy search hint

Editor-tooling change to the TTR language surface. Small, self-contained. No
architecture or contract docs — this README plus the five task lists below are
the whole plan.

## Goal

1. **Remove** the top-level `searchable` boolean property (currently allowed on
   db columns and er attributes).
2. **Widen** the `search { … }` block so it is allowed on **all data-bearing
   definition kinds**: `table`, `column`, `view`, `entity`, `attribute`,
   `relation`, `query`, `role`. (Today it is only on `entity`, `attribute`,
   `query`, `role` — so we are *adding* `table`, `column`, `view`, `relation`.)
3. **Add two sub-properties** to the `search` block:
   - `searchable` — boolean — the old top-level flag, relocated inside the block.
   - `fuzzy` — boolean — whether the element is exposed to fuzzy search.

### New surface (illustration)

```ttr
def table QZBOZI_DF {
  columns: [
    def column NAZEV_ZBOZI {
      type: varchar(100),
      search { searchable: true, fuzzy: true, patterns: ["název .*"] }
    }
  ]
}
```

## Decisions (from the planning session)

| Decision | Choice |
|---|---|
| Where the `search` block is allowed | All data-bearing kinds (table, column, view, entity, attribute, relation, query, role) |
| Validation in `@modeler/semantics` | (a) **warn** when `fuzzy: true` but `searchable` is not `true`; (b) **error** on a duplicate sub-property inside one `search` block |
| Boolean representation | Tri-state optional (`searchable?: boolean`, `fuzzy?: boolean`; `undefined` when omitted) — matches the existing `searchable?` field |
| Migration hint for old top-level `searchable` | **Not added** — old usage simply becomes a syntax error |

### Breaking change

Removing the top-level `searchable` means `def column X { searchable: true }`
and `def attribute Y { searchable: true }` become **parse errors**. This is
acceptable because:

- No `.ttr` sample or test fixture in this repo currently uses top-level
  `searchable` (verified via grep — only `samples/yaml/**` carry it, and those
  are YAML inputs, not TTR; they are handled by T5).
- The only `.ttr` file using a `search` block today is
  `samples/v1-metadata/query.ttr`, which uses `patterns` only — unaffected.

### Duplicate-detection approach

The walker collapses repeated sub-properties (last-wins), so the AST alone can't
tell that `keywords` appeared twice. To keep `@modeler/parser` mechanical while
keeping policy in `@modeler/semantics`, the walker records the **names of
sub-properties that occurred more than once** on the `SearchBlock` node
(`duplicateProperties?: string[]`); the validator turns that into an error.
This is bookkeeping (the parser literally sees two `keywords` children), not
resolution logic. (Alternative considered: have the validator re-walk the CST —
rejected as heavier than the feature warrants.)

## Task lists

Execute in order. Each is a self-contained mini-list of ≤8 checkboxed steps;
tick every box as you go. Tests are written **before** implementation within
each list (TDD).

1. [T1 — Grammar + regeneration](./T1-grammar.md)
2. [T2 — Parser AST + walker (+ parser tests)](./T2-parser-ast-walker.md)
3. [T3 — Semantics validation (+ validator tests)](./T3-semantics-validation.md)
4. [T4 — Samples + docs](./T4-samples-and-docs.md)
5. [T5 — ai-platform: YAML loader + YAML→TTR converter](./T5-ai-platform-yaml.md)

> T5 lives in the separate `ai-platform` repo (not mounted in this workspace),
> so its steps are written against the **described** behaviour and the
> conventions in `CLAUDE.md`. Verify each path/symbol against the actual code
> before editing.

## Definition of DONE (whole feature)

- [ ] `pnpm -r build` is green (includes antlr + esbuild bundles).
- [ ] `pnpm -r typecheck` is green.
- [ ] `pnpm -r test` is green, including the **new** parser and semantics tests.
- [ ] `pnpm -r lint` is green (no new `any`).
- [ ] Generated files committed alongside the grammar change
      (`packages/parser/src/generated/*`, the VS Code TextMate grammar).
- [ ] The grammar change is synced to `ai-platform` and its Kotlin parser
      regenerated; the YAML loader/converter emit the new `search { searchable }`
      shape (T5).
- [ ] Manual smoke: open a `.ttr` in the Extension Dev Host (F5 in
      `packages/vscode-ext`), confirm `search`/`searchable`/`fuzzy` highlight and
      that the two new diagnostics appear.

## Commit style

Follow the repo convention: `Section <X>: <description>` per logical step
(e.g. `Search block: move searchable into block, add fuzzy (grammar)`). Don't
squash unrelated changes; keep generated-file regeneration in the same commit as
the grammar edit it derives from.
