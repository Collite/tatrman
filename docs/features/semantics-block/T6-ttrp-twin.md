# T6 — TTR-P BuiltinCatalog twin entries

Pre-flight: none on T1–T5 (independent of the grammar work; coordinate the merge with the in-flight TTR-P branch — these files live in `packages/kotlin/ttrp-frontend`, which TTR-P Phase 2 does not currently modify, but rebase before opening the PR).

Context: ai-platform's Translator gains catalog functions `period_start`, `period_end`, `geo_distance_m` with per-dialect lowering (ai-platform `feature-grounding-contracts.md` §6 is the **normative signature table**). TTR-P's expression IR resolves functions via `BuiltinCatalog` `CatalogEntry`s gated by engine capability manifests; these twins must not drift.

- [ ] **T6.1 — Drift-guard test first.** In `ttrp-frontend` tests add `GroundingFunctionsSignatureSpec`: a literal table (name, arity, arg types, return type, null rule) for the three functions, asserted against the `BuiltinCatalog` entries. Header comment: "MUST match ai-platform feature-grounding-contracts.md §6 — change both or neither." Red.
- [ ] **T6.2 — Catalog entries.** Add to `BuiltinCatalog`:
  `CatalogEntry(CatalogId("fn.period_start"), "period_start", SCALAR, [Text, Text?], ReturnTypeRule.Fixed(Datetime), NullRule.STRICT)`;
  `fn.period_end` identical (return exclusive-end semantics documented in Kdoc);
  `CatalogEntry(CatalogId("fn.geo_distance_m"), "geo_distance_m", SCALAR, [Number × 4], ReturnTypeRule.Fixed(Number), NullRule.STRICT)`.
  (Adjust optional-second-arg representation to however the catalog models optional params; if it doesn't, register 1-arg and 2-arg overloads — follow the existing pattern, don't invent.)
- [ ] **T6.3 — Typechecker coverage.** Expression golden tests: `period_start('202605') <= d`, `geo_distance_m(lat, lon, 49.19, 16.61) < 20000` typecheck; wrong arity/types produce the standard TTRP diagnostics.
- [ ] **T6.4 — Engine manifests.** Add the three `fn.*` ids to the PG and MSSQL engine-type manifests (T6 β format from TTR-P Stage 2.2); NOT to Polars/bash (capability miss ⇒ node-granularity re-placement per B-T5-b — exactly the designed behavior). Capability-check test: a node using `fn.geo_distance_m` placed on Polars triggers the re-placement path.
- [ ] **T6.5 — Alias reject rows.** Extend the closed alias table: `period_from` → `period_start`, `distance` → `geo_distance_m` (TTRP-FN-001 with suggestion). Test each.
- [ ] **T6.6 — Green + cross-note.** `./gradlew :packages:kotlin:ttrp-frontend:test` green; add a row to the TTR-P plan's cross-cutting table noting the grounding twin contract and its ai-platform anchor.
