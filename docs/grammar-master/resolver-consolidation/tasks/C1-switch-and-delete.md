# C.1 — Switch the pass + rework used-imports + delete legacy

**Repo:** ai-platform. **Effort:** ~half day.

**Pre-flight:**
- B.3 done — `ResolverParitySpec` fully green.
- Read [`../contracts.md`](../contracts.md) §2.3, §2.4.

**File:** `infra/metadata/src/main/kotlin/infra/metadata/resolve/ReferenceResolutionPass.kt`
(orchestration kept; only the resolver wiring + used-import tracking change).

---

- [ ] **C.1.1 — Expose `viaStep` from the adapter.** Add a detailed entry point
      so the pass can mark used-imports without the deleted `SymbolTable`:
      ```kotlin
      data class ResolveDetail(val resolution: Resolution, val viaStep: ResolutionStep?)
      fun resolveDetailed(ref: String, ctx: ResolutionContext): ResolveDetail
      ```
      `resolve(ref, ctx)` becomes `resolveDetailed(ref, ctx).resolution`.

- [ ] **C.1.2 — Switch the pass to the adapter** (contracts §2.3): replace
      ```kotlin
      val symbolTable = SymbolTable(definitions = buildDefList())
      val resolver = ReferenceResolver(symbolTable)
      ```
      with `val adapter = PublishedResolverAdapter.build(files)` and each
      `resolver.resolve(ref.path, ctx)` with `adapter.resolveDetailed(ref.path, ctx)`
      (use `.resolution` for the diagnostic, `.viaStep` for used-import tracking).

- [ ] **C.1.3 — Rework `recordUsedImport`** (contracts §2.4). Drive it off
      `viaStep` instead of `symbolTable.matchingWildcard` / `byFull`: when
      `viaStep == NamedImport`, mark the named import whose `target` suffix-matches
      the bare name; when `viaStep == WildcardImport`, mark the wildcard import
      whose package (`getByPackage(imp.target)` via an adapter helper) contains a
      def with that bare name, and bump its `wildcardMatchCount`. Keep the
      emitted `ttr/unused-import` / `ttr/wildcard-with-no-matches` /
      `ttr/duplicate-import` messages byte-identical.

- [ ] **C.1.4 — Delete the legacy resolver.**
      ```bash
      git rm infra/metadata/src/main/kotlin/infra/metadata/resolve/ReferenceResolver.kt \
             infra/metadata/src/main/kotlin/infra/metadata/resolve/SymbolTable.kt \
             infra/metadata/src/test/kotlin/infra/metadata/resolve/ResolverParitySpec.kt
      ```
      (The parity spec's job — proving equivalence — is done.) If `buildDefList`
      / `Def` / `definitionKindSchemaAndNamespace` are now unused, delete them
      too; if still referenced (e.g. by other passes), keep them.

- [ ] **C.1.5 — Resolve fallout.** Fix any remaining references to the deleted
      types (`SymbolTable`, `ReferenceResolver`, `Def`, `Resolution.Resolved`
      shape) across `infra/metadata`. `Resolution` itself (Resolved/Diagnostic)
      stays — it's the pass's public result type.

- [ ] **C.1.6 — Full suite + lint.**
      ```bash
      ./gradlew :infra:metadata:test :infra:metadata:ktlintMainSourceSetCheck \
                :infra:metadata:ktlintTestSourceSetCheck
      ```
      247+ tests green. Watch `ResolutionIntegrationSpec`,
      `StockRoleResolutionSpec`, `MetadataServiceFixtureSpec`,
      `SearchBlockEndToEndSpec`, `Phase2_2ExpressivenessSpec`.

- [ ] **C.1.7 — Confirm the directory shape + commit.**
      `infra/metadata/src/main/kotlin/infra/metadata/resolve/` now contains only
      `ReferenceResolutionPass.kt` and `DrillMapValidator.kt`. Revert TEMP
      `mavenLocal()`; build against published `0.3.0`; commit + push.

**Stage DoD:**
- Seven boxes checked.
- `resolve/` = `ReferenceResolutionPass.kt` + `DrillMapValidator.kt` only.
- Full `:infra:metadata:test` green; ktlint clean; builds against published
  `0.3.0`.
