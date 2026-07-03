# Qname redesign — task lists

Per-phase mini-task-lists for [`../plan.md`](../plan.md). Each is one coding session
(≤8 tasks). **Tick each box the moment its task is done — do not batch.** Tests
precede implementation within every stage (TDD). Numbering matches the plan's phases.

| # | Phase | Repo | File | Status |
|---|---|---|---|---|
| 01 | Grammar rename (breaking) | modeler | [`01-grammar-rename.md`](01-grammar-rename.md) | ☐ |
| 02 | AST node renames | modeler | [`02-ast-renames.md`](02-ast-renames.md) | ☐ |
| 03 | Manifest: named schemas + package bindings | modeler | [`03-manifest-bindings.md`](03-manifest-bindings.md) | ☐ |
| 04 | Slot-filling qname + resolver (core) | modeler | [`04-slot-resolver.md`](04-slot-resolver.md) | ☐ |
| 05 | Kotlin twin + conformance (parity) | modeler | [`05-kotlin-conformance.md`](05-kotlin-conformance.md) | ☐ |
| 06 | LSP, lint, hosts | modeler | [`06-lsp-lint-hosts.md`](06-lsp-lint-hosts.md) | ☐ |
| 07 | Migrator (hard cut) | modeler | [`07-migrator.md`](07-migrator.md) | ☐ |
| 08 | Accept new grammar | ai-platform | [`08-ai-platform-accept.md`](08-ai-platform-accept.md) | ☐ |
| 09 | Migrate real content | ai-models | [`09-ai-models-migrate.md`](09-ai-models-migrate.md) | ☐ |

## Ordering & gates

Phases 01–07 land in `modeler` behind the new grammar (no content migrated yet).
**Publish the new MAJOR artifacts** (`kotlin/v<x.y.z>`, `PUBLISHING.md`) between 07
and 08. Phase 08 makes ai-platform build on the new artifacts. Phase 09 is the
content cutover, gated behind a resolved-symbol-equivalence check, then the metadata
pointer advances. Hard cut (D13): no partial-migration state is ever deployed.

## Inventory captured (2026-06-27)

- **ai-models** `model-ttr/`: 10 `.ttrm` files (`areas.ttrm` + `er/db/patterns.ttrm`
  per package). **Zero** `schema` / `def model` / `namespace` keywords — files rely on
  per-kind defaults; references are dotted (`db.dbo.*` ×7+, `er.entity.*` ×26,
  `db.query.*` ×2, `cnc.cnc.role.*`). So ai-models needs a **manifest lift + two
  reference rewrites** (D14 `db.query.*`→`db.dbo.*`, D15 `cnc.cnc.*`→`cnc.*`), not
  keyword rewrites. Manifest at `model-ttr/modeler.toml`
  (`[schemas] declared=["db","er","cnc"]`, `namespaces={db="dbo",er="entity",cnc="cnc"}`).
- **ai-platform** consumes `org.tatrman:ttr-parser|writer|semantics` via
  `gradle/libs.versions.toml` (version ref `tatrman-modeler`, lines 91–93). Consumer
  code: `infra/metadata/src/main/kotlin/infra/metadata/{reconcile,resolve,source,parse,grpc}`
  (10 files reference `SchemaDirective`/`ModelDef`/`schemaCode`).
