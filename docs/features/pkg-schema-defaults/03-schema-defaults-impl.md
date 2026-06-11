# Stage 3 — Item 2: implement `defaultSchemaForKind` + wire it in

**Goal:** make Stage 2's tests pass in both languages with the **minimal** change
that mirrors the existing namespace fallback. Do not touch the grammar.

**Design (normative):** introduce one pure helper `defaultSchemaForKind(kind)`
returning the schema code from the [`INDEX.md`](INDEX.md) map. Replace the
hardcoded file-level `?? 'db'` default with a **per-definition** derivation that
fires only when the file has no `schema` directive. An explicit directive
continues to win for the whole file.

> The key boundary is qname construction (symbol-table population) and reference
> resolution. Both must use the same default, exactly as both already use the
> same `namespace || def.kind` fallback.

---

- [ ] **3.1 (TS) — Add the helper.** Create
  `packages/semantics/src/default-schema.ts` exporting
  `export function defaultSchemaForKind(kind: string): 'db' | 'er' | 'map' | 'cnc' | 'query'`
  implementing the map. Throw or fall back to `'db'` for an unknown kind — match
  whatever the Kotlin side does (keep them identical). Export it from the
  package index if the package re-exports its public surface.

- [ ] **3.2 (TS) — Use a single "effective schema" rule.** Where a file-level
  schema default is needed for **qname construction and reference resolution**,
  compute: explicit `ast.schemaDirective?.schemaCode` if present, else **per
  definition** `defaultSchemaForKind(def.kind)`. Concretely:
  - `packages/semantics/src/reference-index.ts` — `enclosingQnameOf` already
    receives `schemaCode`; change its callers so that when the directive is
    absent it passes the kind-derived schema (or pass `undefined` and let
    `enclosingQnameOf` apply `defaultSchemaForKind(def.kind)`, symmetric to its
    existing `namespace || def.kind`).
  - The symbol-table population path that today supplies the file schema with
    `?? 'db'`. Find it: `grep -rn "upsertDocument\|DocumentSymbolTable" packages/semantics/src`
    and `grep -rn "?? 'db'" packages/semantics/src`. Update so each symbol entry
    is keyed by the effective per-kind schema when no directive is present.

- [ ] **3.3 (TS) — Update the validator's resolution contexts.** In
  `packages/semantics/src/validator.ts`, the two `?? 'db'` sites at **:158**
  (`validateReferences`) and **:302** (`validateImports`) feed the resolver's
  context. Make the effective schema consistent with 3.2 so references in a
  schema-less file resolve against the same qnames the symbol table produced.
  (If you route everything through `enclosingQnameOf`/the symbol table, these
  may reduce to passing the directive value or `undefined`.)

- [ ] **3.4 (TS) — Leave LSP presentation defaults alone (note only).** The
  `?? 'db'`/`?? 'er'`/`?? 'cnc'` literals in `packages/lsp/src/**` (e.g.
  `model-graph.ts`, `server.ts`, `server-stdio.ts`) are display/graph defaults,
  **out of scope** for this correctness fix. Add a one-line `// TODO` referencing
  this feature where you see them, but do not change behavior. Confirm no
  semantics test depends on them.

- [ ] **3.5 (TS) — Green.** `pnpm --filter @modeler/semantics test`,
  `pnpm --filter @modeler/semantics typecheck`, `pnpm -r lint` (no new `any`).
  All Stage 2 TS tests pass; existing suites stay green.

- [ ] **3.6 (Kotlin) — Add the helper next to `Kinds.kt`.** In
  `packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/`,
  add `internal fun defaultSchemaForKind(kind: String): String` with the same
  map and same unknown-kind behavior as TS. Reuse the `kindOf(def)` string
  output as the input domain.

- [ ] **3.7 (Kotlin) — Wire it into qname + resolution.** Mirror 3.2/3.3 in
  `Qname.kt` / `SymbolTable.kt` / `Resolver.kt` / `Validator.kt`: replace the
  constant `db` file-default with the per-kind default when no directive is
  present; explicit directive still wins. Then
  `./gradlew :packages:kotlin:ttr-semantics:test` green.

### Stage 3 DoD
- [ ] `defaultSchemaForKind` exists in TS and Kotlin with identical maps and
      identical unknown-kind handling.
- [ ] Schema-less files resolve per the map in both languages; explicit-directive
      regression tests still pass.
- [ ] TS + Kotlin semantics suites green; lint/typecheck clean; LSP presentation
      defaults unchanged (only TODO comments added).
