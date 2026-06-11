# D.1 — (Optional) Fold import/circular diagnostics into the published Validator

> ## Outcome: EVALUATED — NOT PURSUED (2026-06-11)
>
> The D.1.1 parity gate was built and run against ai-platform's existing
> import/circular fixtures (the `A5DiagnosticsSpec` set). **It fails decisively**,
> so the published `Validator`/`PackageGraphBuilder` cannot replace ai-platform's
> hand-rolled emitters without regressing diagnostics. The hand-rolled emitters
> **stay**. Decision: abandon Phase D (owner-approved).
>
> Root cause — the same import-model mismatch documented in
> [`../contracts.md`](../contracts.md) §3.1: the published `Validator` is built for
> modeler-v1.1 **package** imports, while ai-platform uses **directory-package +
> schema.namespace** imports. Concretely, over the alpha↔beta circular fixture and
> the wildcard-no-match fixture:
>
> | fixture | ai-platform (legacy) emits | published `Validator`+`PackageGraphBuilder` emits |
> |---|---|---|
> | wildcard-no-match | `unused-import` + `wildcard-with-no-matches` | `wildcard-with-no-matches` only |
> | circular (alpha↔beta) | `unused-import` + `wildcard-with-no-matches` + **`circular-package-dependency`** | `wildcard-with-no-matches` only — **cycle NOT detected** |
>
> - **Circular detection is lost**: `PackageGraphBuilder` keys nodes by *declared*
>   package (`alpha`, `beta`) but edges by `packageOfImport` (`beta.entity`,
>   `alpha.entity`), which are not nodes → every edge is dropped → no cycle. The
>   hand-rolled `detectCircularDependencies` uses *computed* (directory) package
>   nodes and the import's *first segment* as the dep, so it sees the cycle.
> - **`unused-import` differs**: `Validator.validateImports` only checks `unused`
>   for non-wildcard imports and derives "used" from `viaStep ∈ {NamedImport,
>   WildcardImport}` — but ai-platform refs resolve via `FullyQualified` (the
>   package/namespace mismatch from Phase C), so named-import usage and unused
>   wildcard imports are both judged differently.
> - **`wildcard-with-no-matches` uses `getByPackage(target)`** (declared-package),
>   empty for ai-platform's schema.namespace wildcards → would false-positive on
>   real wildcard imports that do match.
>
> Making the published `Validator` import-model-agnostic would be a significant,
> risky change to a conformance-locked modeler component (modeler v1.1
> deliberately uses package imports). Not worth it for optional cleanup: Phases
> A–C already deliver the headline promise (a grammar/version bump reaches
> ai-platform as a version-ref change, rehearsed), and the hand-rolled emitters
> are small, correct, and `LoadedFile`/proto-coupled anyway.

---

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
