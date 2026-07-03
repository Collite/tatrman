# Phase 2.8 — ai-platform consumer switch (separate ai-platform PR)

**Repo:** **ai-platform**. **Owner:** one developer. **Estimated effort:** 1–2
days — the resolver/symbol-table delete is mechanical; the BuiltinStockSource
collapse needs care.

**Pre-flight:**
- Phase 2.7 DoD met. `org.tatrman:ttr-semantics:0.1.0` resolvable from
  GitHub Packages.
- Fresh ai-platform branch: `grammar-master/consume-tatrman-semantics-0.1.0`.

**Reference:**
- [`../../architecture.md`](../../architecture.md) §"Phase 2 architecture
  additions".
- [`../../contracts.md`](../../contracts.md) §4.

**Tasks:**

- [ ] **2.8.1 — Add `tatrman-ttr-semantics` to `libs.versions.toml`.**
      ```toml
      tatrman-ttr-semantics = { module = "org.tatrman:ttr-semantics", version.ref = "tatrman-modeler" }
      ```
      (Reuse the existing `tatrman-modeler` version ref from Phase 1.8.2.
      Bump it to `0.2.0` or whatever the released semantics version is.)

- [ ] **2.8.2 — Add the dependency to `infra/metadata/build.gradle.kts`.**
      `implementation(libs.tatrman.ttrm.semantics)`.

- [ ] **2.8.3 — Refactor `BuiltinStockSource` to delegate.** Replace its body
      with a call to `org.tatrman.ttrm.semantics.StockLoader.load()` and wrap
      the result in ai-platform's `SourceSnapshot` shape. Delete the bundled
      `infra/metadata/src/main/resources/builtin/cnc-stock-roles.ttrm` (the
      canonical copy now ships inside the artifact).

- [ ] **2.8.4 — Replace `infra/metadata/resolve/Resolver` with the published
      one.** Update `infra/metadata/resolve/ReferenceResolutionPass.kt` to
      import `org.tatrman.ttrm.semantics.Resolver` (and `SymbolTable`,
      `Qname`). Verify the public API match — the contract was designed for
      drop-in compatibility.

- [ ] **2.8.5 — Delete the ai-platform `resolve/` directory contents.**
      Remove `ReferenceResolver.kt`, `SymbolTable.kt`,
      `DrillMapValidator.kt`. Keep `ReferenceResolutionPass.kt` —
      it's ai-platform-specific (does proto conversion, plugs into the
      reconciler). It just changes its imports.

- [ ] **2.8.6 — Wire the validator.** ai-platform's existing validators that
      are now in `org.tatrman.ttrm.semantics.Validator` should be removed
      from ai-platform; the published `Validator` runs in
      `ReferenceResolutionPass.kt`'s pipeline. ai-platform-specific
      validators (anything touching proto, the model graph, or the
      reconciler) stay.

- [ ] **2.8.7 — Run full ai-platform test suite + lint.**
      ```bash
      just test-kt infra/metadata
      just test-all
      just lint-all
      ```
      Iterate until green. Watch in particular: `ReferenceResolverSpec`,
      `ResolutionIntegrationSpec`, `MetadataServiceFixtureSpec`,
      `SearchBlockEndToEndSpec`, `Phase2_2ExpressivenessSpec`.

- [ ] **2.8.8 — Verify the goal: next grammar bump is consumer-side
      version-bump only.** Simulate by bumping the grammar version locally in
      modeler (e.g. `2.2` → `2.3`), publishing a new modeler artifact set,
      and switching ai-platform's `tatrman-modeler` version ref. The
      ai-platform suite should pass with no source edits (other than the
      version-ref bump and any genuinely-new-syntax test fixtures).

**Stage DoD:**
- Eight tasks checked.
- `infra/metadata/src/main/kotlin/infra/metadata/resolve/` contains only
  `ReferenceResolutionPass.kt`; the other three files are deleted.
- `infra/metadata/src/main/resources/builtin/cnc-stock-roles.ttrm` deleted.
- ai-platform test suite green.
- The 2.8.8 simulation succeeds — the grammar-master refactor's headline
  promise is delivered: grammar changes no longer require code in ai-platform.

**Phase 2 COMPLETE when this stage's DoD is met.**

---

## Project-wide DoD

- Both phases' DoD lists in `tasks/INDEX.md` fully checked.
- `tasks/INDEX.md` has both phases marked complete.
- Memory note (modeler-side) records the new contract: grammar changes ship
  via `org.tatrman:*` artifact versions; ai-platform consumes; no repo
  hopping required.
