# D.1 — (Optional) Fold import/circular diagnostics into the published Validator

**Repo:** ai-platform (possibly a small modeler follow-up). **Effort:** ~half day.
**Pursue only if you want even the import/circular diagnostics to stop being
hand-maintained.** Phases A–C already deliver the headline promise; this is
incremental cleanup.

**Pre-flight:** C.2 done. Read [`../contracts.md`](../contracts.md) §5 and the
published `Validator.validateImports` / `validateCircularDependencies` +
`PackageGraphBuilder` signatures.

**Scope note:** the published `Validator` covers `ttr/unused-import`,
`ttr/duplicate-import`, `ttr/wildcard-with-no-matches` (via `validateImports`)
and `ttr/circular-package-dependency` (via `validateCircularDependencies` +
`PackageGraphBuilder`). It does **not** cover `ttr/missing-package-declaration`
(needs the project root + a package-decl source the Kotlin `ParseResult` doesn't
carry) — that emitter **stays** in `ReferenceResolutionPass`.

---

- [ ] **D.1.1 — Parity spec first (red).** New `ImportDiagnosticsParitySpec.kt`:
      for the existing import/circular fixtures, collect the **set** of
      `(code)` (and, where both sides have them, `(code, file)`) emitted by (a)
      the current `ReferenceResolutionPass` emitters and (b) the published
      `Validator.validateImports` + `validateCircularDependencies`. Assert the
      sets are equal for the covered codes (exclude `missing-package-declaration`).

- [ ] **D.1.2 — Build the adapter inputs.** From the `LoadedFile`s, build the
      published `SemanticDocument` per file (uri, definitions, schemaCode,
      namespace, packageName, imports) and a `PackageGraphBuilder(symbolTable,
      documentImports)` where `documentImports[uri] = file.imports`. Reuse the
      `PublishedResolverAdapter`'s `SymbolTable`.

- [ ] **D.1.3 — Map diagnostics to proto `LoadWarning`.** A small mapper from
      `org.tatrman.ttr.semantics.ValidationDiagnostic` →
      `infra.metadata.source.LoadWarning` (sourceId, file, line/col = -1, message
      = the published message or the existing ai-platform wording — pick one and
      keep `StockRoleResolutionSpec`/import specs green).

- [ ] **D.1.4 — Green the parity spec.** Resolve any code-set differences (e.g.
      wildcard-match counting, self-import handling — the published
      `PackageGraphBuilder` already skips self-imports, matching ai-platform).

- [ ] **D.1.5 — Switch the pass.** Replace the hand-rolled unused/duplicate/
      wildcard/circular emitters in `ReferenceResolutionPass` with the published
      `Validator`/`PackageGraphBuilder` via the mapper. Keep
      `missing-package-declaration` and the per-reference resolution loop.

- [ ] **D.1.6 — Delete the hand-rolled emitters + parity spec; full suite.**
      Remove `detectCircularDependencies` and the inline import-diagnostic blocks
      now covered; delete `ImportDiagnosticsParitySpec.kt`. Run
      `./gradlew :infra:metadata:test :infra:metadata:ktlintMainSourceSetCheck` —
      green. Revert TEMP `mavenLocal()`; commit + push.

**Stage DoD:**
- Six boxes checked.
- Import/circular diagnostics emitted by the published `Validator`; only
  `missing-package-declaration` + the resolution loop remain hand-rolled in the
  pass.
- Full `:infra:metadata:test` green; ktlint clean.
