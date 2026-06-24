# Phase 2.5 — Validator + StockLoader

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.

**Pre-flight:**
- Phase 2.4 DoD met. `Resolver` is green.
- Read [`../../contracts.md`](../../contracts.md) §4.6, §4.7.

**Reference (port from):**
- `packages/semantics/src/validator.ts`
- `packages/semantics/src/stock-loader.ts`
- `packages/semantics/src/stock/*.ttrm` — the canonical stock vocab content.
- ai-platform `infra/metadata/src/main/resources/builtin/cnc-stock-roles.ttrm` —
  ai-platform's current copy. Compare side-by-side with modeler's TS-side
  stock to confirm they describe the same six roles; reconcile if not.

**Tasks:**

- [ ] **2.5.1 — Copy stock vocab `.ttrm` files** from
      `packages/semantics/src/stock/` (or wherever the canonical content
      lives) to
      `packages/kotlin/ttr-semantics/src/main/resources/builtin/cnc-stock-roles.ttrm`.
      Consolidate into one file if the TS side splits them — Kotlin loads from
      one resource per `contracts.md` §4.7.

- [ ] **2.5.2 — Implement `StockLoader.kt`** per §4.7. Use
      `Thread.currentThread().contextClassLoader.getResource(resourcePath)`
      with a `StockLoader::class.java.classLoader.getResource(...)` fallback
      (matches ai-platform's `BuiltinStockSource` defensiveness against
      classloader weirdness). Parse via `TtrLoader.parseString(...)`. Verify
      `StockLoaderSpec` turns green.

- [ ] **2.5.3 — Implement `Validator.kt` skeleton** per §4.6 — empty per-kind
      methods returning `emptyList<ValidationDiagnostic>()`. Wires the class
      structure.

- [ ] **2.5.4 — Implement per-kind validators**, one at a time, each
      flipping a `ValidatorSpec.kt` case green:
      - Cardinality validation for `RelationDef.cardinality`.
      - Target shape validation for `Er2DbEntityDef.target` (must contain
        `table`), `Er2DbAttributeDef.target` (must contain `column`).
      - Search-block sub-property check:
        `fuzzy: true && !searchable` → `ttr/fuzzy-without-searchable` (Warning).
      - Drill-map arg validation: every `DrillMapDef.args` key must correspond
        to a parameter declared on the resolved `to: QueryDef`.

- [ ] **2.5.5 — Wire `Validator` to use `Resolver`** for cross-reference
      validation (e.g. drill-map arg validation needs to resolve the `to`
      reference first). Pass the `Resolver` into `Validator.validate(...)`
      via a parameter — keep classes immutable.

- [ ] **2.5.6 — Add a stock+resolver integration test.** Create
      `StockAutoImportIntegrationSpec.kt` parsing a fixture like
      `def entity X { roles: [fact, dimension] }`, building the symbol table
      with stock loaded, and asserting both `fact` and `dimension` resolve via
      step 5 (auto-import).

- [ ] **2.5.7 — Run full module tests + ktlint.**
      `./gradlew :packages:kotlin:ttr-semantics:test ktlintCheck`.

**Stage DoD:**
- Seven tasks checked.
- All Phase 2.2 specs green (`Qname`, `PackageInference`, `SymbolTable`,
  `PackageGraph`, `Resolver`, `Validator`, `StockLoader`,
  `StockAutoImportIntegrationSpec`).
- ktlint green.
- `./gradlew build` green (all three Kotlin modules).
