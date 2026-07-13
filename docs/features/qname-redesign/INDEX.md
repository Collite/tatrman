> **⚠ RECONCILIATION NOTE (2026-07-13):** this feature was **delivered in substance** — grammar 4.0
> ships the `project`/`model`/`schema` renames (ttr-metadata RM2/RM10 and the manual teach them) —
> but the 68 checkboxes below were never ticked. Treat this folder as historical; a verification
> pass for cross-repo tails 08/09 is tracked as QN-1 in the open-work portfolio (design repo
> `ecosystem/open-work-260713.md`).

# Feature: Qualified-name redesign — `project` / `model` / `schema`, named bindings, slot-filling resolver

Rework the three concepts that make up a TTR address — the model **type**, the
**namespace**, and the **package** — so that references are as short as possible,
qnames are uniform internally, and the words mean exactly one thing each.

Two problems motivate this:

1. **The word "schema" is overloaded.** Today `schema <code>` declares the *model
   type* (`db|er|md|binding|query|cnc`), while the actual SQL schema (`dbo`) lives in
   the *namespace* slot. So `schema er namespace dbo` reads backwards.
2. **The qname slots are not uniform.** db keys look like `db.dbo.Orders`
   (model·namespace·name) but er keys look like `er.entity.artikl`
   (model·kind·name) — different shapes wearing the same dots, papered over by the
   `namespace || defaultNamespaceForSchema(schema) || def.kind` fallback
   (`semantics/src/reference-index.ts:20`). The "namespace is meaningless for er/md"
   complaint is this asymmetry: er's namespace slot is just echoing the kind.

The fix: rename the keywords so each names one concept, split the **uniform internal
canonical key** from the **short surface reference**, promote schemas to **named
bindings** in `modeler.toml`, and make the parser a **slot-filling resolver** that
elides everything derivable from context, the file header, the package, or defaults.

## Status

Planning. Artefacts in this folder:

| Artefact | File |
|---|---|
| Architecture (problem, slot model, resolution algorithm, renames, migration) | [`architecture.md`](architecture.md) |
| Contracts (`modeler.toml` schema, DTOs, parser/resolver interfaces, validation) | [`contracts.md`](contracts.md) |
| Phased plan (modeler + ai-platform + ai-models, DoD, migration) | [`plan.md`](plan.md) |

Per-phase task lists are **not** generated yet — they come after this plan is
approved (per the planning skill: architecture + contracts + plan before tasks). Parity with the Kotlin
twin and the conformance harness is in scope and normative, as with
[`pkg-schema-defaults`](../pkg-schema-defaults/INDEX.md).

## Locked decisions (with the user)

- **D1 — `def model` → `def project`.** The whole-artifact header (identity +
  `version` + `description` + `tags`, grammar rule `modelProperty`) is renamed to
  `project`. "The whole ERP v1 is a project." **One `def project` per repo** — its
  identity coincides with the `modeler.toml` project.
- **D2 — `model` becomes the model-type keyword.** The directive `schema <code>`
  becomes `model <code>`. `model` no longer collides with anything because the old
  `def model` is gone (D1). Reads: `model db`, `model er`.
- **D3 — `schema` becomes the namespace/binding keyword.** The old `namespace <id>`
  becomes `schema <id>`. For db/binding it binds to a physical database; for er/md it
  is **absent** — those models have no schema slot. Reads: `model db schema dbo`.
- **D4 — Canonical key is uniform and package-first.**
  `package · model · schema? · kind · name(.sub)*`. Internal only; users never type
  it. Existing layout keys are already package-first
  (`billing.invoicing.er.entity.artikl`).
- **D5 — Surface references are slot-filled and as short as possible.**
  `shop.sales.Orders` is the target ergonomics: package + name, everything else
  derived. Model is derived from kind (kind→model is single-valued), so a file
  header never needs `model` — only an optional `schema`.
- **D6 — ER/MD/CNC skip `schema` entirely.** The schema slot binds to a physical
  database; it is meaningless for a logical model. Schema-bearing: **`db` only**.
  Schema-less: `er`, `md`, `cnc`, `binding`.
- **D7 — Named schemas in `modeler.toml`.** A schema is a named handle that binds to
  `(database, db-schema, dialect)`. Subsumes the embedded-SQL `[[sql.namespace-map]]`
  (`docs/features/embedded-sql/contracts.md` §5) — one source of truth. This answers
  "which database does a query run against?": the query's schema (or the project
  default), resolved through this table.
- **D8 — Packages may carry a default schema.** So a reference under a package
  resolves its schema from the package binding with no guessing.
- **D9 — No-collision rule.** A schema name may not collide with a package name, a
  model code, or a kind keyword. This makes every leading reference segment
  classifiable by vocabulary alone, so input order barely matters for parsing.
- **D10 — Unique-match is scoped + lintable.** When a slot is omitted and exactly one
  symbol matches, accept it — but search the current package/schema first before
  widening, and let `[lint] require-qualified-refs` flag bare cross-schema names.
- **D11 — Major grammar version.** Renaming `schema`→`model`, `namespace`→`schema`,
  `def model`→`def project` is **breaking**; it ships as a new **major** `TTR.g4`
  version with a conformance update and a coordinated artifact bump
  (`org.tatrman:ttr-parser` + the `ttr-parser` wheel) consumed by ai-platform.
- **D12 — `schema` is db-only; files may mix models.** A single file **may** contain
  both `db` and `er` (etc.) defs. The file-header `schema` applies **only** to the
  file's `db`/`binding` defs and unqualified db refs; `er`/`md` defs ignore it. No
  "one model per file" rule.
- **D13 — Hard cut + migrator.** Old keywords (`schema`/`namespace`/`def model`) are
  **removed**, not kept as aliases — `schema`'s meaning *changes*, so a silent alias
  would be dangerous. `@modeler/migrate` rewrites every file mechanically. The
  **ai-models** repo is migrated by the same codemod, in lockstep with the
  ai-platform parser-artifact bump (see [`plan.md`](plan.md) Phase 7).
- **D14 — `query` folds into `db`; queries carry a schema.** There is no separate
  `query` model code. A `def query` is a **db-layer object that lives in a schema**
  (default `dbo`), exactly like a table — so the tables it references resolve in that
  schema. Addressed `db.<schema>.<name>` (kind `query` derived). Migration:
  `db.query.<X>` → `db.dbo.<X>`. `modelCode` set becomes `{db, er, md, binding, cnc}`
  (QUERY removed; the `QUERY` token survives for `def query`).
- **D15 — `cnc` is schema-less; drop the `cnc.cnc` segment.** `cnc` joins `er`/`md`:
  no schema slot. The redundant namespace segment goes away. Migration:
  `cnc.cnc.role.<X>` → `cnc.role.<X>` (model `cnc`, kind `role`, name `X`) — now
  parallel to `er.entity.<X>`.

## Plan

Phased implementation across three repos in [`plan.md`](plan.md).
