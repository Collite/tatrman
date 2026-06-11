# Stage 3 — Converter: db/er/cnc split + directive-less + dep bumps (ai-platform)

**Goal:** collapse the per-package output to at most `db.ttr`, `er.ttr`,
`cnc.ttr`, emitted **without** schema/namespace directives (each def derives
schema+namespace from its kind). Bump the modeler dependency to the published
ttr-writer + ttr-semantics.

**Pre-flight (hard):** item 2 shipped *and* ai-platform consuming the published
`org.tatrman:ttr-semantics` with kind-derived defaults — otherwise the
directive-less files won't resolve. See `docs/features/pkg-schema-defaults/`.

**File:** `infra/metadata/src/main/kotlin/infra/metadata/export/ModelToDefinitions.kt`.
- The bucket tagging in `convert` (112–153) — currently `"er"`, `"relation"`,
  `"db"`, `"map"`, `"query"`, `"cnc"`.
- The partition loop (156–174) and `schemaDirectiveFor` (205) and
  `sourceKindOf`/collision-suffix logic (160–162).
- `packageOf` (184) is **kept** — per-source-file packages stay.

---

- [ ] **3.1 — Tests first: file set per package.** Fixture: a model from one
  entity YAML (entity + attributes + relation + table + fk + query + role +
  er2cnc_role). Assert the bundle for that package contains exactly `db.ttr`,
  `er.ttr`, `cnc.ttr` — and **no** `map.ttr`, **no** `relation.ttr`, **no**
  `query.ttr` (queries live in `db.ttr`). Assert (red) until 3.4.

- [ ] **3.2 — Tests first: bucket membership.** Assert: tables, views, foreign
  keys, procedures, **and queries** land in `db.ttr`; entities **and relations**
  land in `er.ttr`; roles **and** `er2cnc_role` defs land in `cnc.ttr`.

- [ ] **3.3 — Tests first: no directive.** Assert each emitted `ExportFile` for
  these three has `schemaCode == null` and `namespace == null`, so
  `TtrRenderer.renderFile` emits no leading `schema …` line. Assert the rendered
  text does **not** start with `schema`.

- [ ] **3.4 — Re-bucket.** Change the `Tagged.schema` assignment in `convert`:
  fold `"relation"` → `"er"`; move `query` defs into the `"db"` bucket; keep
  `er2cnc_role` (and roles) in `"cnc"`; drop the `"map"` bucket entirely (Stage 2
  removed its producers). Net buckets: `db`, `er`, `cnc`.

- [ ] **3.5 — Directive-less files + filenames.** In the partition loop, set
  `schemaCode = null, namespace = null` for all three files and name them
  `db.ttr` / `er.ttr` / `cnc.ttr`. Decide the collision policy now that schemas
  are merged: since one package may aggregate multiple source files into the
  same bucket, **drop** the `_entity`/`_pattern`/`_query` suffix logic and merge
  their defs into the single file (or keep suffixing if you prefer — state the
  choice in the test). Simplify/remove `schemaDirectiveFor` and `sourceKindOf`
  as they become unused.

- [ ] **3.6 — Bump modeler deps + full gate.** In `gradle/libs.versions.toml`
  set `tatrman-modeler` to the version published in Stage 1 (covers both
  `ttr-parser`/`ttr-writer`) and the ttr-semantics version from item 2. Run
  `just build-kt infra/metadata`, `./gradlew :infra:metadata:test`,
  `just lint-all`. Run the CLI on the sample corpus
  (`:infra:metadata:run -PmainClass=…YamlToTtrCliKt --args="<in> <out>"`); assert
  exit 0 and **no** render self-validation failures (exit 3).

- [ ] **3.7 — End-to-end semantic equivalence + reload.** Load the emitted
  `<out>` directory back through the metadata loader/resolver and assert the
  resolved-qname set and diagnostic-code set match the pre-feature output for the
  same input (the directive-less + inline-mapping output must be semantically
  identical to the old directive-ful + standalone-mapping output). Tick boxes.

### Stage 3 DoD
- [ ] Per package: only `db.ttr` (tables/views/fk/procedures/queries), `er.ttr`
      (entities+relations, inline mappings), `cnc.ttr` (roles+er2cnc_role); no
      directives in those files; reload is semantically identical to before;
      ai-platform suite + lint green; CLI exit 0.
