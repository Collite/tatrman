# Phase 2.3 — Qname + SymbolTable + PackageInference + PackageGraph

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.

**Pre-flight:**
- Phase 2.2 DoD met — specs from `QnameSpec`, `PackageInferenceSpec`,
  `SymbolTableSpec`, `PackageGraphSpec` are red (compile errors).
- Read [`../../contracts.md`](../../contracts.md) §4.1, §4.2, §4.4, §4.5.

**Reference (port from):**
- `packages/semantics/src/qname.ts`
- `packages/semantics/src/symbol-table.ts`
- `packages/semantics/src/package-inference.ts`
- `packages/semantics/src/package-graph.ts`
- ai-platform `infra/metadata/src/main/kotlin/infra/metadata/resolve/SymbolTable.kt`
  for Kotlin-idiomatic reference.

**Tasks:**

- [ ] **2.3.1 — Implement `Qname.kt`** per `contracts.md` §4.1. Value class
      wrapping `String`. Verify `QnameSpec` turns green.

- [ ] **2.3.2 — Implement `PackageInference.kt`** per §4.4. Single function
      `inferPackage(filePath, projectRoot)` walking the relative path.
      Verify `PackageInferenceSpec` turns green.

- [ ] **2.3.3 — Implement `SymbolTable.kt`** per §4.2. Internal storage =
      `MutableMap<Qname, SymbolEntry>` plus auxiliary indexes for
      `findByLastSegment` and `findUnderPackage`. Decision on conflict
      semantics (throw vs sentinel) — mirror TS `symbol-table.ts`. Verify
      `SymbolTableSpec` turns green.

- [ ] **2.3.4 — Implement `PackageGraph.kt`** per §4.5. Tarjan or Kosaraju for
      `detectCycles`; small graphs in practice so either works. Verify
      `PackageGraphSpec` turns green.

- [ ] **2.3.5 — Add cross-module integration test.** Create
      `SemanticsIntegrationSpec.kt` that parses a small fixture, builds the
      `SymbolTable`, infers packages, builds the `PackageGraph`, and asserts
      end-to-end shape. Establishes the wiring pattern Phase 2.4 will extend.

- [ ] **2.3.6 — Run module tests + ktlint.**
      ```bash
      ./gradlew :packages:kotlin:ttr-semantics:test
      ./gradlew :packages:kotlin:ttr-semantics:ktlintCheck
      ```

**Stage DoD:**
- Six tasks checked.
- `QnameSpec`, `PackageInferenceSpec`, `SymbolTableSpec`, `PackageGraphSpec`,
  `SemanticsIntegrationSpec` all green.
- Other Phase 2.2 specs (Resolver, Validator, StockLoader) still red — that's
  expected; subsequent stages own them.
