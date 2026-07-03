# DB and ER models, and bindings — summary

Context for the MD work: the multidimensional model binds *down* to these two existing
schemas. This is a compact summary; the authoritative descriptions are in
`docs/v1/design/architecture.md` and `docs/features/md/design.md` (§3, §6, §8).

## The `db` schema — physical model

The `db` schema is the **physical layer**: the actual database objects. Defs include
tables/columns, primary keys and foreign keys (`FkDef`), stored procedures (`ProcedureDef`),
and queries (`QueryDef`). Objects are addressed by dotted qnames through
`schema → namespace → object kind → object → sub-object`, e.g. `db.dbo.QZBOZI_DF` for a
table in the `dbo` namespace, or `db.query.*` / `db.dbo.*`. The default `db` namespace in
`modeler.toml` is `dbo`. This is the layer MD cubelet bindings ultimately target (table +
columns), and where measure aggregation is physically realized.

## The `er` schema — entity-relation model

The `er` schema is the **conceptual entity-relation layer** sitting above the physical one.
Its defs are **entities** (`EntityDef`), their **attributes** (`AttributeDef`, defined inline
in an entity's `attributes: [...]` list), and **relations** between entities (`RelationDef`,
with cardinality). Qnames look like `er.entity.artikl` for an entity and
`er.entity.artikl.id_artiklu` for one of its attributes; the default `er` namespace is
`entity`. Entities historically carried `nameAttribute` / `codeAttribute` pointers (validated
to exist) for display labels.

The `attribute` keyword is **shared** with the new `md` schema. Rather than fork it, the repo
keeps **one permissive grammar body** (ER-specific and MD-specific properties all optional) and
lets the **semantics layer** enforce which properties are valid per schema. This follows the
repo's standing "parser stays mechanical" invariant — the grammar catches syntax, semantics
catches meaning ("you referred to `er.entity.artiklu` but no such entity exists").

## Bindings — connecting the layers (renamed from "mapping"/"map")

A **binding** is a cross-model link. Until grammar 3.0 this family was called `schema map`
(namespace `er2db`) with an inline `mapping:` property; Phase 0 renames the schema code to
**`schema binding`** (Stage A) and the inline property to **`binding:`** (Stage AA). The
def keywords `er2db_entity` / `er2db_attribute` / `er2db_relation` stay as-is (they name the
specific source→target direction; decided to keep them). The qname namespace was already
`binding.er2dbEntity.*`.

The existing **ER → DB** bindings (`er2db_*`) map an entity to its physical table, an attribute
to its column, and a relation to its FK. The MD work **extends this same binding family** with:

- **`md2db_cubelet`** — binds a cubelet to a physical table. Carries the fact-table **shape**
  (`wide` = one column per attribute/measure; `long` = a code column + a value column),
  the attribute → column and measure → column maps, and a **journaling** mode
  (`overwrite` | `invalidate` | `diff`) governing writeback. An attribute can be reached
  *through a map* (`via:` + a source table/column). **Multi-source** = several `md2db_cubelet`
  defs targeting the same cubelet, each binding a subset of measures; their union must agree on
  the cubelet's grain.
- **`md2db_domain`** — where a `kind: bound` domain's members come from (table + column).
- **`md2db_map`** — the case-table backing a table-backed map (table + the from/to columns).
- **`md2er_*`** — the **structural-only** MD → ER binding (attribute → ER attribute, dimension →
  entity). Shape/journaling/measure-columns are inherently physical, so `md→er` is deliberately
  thinner and **read-oriented**; the existing ER → DB binding completes the chain. v1 ships
  `md→db` fully; `md→er` is structural-only.

`load` (read) and `store`/`append` (write) SQL are **generated** from the binding declaration.
The hard inverse case — an N:1 attribute map must "pick a winner" on store
(`row_number() … qualify = 1`) — is **codegen in semantics/runtime, not grammar**; the grammar
only declares enough for both directions to be generated.

## Why this matters for MD

The MD logical model (domains, dimensions, attributes, maps, hierarchies, measures, cubelets)
is **binding-free** on purpose, so the ROLAP binding (v1) and a future MOLAP binding both attach
to the *same* logical objects as separate definitions. The DB and ER schemas are the targets the
ROLAP binding reaches; the binding family (formerly "map") is the mechanism, now unified across
`er2db_*` and the new `md2db_*` / `md2er_*`.
