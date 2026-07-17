<!-- SPDX-License-Identifier: Apache-2.0 -->
# Language reference

*Reference. TTR-M in full — the definition kinds, properties and constructs the parser accepts.*

!!! note "Skeleton"
    This is the language-reference **skeleton** for TTR-M grammar **v0.9** (the grammar version
    tracks the product `major.minor`). The tables below enumerate the surface; the per-construct
    property detail marked _(from grammar)_ is generated from `TTR.g4` so it cannot drift from what
    the parser accepts, and the prose marked _(hand-written)_ is authored. Fuller worked examples
    are migrating in from the user manual.

## File shape

A TTR-M document is a package member with a model directive:

```
package <name>            // the package this file belongs to
import  <name>[.*]        // optional cross-package imports
model   <code> [schema <id>]   // the layer this file defines
def <kind> <name> { … }   // one or more definitions
```

`<code>` is the layer: `db`, `er`, `binding` (er↔db and other mappings), `cnc`, `md`. Identifiers
allow Latin-1/Extended letters, so real Czech names are valid verbatim.

## Definition kinds _(from grammar)_

| Layer | Kinds |
|---|---|
| `db` | `table`, `column` (inline), `view`, `index`, `constraint`, `fk`, `procedure` |
| `er` | `entity`, `attribute` (inline), `relation` |
| `binding` | `er2db_entity`, `er2db_attribute`, `er2db_relation` |
| `cnc` | `role`, `er2cnc_role` |
| `md` | `domain`, `dimension`, `hierarchy`, `measure`, `cubelet`, `map`, `md2db_*`, `md2er_*` |
| structure | `package`, `import`, `area` (`packages:` / `entities:`), `graph`, `drill_map`, `query`, `world` |

## Packages and areas _(hand-written)_

- A **package** is a directory of `.ttrm` files sharing a `package <name>` header, with a
  `modeler.toml` manifest at its root. Packages are the unit of import and versioning.
- An **`area`** is a subject area that spans packages — `def area <name> { packages: […],
  entities: […] }`. Use it to name a business domain that cuts across package boundaries.

## Bindings _(hand-written)_

Bindings keep the `er` meaning anchored to the `db` truth without repeating it. `er2db_entity`,
`er2db_attribute` and `er2db_relation` map logical constructs to their physical targets; an inline
`binding:` shorthand on an entity/attribute/relation covers the common case. This is what lets the
`db` mirror be regenerated while the `er` model stays hand-owned.

## Naming, search and the lexicon _(from grammar)_

Entities and attributes carry the vocabulary the understanding layer resolves against:

- **`aliases: [ … ]`** — alternate names for the concept.
- **`search { searchable, fuzzy, keywords { <locale>: [ … ] }, patterns [ … ] }`** — how the fuzzy
  and search doors find this field, including localized keywords.
- **`lexicon { … }`** — inline sugar for terms, patterns and examples the resolution layer uses.
- **`valueLabels { "<code>": { <locale>: "…" } }`** — human labels for coded values.

## Queries _(from grammar)_

- **Named queries** — `def query <name> { … }` with a SQL/DSL template and a parameter list;
  surfaced through `list_queries` and runnable through the query door.
- **Pattern queries** — parameterized query shapes an agent can bind and run.

## Governance — roles, not a `security` block

!!! warning "There is no `security {}` construct"
    Governance in TTR-M is expressed through the **`cnc` layer** — `role` and `er2cnc_role`
    definitions (fact/dimension roles and role bindings) — **not** a single `security` keyword. The
    row-level filters and column masks are enforced downstream by the validator and reported in
    `pipelineWarnings` on every governed answer. Model the roles; the platform enforces them.

## Worlds and composition _(from grammar)_

`def world <name> { … }` (with `engine`/`executor`/`storage` nouns) describes a composition of
packages into a deployable whole — the model as the deployment artifact, named.

## Types _(from grammar)_

The canonical type tokens (`int`, `bigint`, `text`, `decimal`, `float`, `bool`, `date`, `time`,
`datetime`, …) are what `db` columns and `er` attributes carry; `ttr import-schema` normalizes SQL
types onto them. The full type table is generated from the grammar.

---

_For a guided path into the language rather than a lookup, start with
[model your first three tables](first-three-tables.md); for how the layers relate, see
[the layers](layers.md)._
