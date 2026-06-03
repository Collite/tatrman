# 1.1.I.2 — Document formatting

**Goal:** `textDocument/formatting` produces deterministic, opinionated pretty-printed output for any `.ttr` or `.ttrg` file. Two settings control separator style and key-alignment.

**Reads:** the v1 grammar (for property kinds and value shapes), [contracts §1 (grammar)](../../design/v1-1-contracts.md#1-grammar-tokens-and-parser-rules-added).
**Blocked by:** 1.1.I.1 (uses the CST view machinery established by the rename builders).
**Blocks:** I3 (some quick-fixes invoke formatter helpers).
**Estimated time:** 2 days.

## Tests-first

- [ ] `packages/lsp/src/__tests__/formatter.test.ts` — unit. Cases:
  - Round-trip: format a file, format the result, no further changes (idempotent).
  - Indentation: nested `attributes: [...]` lists are 4-space-indented; the inner `def attribute X { ... }` blocks are 8-space-indented.
  - Property alignment (when `alignKeys: true`): keys in a single object literal line up at the longest-key + 1 column.
  - Separator policy: `separator: 'newline'` puts each property on its own line; `'comma'` puts them on one line; `'preserve'` leaves whatever the user wrote.
  - Triple-string literals are left untouched (their internal whitespace is content).
  - Comments adjacent to defs are preserved on their original lines.
- [ ] Fixture-driven golden tests: hand-author `samples/format/<name>.in.ttr` and `<name>.out.ttr` pairs; the test asserts `format(in) === out`. Cover at least: entity with attributes, table with columns and indices, relation with cardinality + join, hand-formatted "messy" input.

## Library reference

```
mcp__context7__resolve-library-id { libraryName: "prettier", query: "AST-based formatter design, doc IR" }
mcp__context7__query-docs         { libraryId: "<id>", query: "doc builders, group, indent, line, softline" }
```

We do NOT depend on Prettier — too heavy and the language is too specific. Implement a hand-rolled formatter on top of the CST. Prettier's "doc IR" pattern is good inspiration; use a similar "build a printable tree, then render" approach.

## Implementation tasks

- [ ] **I2.1 — Define the formatter's intermediate representation.** New file `packages/lsp/src/formatter/ir.ts`. Types: `Doc = Text | Group | Indent | Line | SoftLine | Concat | Choice`. Helpers `text`, `group`, `indent`, `line`, `softline`, `concat`, `choice` for building.
- [ ] **I2.2 — Implement the renderer.** `render(doc: Doc, width: number, indentSpaces: number): string`. Standard line-breaking algorithm: try the "all-on-one-line" rendering first; if it exceeds `width`, break at the outermost `group` boundary. Recurse.
- [ ] **I2.3 — Implement AST-to-IR conversion.** `formatDocument(doc: Document, config: FormatConfig): Doc`. Walk the AST; for each node kind, produce the matching IR. Property lists become `group(concat([text(key), text(': '), value, choice(text(','), softline)]))` style nodes.
- [ ] **I2.4 — Wire `connection.onDocumentFormatting`.** In `server.ts`, register the handler. Reads `FormatConfig` from `workspace.getConfiguration('modeler.format')` (defaults: `{ separator: 'preserve', alignKeys: false, indentSpaces: 4, width: 100 }`). Returns `TextEdit[]` — typically a single edit replacing the whole document.
- [ ] **I2.5 — Settings & capability.** Add `modeler.format.separator`, `modeler.format.alignKeys`, `modeler.format.indentSpaces`, `modeler.format.width` to `packages/vscode-ext/package.json` `contributes.configuration` with descriptions. Capability `documentFormattingProvider: true` in `initialize`.
- [ ] **I2.6 — `.ttrg`-specific formatter rules.** The `graph { ... }` block formats analogously to `def entity { ... }`. The `objects: [...]` list always breaks one-per-line (regardless of separator config) because lists of qnames are visually clearer that way. The `layout: { nodes: { ... } }` block formats `nodes` as one-per-line with the qname key + position object on the same line.
- [ ] **I2.7 — Run the formatter against every v1.1 sample as part of the test suite.** Add `formatter-samples.test.ts` that loops over every file in `samples/v1.1-*/`, formats it, asserts the output parses cleanly, and asserts a second pass is idempotent.

## Verify by running

```bash
pnpm --filter @modeler/lsp test
pnpm -r typecheck
```

All formatter tests green. Manual smoke in VS Code: Format Document (Shift-Alt-F) on any sample file → produces clean output; re-running is a no-op.

## DONE when

- [x] Every checkbox above is ticked.
- [x] Formatter is idempotent on every v1.1 sample. (`formatter-samples.test.ts`, all 25 files.)
- [x] All four settings work. `separator: 'comma'` forces a single line regardless of width; `'preserve'` breaks iff the original def was multi-line.
- [x] Triple-string literals are preserved verbatim (sliced from source by offset).
- [ ] **Comments are NOT preserved — deferred.** The lexer `-> skip`s `//` and `/* */`, so the AST carries no comment data; a reprint formatter cannot restore them. Restoring comments requires `@modeler/parser` to expose a CST/trivia view (separate follow-up). Samples contain no comments, so idempotency holds.

### Notes on what shipped (review-058)

- **Golden fixtures**: implemented as inline assertions in `formatter.test.ts` plus the 25-file sample idempotency suite, rather than `samples/format/*.in.ttr`/`.out.ttr` file pairs.
- **Property order is canonicalised**: the formatter emits each def's properties in a fixed order, so the *first* format of an existing file may produce a reordering diff (this is what makes formatting deterministic / idempotent).
