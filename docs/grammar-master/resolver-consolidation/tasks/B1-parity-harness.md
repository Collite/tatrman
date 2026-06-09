# B.1 — Parity harness scaffolding (red)

**Repo:** ai-platform. **Effort:** ~half day.

**Pre-flight:**
- Phase A.1 done; `org.tatrman:*:0.3.0` resolvable.
- ai-platform PR #89 (stock swap) merged → `ttr-semantics` is on
  `infra/metadata`'s classpath.
- Branch `grammar-master/resolver-consolidation` off `main`.
- Read [`../contracts.md`](../contracts.md) §3, and skim the existing specs in
  `infra/metadata/src/test/kotlin/infra/metadata/resolve/ResolutionIntegrationSpec.kt`
  and `infra/metadata/src/test/kotlin/infra/metadata/source/StockRoleResolutionSpec.kt`
  for the `LoadedFile`-building pattern.

**Goal:** a Kotest spec that drives **both** the legacy `ReferenceResolver` and
the (not-yet-existent) `PublishedResolverAdapter` over a shared corpus and asserts
identical results. It must fail to **compile** on the missing adapter — nothing
else.

---

- [ ] **B.1.1 — Bump the version ref.** `gradle/libs.versions.toml`:
      `tatrman-modeler = "0.3.0"`. (Local verification: add `mavenLocal()` to the
      top of `settings.gradle.kts` `dependencyResolutionManagement.repositories`
      with a `// TEMP` comment; revert before any commit.)

- [ ] **B.1.2 — Create `ResolverParitySpec.kt`** in
      `infra/metadata/src/test/kotlin/infra/metadata/resolve/`. Define the case
      shape and the assertion (contracts §3):
      ```kotlin
      data class ParityCase(
          val name: String,
          val files: List<infra.metadata.source.LoadedFile>,
          val refs: List<Pair<String, ResolutionContext>>,
      )

      private fun assertSameResolution(a: Resolution, b: Resolution) {
          when {
              a is Resolution.Resolved && b is Resolution.Resolved ->
                  a.qualifiedName shouldBe b.qualifiedName            // proto equality
              a is Resolution.Diagnostic && b is Resolution.Diagnostic ->
                  a.code shouldBe b.code                              // code only; message ignored
              else -> error("resolution kind mismatch: legacy=$a adapter=$b")
          }
      }
      ```

- [ ] **B.1.3 — Add a `LoadedFile` builder helper** (or reuse the one in
      `ResolutionIntegrationSpec`). A small `fun loadedFile(path, ttr): LoadedFile`
      that parses via `org.tatrman.ttr.parser.loader.TtrLoader.parseString` and
      fills `computedPackage` / `declaredPackage` / `imports` / `definitions` /
      `schemaCode` / `namespace` exactly as the production loader does. Keep it
      identical to the existing specs so the corpus is realistic.

- [ ] **B.1.4 — Build the corpus.** Port the reference scenarios from
      `ResolutionIntegrationSpec` and `StockRoleResolutionSpec` into `ParityCase`s
      (same `.ttr` sources, same `ResolutionContext`s the pass would build).
      Cover at minimum: same-package sibling, named import, wildcard import,
      bare stock role (auto-import), fully-qualified ref.

- [ ] **B.1.5 — Add the edge cases** (contracts §3): (a) two wildcard imports
      exposing the same bare name → ambiguous; (b) an entity and a table sharing a
      name, both wildcards imported, ref by FQN → resolves (not ambiguous);
      (c) cross-package named import; (d) nested attribute/column FQN ref
      (relation `join` / `er2db_attribute` target); (e) bare unknown name →
      `ttr/unimported-reference`.

- [ ] **B.1.6 — Wire both resolvers** in the spec body:
      ```kotlin
      for (case in cases) {
          val legacy = ReferenceResolver(SymbolTable(buildDefList(case.files)))   // existing helpers
          val adapter = PublishedResolverAdapter.build(case.files)                // DOES NOT EXIST YET
          for ((ref, ctx) in case.refs) assertSameResolution(legacy.resolve(ref, ctx), adapter.resolve(ref, ctx))
      }
      ```
      `buildDefList` mirrors `ReferenceResolutionPass.buildDefList` (extract a
      shared `internal` helper if convenient).

- [ ] **B.1.7 — Confirm red for the right reason.**
      ```bash
      ./gradlew :infra:metadata:compileTestKotlin
      ```
      The only errors are `Unresolved reference: PublishedResolverAdapter`. No
      other compile errors (fix the spec if so).

**Stage DoD:**
- Seven boxes checked.
- `ResolverParitySpec` compiles-fails **only** on the missing
  `PublishedResolverAdapter`.
- `mavenLocal()` is the sole working-tree change to `settings.gradle.kts` and is
  clearly marked TEMP.
