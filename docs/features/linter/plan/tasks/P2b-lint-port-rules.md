# P2b — Port the 26 checks to rules

**Package:** `@modeler/lint` · **Pre-flight:** P2a DONE · **Contracts:** §3.4 + design §5.5 table

Goal: move every check out of `packages/semantics/src/validator.ts` into a `Rule`, preserving code,
message, and default severity exactly. Use the design §5.5 table as the source of truth for
id/code/category/scope/defaultSeverity. **Do not delete `Validator` yet** (that's P2c, after the
golden test proves parity).

Work rule files in the order below; each task is one `rules/*.ts` module + its unit tests written
first. Tick when done; commit as `Section P2b: <task>`.

---

- [x] **1. (test) Port the existing validator tests as rule tests.**
  Copy `packages/semantics/src/__tests__/{validator,duplicate-mapping,diagnostics-v1.1}.test.ts`
  into `packages/lint/src/__tests__/rules/` and rewrite them to call `lintDocument`/`lintProject`
  with `recommended`-equivalent severities, asserting the same `code`/`message`/`source`. These are
  the regression net for tasks 2–7; they fail until each rule lands.

- [x] **2. `rules/structure.ts`.**
  Port from `validator.validateDocument` (lines ~44–131): `entity-no-attributes`, `table-no-columns`,
  `column-missing-type`, `attribute-missing-type`, `missing-description` (default `off`), plus the
  entity `entity-attribute-not-found` (name/code attr) and `primary-key-column-not-found`. Reuse the
  exact messages. `missing-description` reads no manifest flag now — severity comes from config.

- [x] **3. `rules/search.ts`.**
  Port the search-block checks from `validateDocument` (lines ~133–150) + the `searchBlocksOf`
  helper: `fuzzy-without-searchable` (warning), `duplicate-search-property` (error). Move
  `searchBlocksOf` into this module or a `lint/src/internal/` helper.

- [x] **4. `rules/references.ts`.**
  Port `validateReferences`: `unresolved-reference` (default warning — the `strict` override is now
  config, P3), `ambiguous-reference` (error), `unimported-reference` (info). Use `ctx.refs` (already
  computed by the runner) instead of recomputing `collectAllReferences`.

- [x] **5. `rules/imports.ts`.**
  Port `validateImports`: `unused-import`, `duplicate-import`, `wildcard-with-no-matches` (all
  warning). Reuse `packageOfImport`. Use `ctx.refs` for the used-target computation.

- [x] **6. `rules/packages.ts` + `rules/graph.ts`.**
  packages: `circular-package-dependency` (project scope, from `validateCircularDependencies`),
  `package-declaration-mismatch` (error), `missing-package-declaration` (info). graph:
  `graph-missing-schema`, `graph-object-not-found`, `graph-layout-stale-node`, `graph-objects-empty`,
  `graph-name-mismatch`, and the `file-ordering` placeholder rule (registered; its fix defers to the
  formatter — no edit synthesized here).

- [x] **7. `rules/project.ts`.**
  Port `validateProject` + `validateDuplicateMappings`: `duplicate-definition`, `duplicate-mapping`
  (both project scope, error). Preserve the er2db inline-mapping skip logic exactly.

- [x] **8. Register all + gates.**
  Wire every rule into `RULES` (registry). Confirm the registry invariant test passes with all 26
  ids. Confirm the ported tests (task 1) are green. `pnpm -r {build,test,typecheck,lint}` green. No
  `any`. (Golden parity vs `Validator` and deletion happen in P2c.)
