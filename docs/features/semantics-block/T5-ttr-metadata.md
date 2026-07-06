# T5 — ttr-metadata typed-model surface + publish

Pre-flight: T4 green. ttr-metadata pins ttr-parser/semantics versions and re-exports them as `api` deps (contracts.md §1) — bump the pins to the 4.2 builds first.

- [ ] **T5.1 — Specs first.** In `packages/kotlin/ttr-metadata` add `SemanticsSurfaceSpec` (Kotest, loads an annotated fixture model via `LocalFsStorage`): (a) `Attribute` model objects expose `semantics: ResolvedSemantics?`; (b) `Entity`/`DbTable` expose `semanticsKind`; (c) `MetadataQuery` answers `periodTableFor(pkg)`, `semanticRole(attrQname)`, `attributesByRole(pkg, role)`, `poiEntities(pkg)`, `fxRateTableFor(pkg)`; (d) a model whose semantics blocks have diagnostics loads with those as `LoadIssue`s and WITHOUT the offending `ResolvedSemantics` (degrade, don't fail). All red.
- [ ] **T5.2 — Typed model.** Extend `ModelObject` variants (`Entity`, `Attribute`, `DbTable`, `DbColumn`) with the resolved-semantics fields, populated in the load pipeline (parse → reconcile → resolve) from ttr-semantics' `ResolvedSemantics`. Immutable, snapshot semantics as everywhere in the library.
- [ ] **T5.3 — MetadataQuery accessors.** Implement the five accessors from T5.1(c) on the query facade (these are what ai-platform's Ariadne gRPC layer and the grounding services' metadata lookups will call). Kdoc each with its grounding use-case.
- [ ] **T5.4 — LoadIssue taxonomy.** Map TTR-SEM-2xx diagnostics into `LoadIssue` (id-free taxonomy per the extraction design) with source positions; test T5.1(d) goes green.
- [ ] **T5.5 — Version + publish.** Bump lockstep pins; update `PUBLISHING.md` rows if wording drifted; tag `kotlin/v0.9.0` (parser/writer/semantics bundle with grammar 4.2, includes metadata as well)
- [ ] **T5.6 — Changelog + docs.** ttr-metadata `contracts.md` changelog entry (new API surface — the five accessors + model fields); grammar CHANGELOG cross-link; mark T5 done in README task list.
