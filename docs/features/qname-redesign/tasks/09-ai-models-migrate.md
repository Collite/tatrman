# 09 — ai-models: migrate the real content (the cutover)

**Repo:** `~/Dev/ai-models`, root `model-ttr/` (loaded by ai-platform's metadata
service; `METADATA_GIT_SUBDIR=model-ttr`). Goal: migrated content parses + resolves on
the new grammar with a **byte-identical resolved-symbol set**, then advance the
metadata pointer. See [`../plan.md`](../plan.md) Phase 9.

**Pre-flight.** Phase 08 merged (ai-platform on the new artifact). Inventory captured
2026-06-27:

- 10 `.ttrm` files: `areas.ttrm` + per-package `er.ttrm` / `db.ttrm` / `patterns.ttrm`
  (`ucetnictvi`, `artikl`, `uzivatel`, …).
- **No `schema` / `def model` / `namespace` keywords** → **no keyword rewrites
  needed.** Files rely on per-kind defaults.
- Manifest `model-ttr/modeler.toml`: `[schemas] declared=["db","er","cnc"]`,
  `namespaces={db="dbo", er="entity", cnc="cnc"}`, `[stock] load=["cnc-roles"]`,
  `[language] preferred="cs"`.
- Reference patterns: `db.dbo.*` (×7+), `er.entity.*` (×26), `db.query.*` (×2),
  `cnc.cnc.role.*`.

## Tasks

- [ ] **9.1 Baseline snapshot.** Run the *current* metadata loader against the present
  `model-ttr/` (old artifact pin) and snapshot the **resolved-symbol set** — the
  invariant the migration must preserve. (`./gradlew :infra:metadata:...` load task, or
  the modeler resolver over `model-ttr/`.)
- [ ] **9.2 Manifest lift.** Branch `ai-models`. Rewrite `model-ttr/modeler.toml`:
  `db="dbo"` → `[schemas.dbo] { database = "<FILL: DF Partner DB>", db-schema = "dbo",
  dialect = "tsql" }`. **Drop `er="entity"` and `cnc="cnc"`** — er/cnc have no schema
  slot; `entity`/`role` reclassify as *kinds*, not schemas. Keep `[stock]`,
  `[language]`, `[lint]`. Add `[packages.*] default-schema = "dbo"` for the db-bearing
  packages so `db.dbo.*` refs can shorten later.
- [ ] **9.3 Reference rewrites (D14/D15 — resolved by the migrator).** The migrator
  rewrites the two patterns that change meaning: **`db.query.<X>` → `db.dbo.<X>`**
  (queries are db objects in the default schema) and **`cnc.cnc.role.<X>` →
  `cnc.role.<X>`** (cnc is schema-less). The other two reclassify with no edit:
  `db.dbo.X` → model db / schema dbo ✓; `er.entity.X` → model er / kind entity ✓.
  Spot-check a handful by hand against the resolver before the bulk apply.
- [ ] **9.4 Run the migrator.** `@modeler/migrate migrate-qnames model-ttr/ --dry-run`;
  review (expect: manifest lift + the D14/D15 reference rewrites across `db.query.*`
  ×2 and `cnc.cnc.*` refs); apply.
- [ ] **9.5 Equivalence gate.** Re-run the loader on the migrated tree; **diff the
  resolved-symbol set against the 9.1 baseline — must be identical.** Any diff blocks
  the cutover.
- [ ] **9.6 Cutover.** Land the `ai-models` branch; advance the ai-platform metadata
  pointer to the migrated commit (staging → prod). Pointer + artifact now both on the
  new version.

## Done when

- [ ] Migrated `model-ttr/` parses + resolves on the new grammar.
- [ ] Resolved-symbol set **byte-identical** to the 9.1 baseline.
- [ ] Metadata service green on staging, then prod; pointer on the migrated commit.

**Verify:** loader resolved-symbol diff (9.1 vs 9.5) empty ·
`@modeler/migrate migrate-qnames model-ttr/ --dry-run` clean.

## Rollback

Revert the `ai-models` content PR; the metadata pointer stays on the old commit until
9.6, so prod is never in a half-migrated state. If 9.3/9.5 surfaces a classifier gap,
fix it in modeler + republish before retrying — no hand-edits to content.
