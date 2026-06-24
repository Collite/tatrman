# Reference

A lookup chapter, not a tutorial. It collects the definition kinds, the type system, the property vocabulary, the diagnostic catalog, a grammar cheat-sheet, and one complete worked model.

## Schemas

| Schema | Purpose | Typical namespace |
|---|---|---|
| `db` | Physical database — tables, columns, keys, indexes, FKs | `dbo` |
| `er` | Conceptual — entities, attributes, relations | `entity` |
| `map` | Correspondence between `er` and `db` | `er2db` |
| `cnc` | Semantic roles (fact, dimension, …) | `role` |
| `query` | Queries and procedures | — |

A file selects a schema with `schema <code> [namespace <id>]`. A `.ttrg` graph names its schema with `schema: <code>`.

## File kinds

| Extension | Holds | Top-level block |
|---|---|---|
| `.ttrm` | model definitions | `def …` (many) |
| `.ttrg` | one curated diagram ([Graphs](11-graphs.md)) | `graph <id> { … }` |

The two are mutually exclusive per file; mixing them is a `wrong-file-kind` error. An **area** — a reusable model slice ([Areas](10-packages-and-imports.md#areas)) — is a plain `def area` and lives in an ordinary `.ttrm` file, so it needs no file kind of its own. An area only *references* packages and entities; the metadata loader does not load it.

## Definition kinds

| Kind | Schema | Key properties |
|---|---|---|
| `model` | any | `version`, `description`, `tags` |
| `table` | `db` | `primaryKey`, `columns`, `indices`, `constraints`, `description` |
| `view` | `db` | `columns`, `definitionSql`, `description` |
| `column` | `db` | `type`, `optional`, `isKey`, `indexed`, `description` |
| `index` | `db` | `type` (`primary`/`secondary`/`ordered`/`btree`/`fulltext`), `columns` |
| `constraint` | `db` | `type` (`unique`/`not_null`), `columns` |
| `fk` | `db` | `from`, `to` (lists of column paths), `description` |
| `procedure` | `query` | `parameters` (with `direction`), `resultColumns` |
| `entity` | `er` | `nameAttribute`, `codeAttribute`, `attributes`, `roles`, `displayLabel`, `aliases`, `labelPlural`, `mapping` |
| `attribute` | `er` | `type`, `isKey`, `optional`, `valueLabels`, `displayLabel`, `mapping` |
| `relation` | `er` | `from`, `to`, `cardinality`, `join`, `mapping` |
| `er2db_entity` | `map` | `entity`, `target` (`{ table }`), `whereFilter` |
| `er2db_attribute` | `map` | `attribute`, `target` (`{ column }`) |
| `er2db_relation` | `map` | `relation`, `fk` |
| `query` | `query` | `language`, `parameters`, `sourceText` |
| `role` | `cnc` | `label` (localized), `description` |
| `er2cnc_role` | `map`/`cnc` | `entity`, `role` |
| `drill_map` | any | `from`, `to`, `args`, `display`, `override` |

Every kind also accepts `description` and `tags`. Many `er`/`db` kinds also accept a `search { … }` block.

## Type system

A `type` is a simple name or a structured object `{ type: <name>, length: <n>, precision: <n> }`.

| TTR type | Closest SQL | Notes |
|---|---|---|
| `int`, `integer` | `INT` | |
| `float`, `double` | `FLOAT` / `DOUBLE` | |
| `decimal`, `number` | `DECIMAL(length, precision)` | use the structured form for scale |
| `bool`, `boolean` | `BIT` / `BOOLEAN` | |
| `text`, `string` | `TEXT` / `NVARCHAR(MAX)` | |
| `char`, `varchar` | `CHAR(n)` / `VARCHAR(n)` | `length` gives `n` |
| `date` | `DATE` | |
| `datetime`, `timestamp` | `DATETIME` / `TIMESTAMP` | |
| `object`, `list` | — | structured/composite values |

```ttr
type: int
type: { type: varchar, length: 40 }
type: { type: decimal, length: 19, precision: 2 }
```

## Common properties

| Property | Applies to | Value |
|---|---|---|
| `description` | all | string or `"""triple-quoted"""` |
| `tags` | all | list of strings |
| `type` | column, attribute, parameter | type value |
| `optional` | column, attribute | boolean (default `false`) |
| `isKey` | column, attribute | boolean |
| `indexed` | column | boolean |
| `primaryKey` | table | list of column-name strings |
| `columns` | table, view | inline `def column` list |
| `indices` / `constraints` | table | inline `def index` / `def constraint` list |
| `from` / `to` | fk, relation | column paths (fk) or entity paths (relation) |
| `cardinality` | relation | `{ from: "...", to: "..." }` |
| `join` | relation | `[{ from: <attr>, to: <attr> }]` |
| `nameAttribute` / `codeAttribute` | entity | attribute name |
| `roles` | entity | list of role names |
| `displayLabel` | entity, attribute | localized string `{ en: "...", cs: "..." }` |
| `valueLabels` | attribute | `{ "1": { en: "..." }, … }` |
| `aliases` | entity | list of strings |
| `target` | er2db_entity/attribute | `{ table: … }` or `{ column: … }` |
| `whereFilter` | er2db_entity | object filter, e.g. `{ active: 1 }` |
| `language` | query | `SQL` / `TRANSFORMATION_DSL` / `DATAFRAME_DSL` / `REL_NODE` |
| `sourceText` | query | string |
| `parameters` | query, procedure | `[{ name, type, label, direction? }]` |
| `direction` | procedure parameter | `IN` / `OUT` / `INOUT` |

Cardinality multiplicities: `1`, `0..1`, `1..*`, `0..*`, `*`, `1..n`, `n`.

## Diagnostic catalog

| Code | Severity | Meaning / fix |
|---|---|---|
| `ttr/unresolved-reference` | Error | A reference points at nothing. Check the path and spelling. |
| `ttr/unimported-reference` | Error | Bare reference to a package you did not `import`. Add the import or use the full path. |
| `ttr/ambiguous-reference` | Error | A bare name two wildcard imports both provide. Qualify it. |
| `ttr/package-declaration-mismatch` | Warning¹ | Declared `package` ≠ folder path (leaf segment). Rename the folder or the declaration. |
| `ttr/package-prefix-divergence` | Warning¹ | A non-leaf segment of the declaration diverges from the folder — orphans the file from path resolution. |
| `ttr/wrong-file-kind` | Error | A `def`/`graph` in the wrong file kind (see [File kinds](#file-kinds)). Move it. |
| `ttr/unused-import` | Warning | Import nothing uses. Delete it. |
| `ttr/duplicate-import` | Warning | Same import twice. Remove the duplicate. |
| `ttr/wildcard-with-no-matches` | Warning | `import x.*` where `x` has no definitions. |
| `ttr/circular-package-dependency` | Warning | A imports B and B imports A. Break the cycle. |
| `ttr/graph-object-not-found` | Warning | A `.ttrg` lists a qname that no longer resolves. Update the graph. |
| `ttr/domain-member-not-found` | Warning | A `def area` `packages:`/`entities:` member resolves to nothing. |
| `ttr/domain-empty` | Warning | A `def area` with no members. |
| `ttr/duplicate-domain` | Error | Two `def area` definitions declare the same area name. |
| `ttr/domain-redundant-member` | Info | An `entities:` entry already covered by a recursive `packages:` member. |
| `ttr/missing-package-declaration` | Info | File is in the default package. Add a `package` line. |

¹ Package-mismatch severities are set by `modeler.toml [packages] layout` — `flexible` (default) reports `package-declaration-mismatch` as a Warning, `strict` as an Error, `off` not at all; `package-prefix-divergence` is a Warning under `flexible`/`off` and an Error under `strict` (never silenced). See [Packages, imports, and areas](10-packages-and-imports.md#packages-and-folders).

(Plus per-kind validation, e.g. duplicate inline-and-`map` mappings for one attribute.)

## Grammar cheat-sheet

```
file        : package? import* (schema | graph)? definition*
package     : 'package' qualifiedName
import      : 'import' qualifiedName ('.' '*')?
schema      : 'schema' (db|er|map|cnc|query) ('namespace' id)?
definition  : 'def' kind id '{' property* '}'
graph       : 'graph' id '{' graphProperty* '}'        // .ttrg only
// 'area' is one of the def kinds — a plain def in any .ttrm file:
//   def area <id> { (description | tags | packages | entities)* }
areaProp    : ('packages'|'entities') ':' '[' path* ']' | description | tags

property    : key (':' | '=') value
value       : string | number | boolean | null | id | list | object
list        : '[' (value (',' value)*)? ','? ']'
object      : '{' (entry (','? entry)*)? ','? '}'
string      : "..."  |  """ ... """
localized   : '{' (langTag (':'|'=') string)* '}'   // { en: "...", cs: "..." }
path        : id ('.' id)*                           // er.entity.customer.id
```

Commas between properties are optional; trailing commas are allowed; `//` and `/* */` are comments.

## A complete worked model

The full retail example is under [`examples/retail/`](examples/retail/). One self-contained package — `shop.catalog` — shows all three core schemas working together:

```ttr
// shop/catalog/category.ttrm
package shop.catalog
schema er namespace entity

def entity category {
    description: "A grouping of products, e.g. Beverages or Stationery.",
    nameAttribute: name,
    codeAttribute: code,
    roles: [dimension],
    attributes: [
        def attribute id   { type: int, isKey: true },
        def attribute code { type: text, optional: true },
        def attribute name { type: text, optional: true }
    ]
}
```

```ttr
// shop/catalog/product.ttrm
package shop.catalog
schema er namespace entity

def entity product {
    description: "A sellable item in the catalog.",
    nameAttribute: name,
    codeAttribute: sku,
    roles: [dimension],
    attributes: [
        def attribute id          { type: int, isKey: true },
        def attribute sku         { type: text, optional: true },
        def attribute name        { type: text, optional: true },
        def attribute unit_price  { type: { type: decimal, length: 19, precision: 2 }, optional: true },
        def attribute category_id { type: int, optional: true }
    ]
}

def relation product_category {
    from: er.entity.product,
    to: er.entity.category,
    cardinality: { from: "0..*", to: "1" },
    join: [{ from: er.entity.product.category_id, to: er.entity.category.id }]
}
```

```ttr
// shop/catalog/db.ttrm
package shop.catalog
schema db namespace dbo

def table CATEGORY {
    primaryKey: ["CATEGORY_ID"],
    columns: [
        def column CATEGORY_ID { type: int, isKey: true },
        def column CODE        { type: text, optional: true, indexed: true },
        def column NAME        { type: text, optional: true }
    ],
    constraints: [ def constraint UQ_CATEGORY_CODE { type: unique, columns: ["CODE"] } ]
}

def table PRODUCT {
    primaryKey: ["PRODUCT_ID"],
    columns: [
        def column PRODUCT_ID  { type: int, isKey: true },
        def column SKU         { type: text, optional: true, indexed: true },
        def column NAME        { type: text, optional: true },
        def column UNIT_PRICE  { type: { type: decimal, length: 19, precision: 2 }, optional: true },
        def column CATEGORY_ID { type: int, optional: true, indexed: true }
    ],
    constraints: [ def constraint UQ_PRODUCT_SKU { type: unique, columns: ["SKU"] } ]
}

def fk fk_product_category {
    from: [db.dbo.PRODUCT.CATEGORY_ID],
    to:   [db.dbo.CATEGORY.CATEGORY_ID]
}
```

```ttr
// shop/catalog/map.ttrm
package shop.catalog
schema binding

def er2db_entity category { entity: er.entity.category, target: { table: db.dbo.CATEGORY } }
def er2db_entity product  { entity: er.entity.product,  target: { table: db.dbo.PRODUCT } }

def er2db_attribute category.id   { attribute: er.entity.category.id,   target: { column: db.dbo.CATEGORY.CATEGORY_ID } }
def er2db_attribute category.code { attribute: er.entity.category.code, target: { column: db.dbo.CATEGORY.CODE } }
def er2db_attribute category.name { attribute: er.entity.category.name, target: { column: db.dbo.CATEGORY.NAME } }

def er2db_attribute product.id          { attribute: er.entity.product.id,          target: { column: db.dbo.PRODUCT.PRODUCT_ID } }
def er2db_attribute product.sku         { attribute: er.entity.product.sku,         target: { column: db.dbo.PRODUCT.SKU } }
def er2db_attribute product.name        { attribute: er.entity.product.name,        target: { column: db.dbo.PRODUCT.NAME } }
def er2db_attribute product.unit_price  { attribute: er.entity.product.unit_price,  target: { column: db.dbo.PRODUCT.UNIT_PRICE } }
def er2db_attribute product.category_id { attribute: er.entity.product.category_id, target: { column: db.dbo.PRODUCT.CATEGORY_ID } }

def er2db_relation product_category {
    relation: er.entity.product_category,
    fk: db.dbo.fk_product_category
}
```

The `shop.sales` package adds `customer`, `order`, and `order_line`, plus a cross-package relation and foreign key into `shop.catalog`. See the [`examples/retail/`](examples/retail/) folder for the complete set and the `graphs/sales_er.ttrg` diagram.
