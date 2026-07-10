# 1.1.B.1 — AST extension

**Goal:** add the three new AST node types (`PackageDecl`, `ImportDecl`, `GraphBlock`) plus the `GraphLayout` shape, extend the `Document` interface, and wire the AST walker to populate them from the parse tree.

**Reads:** [contracts §2 (AST additions)](../../design/v1-1-contracts.md#2-ast-additions), `packages/parser/src/ast.ts`, `packages/parser/src/walker.ts`.
**Blocked by:** 1.1.A.
**Blocks:** B2, B3, C1.
**Estimated time:** 1.5–2 days.

## Tests-first

- [ ] `packages/parser/src/__tests__/ast-v1.1.test.ts` — new file. Cases:
  - `package billing.invoicing` → `ast.packageDecl` is `{ kind: 'packageDecl', name: 'billing.invoicing', parts: ['billing', 'invoicing'], source: { ... line 1 ... } }`.
  - No `package` line → `ast.packageDecl === undefined` and `ast.imports === []`.
  - `import x.y.*` → `ast.imports[0]` is `{ kind: 'importDecl', target: 'x.y', targetParts: ['x','y'], wildcard: true, source: { ... } }`.
  - `import p.q.r.S` → `ast.imports[0]` is `{ kind: 'importDecl', target: 'p.q.r.S', targetParts: ['p','q','r','S'], wildcard: false, source: { ... } }`.
  - `graph view { schema: er, objects: [a.b.er.entity.X] }` → `ast.graph` is populated, `ast.definitions === []`, `ast.schemaDirective === undefined`.
  - Source-location accuracy: for each new node, `source.line` and `source.endLine` reflect the actual token range. Use the same ANTLR-style invariants spelled out in `CLAUDE.md` (`endColumn = stopToken.column + stopTokenLength`, never `startColumn + spanLength`).

## Library reference

This is pure AST work — no external libraries. The pattern to follow is the existing `walker.ts` `enter*` / `exit*` visitor methods. Look at `enterSchemaDirective` and `enterDefinition` for the template.

## Implementation tasks

- [ ] **B1.1 — Add new node interfaces to `ast.ts`.** Insert `PackageDecl`, `ImportDecl`, `GraphBlock`, `GraphLayout` exactly per [contracts §2](../../design/v1-1-contracts.md#2-ast-additions). Place them under the `// Document / parse result` heading, above the existing `Document` interface.
- [ ] **B1.2 — Extend the `Document` interface.** Add `packageDecl?: PackageDecl`, `imports: ImportDecl[]` (non-optional; default `[]`), `graph?: GraphBlock`. Keep all existing fields.
- [ ] **B1.3 — Add walker handlers for the new nodes.** In `packages/parser/src/walker.ts`, add `enterPackageDecl`, `enterImportDecl`, `enterGraphBlock` methods that:
  - Compute the dotted name from the `qualifiedName` child (reuse the existing `qnameFromIdContext` helper if present; else add it).
  - For `importDecl`, detect the trailing `.*` via the presence of `DOT STAR` children and set `wildcard: true`.
  - For `graphBlock`, walk each `graphProperty` and populate `schema`, `description`, `tags`, `objects`, `layout` fields. For `layout`, parse the `object_` shape into a `GraphLayout` instance.
  - Use `makeSourceLocation(ctx)` for `source: SourceLocation` per the v1 invariant.
- [ ] **B1.4 — Initialize `imports: []` in the walker's root.** When the walker builds the `Document`, ensure `imports` is always an array (never `undefined`). Existing v1 docs with no imports get an empty array.
- [ ] **B1.5 — Emit `ttr/wrong-file-kind` when graph + definitions coexist.** If the walker finds both `graph` and at least one `definition` in the same document, push a `ParseError` with `code: 'ttr/wrong-file-kind'`, severity `'error'`, message `"A file containing 'graph { ... }' must not also contain top-level 'def' definitions."`, located at the offending node's range.
- [ ] **B1.6 — Update existing AST-related unit tests.** Search for tests that construct `Document` literals; ensure they set `imports: []` (or accept the new optional fields). Make all existing parser tests green.

## Verify by running

```bash
pnpm --filter @modeler/parser test
pnpm --filter @modeler/parser typecheck
```

The new `ast-v1.1.test.ts` cases pass; existing parser tests pass.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `ast.ts` exports the four new types.
- [ ] `walker.ts` populates them from the parse tree.
- [ ] Every v1 sample parses with `ast.packageDecl === undefined` and `ast.imports === []` (because none of them declare packages or imports yet — migration fixes that in 1.1.G).
- [ ] `ttr/wrong-file-kind` fires on the obvious bad case (a `.ttr` file with a `graph` block).
- [ ] No semantics work yet — symbol-table additions land in B2.
