# Progress · Phase M1 · Core extraction

> `[x]` = developer intent; verify against runtime before trusting.

## Status

**M1 code-complete & green locally 2026-07-05.** Both modules build/test green
(`./gradlew build`), Maven-Local consumable at `0.0.1-LOCAL`, kantheon byte-untouched
by the port. First real tag `kotlin-metadata/v0.1.0` is deferred to **M2.2** (publish
tags held pending feature review — same policy as M0).

Provenance: copied from `kantheon/services/ariadne` at kantheon `9328e98` (the frozen
pre-arc baseline).

## Moved-spec ledger (RM3 — for M4's delete list)

**24 specs moved** (23 core in `ttr-metadata` + 1 in `ttr-metadata-git`):

- M1.1 (9): `ModelTtrLoadSpec`, `A5DiagnosticsSpec`, `source/{FallbackSource,
  InlineMappingEquivalence, InlineMappingSynthesis, PackageResolution,
  StockRoleResolution, V21Samples}Spec`, `git/GitArchiveStorageSpec` (→ `-git`).
- M1.2 (15): `resolve/{DrillMapValidator,ResolutionIntegration}Spec`,
  `graph/TraverseEdgesHandlerSpec`, `search/{Keyword,Regex,Substring}AlgorithmSpec`,
  `search/{AllAlgorithmAndPostProcess,SearchScaffolding}Spec`,
  `refresh/MetadataRefresherSpec`, `export/{DbErCncSplit,ExportRoundTrip,
  GraphDotExporter,InlineMappingExport,MetadataExportPipeline,ModelToDefinitions}Spec`.

**14 specs stay in kantheon** (grpc-layer / wrapper — M4 keeps): `MetadataQuerySpec`,
`MetadataServiceFixtureSpec`, `Phase2_2ExpressivenessSpec`, `QueryParseWorkerSpec`,
`grpc/{GetModel,GetObjectColumnSearchHints,ListObjectsFuzzyAttributeMapping,
ListObjectsFuzzyOnlyFilter,ListObjectsFuzzyOnlyFixture,ListObjectsPackageFilter,
PageTokenCodec,ResolveArea}Spec`, `refresh/RefreshSchedulerSpec`, `search/SearchRpcSpec`.

**New library specs (not moved):** `query/MetadataQuery{ListObjects,Fuzzy,Paging,
ResolveArea}Spec` (MD2 twins), `DependencyRulesSpec`, `TatrmanRepoFixtureSpec`.

## Key facts / deviations

- **M1.1/M1.2 split is a planning boundary, not a compile boundary** — `resolve/` and
  `export/{ModelToDefinitions,TtrWriter}` were pulled into M1.1 (reconcile→resolve,
  InlineMappingEquivalenceSpec→export). M1.2 added graph/search/registry/refresh +
  GraphDotExporter + MetadataLoader + MetadataQuery.
- **De-proto complete:** `plan.v1.*` and `ariadne.v1.{SearchRequest,EdgeType,Direction}`
  replaced by library types (`model/QualifiedName.kt`, `search/SearchQuery.kt`, graph
  `EdgeType`/`Direction`). `dependencyRules` gate + `DependencyRulesSpec` prove no
  ktor/grpc/otel/jgit/protobuf on the core classpath. `RefreshScheduler`,
  `PageTokenCodec`, `MetadataExportRoutes`, `QueryParseWorker` NOT present.
- **Two behavioral fixes (the pre-arc residuals, fixed in the library not Ariadne):**
  (1) stock-role auto-import — `BuiltinStockSource` now declares an empty package so the
  symbol keys as the D15 `cnc.role.<name>` the Resolver imports; (2) same-package er
  resolution with a non-default namespace — `PublishedResolverAdapter` registers symbols
  under `declaredPackage ?: computedPackage`, matching the pass's resolution scope.
- **`fetchVersion()`** hashes `.ttr` AND `.ttrm`; **`listFiles(extensions, prefixes)`**
  moved surface kept (contracts §2 v1.1).
- **MetadataQuery paging** uses a unique full-qname cursor (not the display sort key), so
  tie rows aren't skipped on resume.
- **Maven Local:** `-Pversion=0.0.1-LOCAL publishToMavenLocal` publishes the closed set
  (ttr-parser/writer/semantics/metadata/metadata-git); the ttr-metadata POM references
  the ttr-* deps at `0.0.1-LOCAL` (api re-export, compile scope). `publish.yml` +
  `PUBLISHING.md` handle `kotlin-metadata/v*` (both artifacts, lockstep).
