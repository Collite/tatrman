# Phase 2.2 — Tests first: Kotest specs + fixtures

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.

**Pre-flight:**
- Phase 2.1 DoD met.
- Read [`../../contracts.md`](../../contracts.md) §4 (ttr-semantics API).

**Reference (port test scenarios from):**
- modeler TS tests:
  - `packages/semantics/src/__tests__/qname.test.ts`
  - `packages/semantics/src/__tests__/symbol-table.test.ts`
  - `packages/semantics/src/__tests__/resolver.test.ts`
  - `packages/semantics/src/__tests__/resolver-v1.1.test.ts`
  - `packages/semantics/src/__tests__/package-graph.test.ts`
  - `packages/semantics/src/__tests__/package-inference.test.ts`
  - `packages/semantics/src/__tests__/validator.test.ts`
  - `packages/semantics/src/__tests__/diagnostics-v1.1.test.ts`
- ai-platform tests for parity:
  - `infra/metadata/src/test/kotlin/infra/metadata/resolve/ReferenceResolverSpec.kt`
  - `infra/metadata/src/test/kotlin/infra/metadata/resolve/ResolutionIntegrationSpec.kt`

**Tasks (TDD — every spec compiles, all fail at runtime):**

- [ ] **2.2.1 — Create `QnameSpec.kt`** in
      `packages/kotlin/ttr-semantics/src/test/kotlin/org/tatrman/ttr/semantics/`.
      Cases: segments, last segment, parent qname (returns null at root),
      equality semantics, `Qname("a.b.c.")` rejection.

- [ ] **2.2.2 — Create `PackageInferenceSpec.kt`.** Cases: `<root>/foo/bar/baz.ttr`
      → `Qname("foo.bar")`; `<root>/baz.ttr` → empty Qname; file outside root
      → throws or sentinel result (decide; document in `contracts.md`).

- [ ] **2.2.3 — Create `SymbolTableSpec.kt`.** Cases: add+lookup, conflict
      detection (same qname twice → exception or sentinel — match TS
      behaviour), `findByLastSegment` returns multiple, `findUnderPackage`
      returns siblings only (not nested children).

- [ ] **2.2.4 — Create `PackageGraphSpec.kt`.** Cases: simple linear graph,
      cycle of two, cycle of three, multiple disjoint cycles.

- [ ] **2.2.5 — Create `ResolverSpec.kt` — the big one.** One test per
      resolution step plus negative cases. Mirror cases from TS
      `resolver-v1.1.test.ts`:
      - Step 1 (lexical): bare name with local symbol in scope.
      - Step 2 (same-package): bare name referencing sibling — no import
        needed.
      - Step 3 (named import): `import a.b.c` makes `c` resolve to `a.b.c`.
        Bare-defName imports (`import c` where `c` is not a qname) → error
        `ttr/unimported-reference`.
      - Step 4 (wildcard import): `import x.y.*` exposes `x.y.z` but **NOT**
        `x.y.z.w` — the non-recursion test.
      - Step 5 (auto-import): bare `fact` resolves to `cnc.role.fact` without
        explicit import.
      - Step 6 (FQN): `db.dbo.QSUBJEKT.IDSUBJEKT` always resolves if the symbol
        exists.
      - Ambiguity: two wildcard imports both exposing a name → `Ambiguous`
        with both candidates.
      - Unresolved: bare name with no matches → `Unresolved(UnimportedReference, ...)`.

- [ ] **2.2.6 — Create `ValidatorSpec.kt`.** Per-kind cases:
      - Cardinality string validation (e.g. `1:N`, `M:N`).
      - Target shape validation (`{ table: X }`, `{ column: Y }`).
      - Search-block sub-property checks (`ttr/fuzzy-without-searchable`).
      - Drill-map arg validation (every arg name must match a parameter of the
        `to` query def).

- [ ] **2.2.7 — Create `StockLoaderSpec.kt`.** Asserts:
      - `StockLoader.load()` returns at least one `RoleDef` per known stock
        role (`fact`, `dimension`, `structural`, `master`, `transaction`,
        `bridge`).
      - `StockLoader.stockQnames()` contains the six qnames `cnc.role.fact`
        … `cnc.role.bridge`.

- [ ] **2.2.8 — Run the tests; confirm they fail with compile errors only.**
      `./gradlew :packages:kotlin:ttr-semantics:test`. Every error should
      point at a missing type from `contracts.md` §4.

**Stage DoD:**
- Eight tasks checked.
- All specs fail to compile (production code doesn't exist yet).
- No accidental green tests — every spec must depend on a Phase 2 type.
