# 02 — AST node renames (modeler / TS + Kotlin)

Goal: the AST reflects the rename, no semantics change yet. See
[`../plan.md`](../plan.md) Phase 2.

**Pre-flight:** Phase 01 merged; baseline green. Read
`docs/grammar-master/AST-NAMING.md` (TS↔Kotlin kind/type map).

## Tasks

- [ ] **2.1 Tests first (snapshots).** Update `tests/conformance/dump.test.ts` (line 12
  currently `def model M`) to `def project M`; add a `model db schema dbo` directive
  snapshot. Make them fail.
- [ ] **2.2 TS AST.** `packages/parser/src/ast.ts`: `SchemaDirective` →
  `ModelDirective` (`schemaCode`→`modelCode`; add `schema?: string`);
  `ModelDef`/`ModelProperty` → `ProjectDef`/`ProjectProperty`. Update `walker.ts`
  construction sites.
- [ ] **2.3 Kotlin AST.** Mirror the rename in the Kotlin parser model classes; keep
  field order for conformance dumps.
- [ ] **2.4 Update AST-NAMING.md.** Record the new names in the TS↔Kotlin table.
- [ ] **2.5 Fix downstream type refs.** `rg -a 'SchemaDirective|ModelDef|schemaCode'`
  across `packages/*/src` (note the `server.ts` NUL byte — use `rg -a`); rename usages.
  Pure type rename; no logic.

## Done when

- [ ] Parser + conformance snapshots green in TS and Kotlin.
- [ ] `pnpm -r typecheck` green; no `SchemaDirective`/`ModelDef` symbols remain
  outside `generated/**`.

**Verify:** `pnpm -r typecheck` · `pnpm --filter @modeler/parser test` ·
`./gradlew :packages:kotlin:ttr-parser:test`
