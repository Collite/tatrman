# Syntax basics

This page explains the punctuation and structure shared by every TTR file, so the later chapters can focus on meaning. None of it is surprising; it is closer to JSON than to SQL.

## The shape of a file

A `.ttrm` file has up to four kinds of line, in this order:

```ttr
package shop.catalog                 // 1. optional: which package this file belongs to
import shop.sales.er.entity.customer // 2. optional: cross-package references
schema er namespace entity           // 3. optional: the schema (perspective) for what follows
def entity product { … }             // 4. zero or more definitions
```

`package` and `import` matter only once a model spans multiple folders — see [Packages and imports](10-packages-and-imports.md). For a single-folder model you often write just the `schema` line and your definitions.

## Definitions

Every object is introduced by `def`, followed by its **kind**, its **name**, and a brace-enclosed body of properties:

```ttr
def <kind> <name> { <properties> }
```

```ttr
def column UNIT_PRICE { type: decimal, optional: true }
```

The kinds are fixed by the language — `table`, `column`, `entity`, `attribute`, `relation`, `fk`, and so on. The full list is in the [Reference](14-reference.md). The name is an identifier you choose.

## Properties

A body is a comma-separated list of `key: value` pairs. The separator can be a colon or an equals sign — both are valid, pick one and be consistent:

```ttr
def column id { type: int, isKey: true }     // colon style
def column id { type = int, isKey = true }    // equals style — identical meaning
```

Trailing commas are allowed everywhere, which keeps diffs clean:

```ttr
columns: [
    def column id   { type: int },
    def column name { type: text },   // trailing comma is fine
]
```

Whitespace and line breaks are insignificant. The compact one-liners and the indented multi-line forms in this manual parse identically.

## Values

Property values take a handful of forms:

| Form | Example |
|---|---|
| String | `"Active customers"` |
| Multi-line string | `"""First line.<br>Second line."""` (triple-quoted) |
| Number | `42`, `3.14`, `1e-5` |
| Boolean | `true`, `false` |
| Null | `null` |
| Identifier | `int`, `full_name`, `er.entity.customer` |
| List | `["a", "b"]` or `[db.dbo.PRODUCT.SKU]` |
| Object | `{ from: "1", to: "0..*" }` |

Identifiers can be **dotted paths** — `er.entity.customer.id` — which is how TTR refers to other definitions. A dotted path is read segment by segment: schema, namespace, object, sub-object. References are checked: point at something that does not exist and the editor underlines it.

Strings can be **localized** by using an object whose keys are language tags:

```ttr
displayLabel: { cs: "Zákazník", en: "Customer" }
```

This is how TTR carries Czech and English labels in one model — a single source of truth instead of parallel files.

## Types

A `type` value is either a simple type name or an object that adds length/precision:

```ttr
type: int
type: text
type: { type: varchar, length: 40 }
type: { type: decimal, length: 19, precision: 2 }
```

The built-in type names include `int`, `integer`, `float`, `double`, `decimal`, `number`, `bool`, `boolean`, `text`, `string`, `char`, `varchar`, `date`, `datetime`, `timestamp`, plus `object` and `list`. The full list and how they relate to SQL types is in the [Reference](14-reference.md).

## Inline vs. standalone definitions

Columns, attributes, indexes, constraints, and parameters can be written **inline** inside their parent (the usual style):

```ttr
def table PRODUCT {
    columns: [
        def column PRODUCT_ID { type: int, isKey: true },
        def column NAME       { type: text, optional: true }
    ]
}
```

A single inline definition does not need the brackets:

```ttr
columns: def column PRODUCT_ID { type: int, isKey: true }
```

Both forms mean the same thing. Inline is conventional and reads best.

## Comments

`//` starts a line comment; `/* … */` spans multiple lines. Use them to annotate intent — though a good `description` property is usually better, because the tooling surfaces descriptions on hover.

```ttr
def column SKU { type: text, description: "Stock-keeping unit code" } // unique per product
```

With the punctuation out of the way, the next pages cover what each schema actually lets you say — starting with the most SQL-like one, [the DB schema](06-db-schema.md).
