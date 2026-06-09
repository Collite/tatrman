# Legacy-YAML→TTR converter: inline mappings + db/er/cnc split — task index

Item 4 of the language-updates batch. **Spans two repos.** The converter itself
lives in **ai-platform** (`infra/metadata/.../cli/YamlToTtrCli.kt` →
`infra/metadata/.../export/ModelToDefinitions.kt` → `TtrRenderer`), but the
rendering capability it needs lives in **modeler** (`ttr-writer`, published as
`org.tatrman:ttr-writer`).

Each linked file is a mini-task-list (≤8 tasks) for one coding session. **Tick
each box the moment its task is done — do not batch.** Tests precede
implementation within every stage (TDD).

## The two rules (confirmed design)

- **Rule 1 — short inline entity→table mappings.** Stop emitting standalone
  `def er2db_entity / er2db_attribute / er2db_relation` blocks. Instead attach
  the mapping inline to the owning def's `mapping:` property:
  - **Entity with a table target** → entity-level block
    `mapping: { target: { table: … }, columns: { attr: COL, … } }`. Each column
    entry uses the **short bare-id** form (`attr: COL`) when the target is a
    plain column; the block form (`attr: { target: { column: … } }` or
    `{ target: <expr> }`) only when an expression/explicit target is needed.
  - **Attribute with its own column mapping and no entity-level block** →
    per-attribute short form `mapping: COL`.
  - **Relation** → short `mapping: <fkRef>` (the model only carries an FK ref).
  - **`er2cnc_role` has no inline form in the grammar** → it stays a standalone
    `def er2cnc_role` (emitted into `cnc.ttr`).
  - Golden shape: `modeler/samples/2.1/er.ttr` (artikl = entity-level block,
    produkt = per-attribute short forms).

- **Rule 2 — collapse to db/er/cnc per package.** Within each per-source-file
  package (the existing `packageOf` grouping is **kept**), emit at most:
  - **`db.ttr`** — tables, views, foreign keys, procedures, **and queries**.
  - **`er.ttr`** — entities + relations (the separate `relation.ttr` folds in).
  - **`cnc.ttr`** — roles + `er2cnc_role` mappings.
  - **`map.ttr` disappears** (er2db mappings are now inline).
  These files are emitted **without** a `schema`/`namespace` directive; each def
  derives its schema + namespace from its kind. (That's item 2 — see
  Preconditions.)

## Preconditions (hard dependencies — implement in this order)

1. **Item 2 shipped end-to-end:** kind-derived schema/namespace defaults in TS
   **and** Kotlin, the new `org.tatrman:ttr-semantics` published, **and
   ai-platform bumped to consume it.** Without this, the directive-less
   `db.ttr`/`er.ttr` (mixing `db` tables with `query` queries, `er` entities
   with `er`-namespace `relation`s) will not resolve correctly on reload.
   See `docs/features/pkg-schema-defaults/`.
2. **Stage 1 of *this* feature shipped:** `ttr-writer` renders inline mappings
   and a new `org.tatrman:ttr-writer` version is published, **before** the
   ai-platform stages bump to it.

So the real execution order is: **item 2 (→ publish ttr-semantics → ai-platform
bump) → Stage 1 here (→ publish ttr-writer) → Stages 2 & 3 (ai-platform).**

## Stages

| # | Mini-task-list | Repo | Status |
|---|---|---|---|
| 1 | [ttr-writer: render inline mappings + publish](01-ttr-writer-inline-render.md) | modeler | ☐ |
| 2 | [Converter: emit inline mappings (no standalone er2db)](02-converter-inline-mappings.md) | ai-platform | ☐ |
| 3 | [Converter: db/er/cnc split + directive-less + dep bumps](03-converter-file-split.md) | ai-platform | ☐ |

## Definition of DONE (whole feature)

- [ ] `ttr-writer` renders both inline `mapping` variants (bare-id + block) on
      entity/attribute/relation defs, with a parse→render→parse round-trip test;
      new version published to GitHub Packages.
- [ ] Converter emits, **per package**, only `db.ttr` (tables/views/fk/
      procedures/queries), `er.ttr` (entities + relations, inline mappings), and
      `cnc.ttr` (roles + er2cnc_role). No `map.ttr`, no `relation.ttr`.
- [ ] Emitted `db.ttr`/`er.ttr`/`cnc.ttr` carry **no** schema/namespace
      directive.
- [ ] **Semantic-equivalence check:** loading the new output through semantics
      yields the **same resolved-qname set and same diagnostic-code set** as the
      pre-change output for the same input fixtures. (Inline vs standalone
      mappings must resolve to identical mapping qnames.)
- [ ] `./gradlew :infra:metadata:test` green; the CLI run exits 0 with no render
      self-validation failures (exit code 3) on the sample corpus.
- [ ] ai-platform `libs.versions.toml` `tatrman-modeler` bumped to the published
      ttr-writer (and ttr-semantics) version; full ai-platform suite green;
      `just lint-all` clean.

## Required reading before starting

- `modeler/samples/2.1/er.ttr` — the golden inline-mapping shapes.
- `modeler/packages/grammar/src/TTR.g4` §"v2.1 — inline mapping" (rules
  `mappingProperty`, `mappingValue`, `mappingBlock`, `mappingColumnMap`).
- `modeler/packages/kotlin/ttr-parser/.../model/Definition.kt` — `MappingProperty`
  sealed type (`MappingPropertyBareId` / `MappingPropertyBlock`), and the
  `mapping` field on `EntityDef` (:123), `AttributeDef` (:141), `RelationDef` (:154).
- ai-platform `infra/metadata/.../export/ModelToDefinitions.kt` — the converter.
