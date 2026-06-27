# 08 — ai-platform: accept the new grammar (lockstep gate)

**Repo:** `~/Dev/ai-platform`. Goal: ai-platform builds and loads on the new **major**
parser artifacts *before* any ai-models content migrates, so the runtime can parse the
new syntax the instant content flips. See [`../plan.md`](../plan.md) Phase 8.

**Pre-flight.** Phases 01–07 merged in `modeler` and the new MAJOR artifacts published
(`org.tatrman:ttr-parser|writer|semantics`, tag `kotlin/v<x.y.z>`, `PUBLISHING.md`).
Baseline `./gradlew :infra:metadata:test` green on the *old* artifact.

**Consumer surface (verified 2026-06-27):**
- Version ref: `gradle/libs.versions.toml` → `tatrman-modeler` (lines 91–93 wire
  `ttr-parser|writer|semantics`).
- Code: `infra/metadata/src/main/kotlin/infra/metadata/{reconcile,resolve,source,parse,grpc}`
  — 10 files reference `SchemaDirective` / `ModelDef` / `schemaCode`; resolution +
  `computePackageFromPath` live in `resolve/ReferenceResolutionPass.kt`, `source/Source.kt`.

## Tasks

- [ ] **8.1 Bump the artifact.** Set `tatrman-modeler` in `gradle/libs.versions.toml`
  to the new MAJOR. `./gradlew :infra:metadata:compileKotlin` — collect breakages.
- [ ] **8.2 Fix renamed AST.** `SchemaDirective`→`ModelDirective`
  (`schemaCode`→`modelCode`, new `schema?`); `ModelDef`→`ProjectDef`. Update the 10
  consumer files; `ModelReconciler.kt`, `Resolution.kt`, `DrillMapValidator.kt`,
  `BuiltinStockSource.kt`, `MetadataServiceImpl.kt`, `QueryParseWorker.kt`,
  `MetadataModelHandle.kt`.
- [ ] **8.3 Reconcile resolution with the slot model.** `ReferenceResolutionPass.kt` +
  `Source.kt`: the old `namespace` slot becomes `schema` (db/binding) / absent (er/md).
  Confirm ai-platform's `computePackageFromPath` + reference resolution produce the
  same resolved symbols as the modeler resolver (parity is the contract). Adjust any
  code that read the `namespace` segment positionally.
- [ ] **8.4 Conformance/parity check.** Run a load of **migrated sample content**
  (Phase 07 output, e.g. the retail example) through the metadata loader; resolved
  symbols must match the modeler resolver. No old-syntax acceptance path (hard cut).
- [ ] **8.5 Gate the flip.** Merge ai-platform on the new artifact but keep the
  metadata service pointing at the **current** ai-models commit until Phase 09 lands;
  document the two-step advance in the deploy runbook.

## Done when

- [ ] `./gradlew :infra:metadata:test` green on the new artifacts.
- [ ] Staging load of migrated sample content succeeds; resolved-symbol parity with
  modeler confirmed.
- [ ] Metadata pointer still on the pre-migration ai-models commit (no content flipped
  yet).

**Verify:** `./gradlew :infra:metadata:test` · staging load smoke against Phase-07
sample output.
