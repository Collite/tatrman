# P1a — Extract formatter into `@modeler/format`

**Package:** new `@modeler/format` · **Pre-flight:** P0 DONE; existing formatter tests green in
`lsp` · **Contracts:** §2, §9

Goal: move the working formatter out of `@modeler/lsp` into its own parser-only package **with no
behaviour change**. Comment-awareness comes in P1b — this list is a pure, test-protected move.

Tick each box when done; commit as `Section P1a: <task>`.

---

- [ ] **1. Scaffold the package.**
  Create `packages/format/` with `package.json` (name `@modeler/format`, `"type":"module"`,
  `workspace:*` dep on `@modeler/parser` only), `tsconfig.json` extending `../../tsconfig.base.json`,
  and a Vitest setup mirroring `packages/parser`. Confirm `pnpm install` links it and
  `pnpm --filter @modeler/format build` runs (empty `src/index.ts` for now).

- [ ] **2. (test) Move formatter tests first.**
  Move `packages/lsp/src/__tests__/formatter.test.ts` and `formatter-samples.test.ts` to
  `packages/format/src/__tests__/`, updating imports to `../index.js` / `../printer.js`. They will
  fail until task 3 — that's the TDD gate for a faithful move.

- [ ] **3. Move the formatter sources.**
  Move `packages/lsp/src/formatter/format.ts` → `packages/format/src/printer.ts`, and `ir.ts`,
  `render.ts` → `packages/format/src/`. Keep all logic identical. Update relative imports. The only
  external import should be `@modeler/parser`.

- [ ] **4. Public API surface.**
  Create `packages/format/src/index.ts` exporting `formatDocument`, `DEFAULT_FORMAT_CONFIG`,
  `FormatConfig` (existing signatures), plus a new thin `format(source, uri, config?)` that
  `parseString`s then calls `formatDocument` (contracts §2.1). Make the moved tests pass unchanged.

- [ ] **5. Re-point the LSP at the package.**
  In `packages/lsp/package.json` add `"@modeler/format": "workspace:*"`. In
  `packages/lsp/src/server.ts` change the import at line 59 to `from '@modeler/format'`. Delete
  `packages/lsp/src/formatter/`. The `refactorExtractDefToNewFile` call (`server.ts:1001`) and
  `onDocumentFormatting` (`:943`) keep working via the package.

- [ ] **6. Gates.**
  `pnpm -r {build,test,typecheck,lint}` green. Verify the LSP formatter integration still works:
  add/keep a test under `tests/integration/` that opens a doc and calls `textDocument/formatting`,
  asserting the formatted result equals `format(src, uri)`. No `any`.
