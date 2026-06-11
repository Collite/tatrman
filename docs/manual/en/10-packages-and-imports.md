# Packages and imports

A real model outgrows a single file. TTR organizes larger models into **packages** — folders of related definitions — and lets one package reference another through **imports**. This page explains the project layout, how names are formed, and the rules for resolving a reference.

## The project root: `modeler.toml`

A project is a folder tree with a `modeler.toml` at the top. That file marks the **root**; everything beneath it is part of the model. A minimal manifest:

```toml
[project]
name = "retail-shop"

[language]
preferred = "en"

[schemas]
declared = ["db", "er", "map"]
namespaces = { db = "dbo", er = "entity", map = "er2db" }

[stock]
load = ["cnc-roles"]

[lint]
strict = false
require-descriptions = false
```

- **`[schemas] declared`** lists the schemas the project uses, and `namespaces` sets the default namespace for each — why you write `schema db namespace dbo` and the objects land at `db.dbo.…`.
- **`[stock] load`** pulls in standard vocabularies; `cnc-roles` gives you `fact`, `dimension`, and the rest (see [CNC roles](09-cnc-roles.md)).
- **`[lint]`** tunes how strict the checker is — for example, whether every object must have a `description`. This is the legacy knob; for per-rule control use a `.ttrlint.toml` (below), which takes precedence.

## Linting & formatting

Modeler ships a configurable **linter** and a **formatter**.

### `.ttrlint.toml`

Drop a `.ttrlint.toml` beside `modeler.toml` to control individual rules. Absent → every rule runs at its default. Precedence (low → high): a rule's default → the `extends` preset → `[categories]` → `[rules]`.

```toml
extends = "recommended"        # recommended | strict | all | none

[rules]                        # per-rule severity: error | warning | info | off
missing-description = "warning"
unresolved-reference = "error"

[categories]                   # per-category severity (a [rules] entry wins)
style = "off"

[cli]
fail-on = "error"              # CI fails (exit 1) on diagnostics at/above this

[fix]
apply = "safe"                 # which fixes `ttr lint --fix` applies
```

**Presets:** `recommended` (defaults, but `missing-description` off), `strict` (`recommended` + `unresolved-reference = error` + `missing-description = warning`), `all` (everything `error`), `none` (everything `off` except *correctness* rules).

**Correctness floor.** Rules that describe a model that won't load (`table-no-columns`, `column-missing-type`, `duplicate-definition`, …) can be *raised* but never lowered below `error`, and they can't be suppressed.

**Rule ids** are kebab-case with no `ttr/` prefix — e.g. `unused-import`, `unresolved-reference`, `fuzzy-without-searchable`, `graph-name-mismatch`. They map onto the `ttr/*` diagnostic codes (see [`docs/v1/design/diagnostics.md`](../../v1/design/diagnostics.md)). `modeler.toml [lint]` is still honoured as a fallback when no `.ttrlint.toml` exists; if both are present, `.ttrlint.toml` wins.

### Inline suppression

Silence a rule on a line or range with a comment directive:

```ttr
// ttr-disable-next-line unused-import
import billing.products.er.entity.produkt

def table t { columns: [ ... ] } // ttr-disable-line missing-description

// ttr-disable graph-name-mismatch
…
// ttr-enable graph-name-mismatch
```

Omit the rule ids to suppress everything on that line/range; `// ttr-disable-file` covers the whole file. Correctness rules cannot be suppressed.

### Formatter

`ttr fmt <path>` prints canonical layout (use `--write` to rewrite in place, `--check` to fail CI on unformatted files). It preserves comments and is idempotent.

## Package = directory

A file's package is its folder path beneath the root, dotted. In the retail model:

```
retail-shop/
  modeler.toml
  shop/
    catalog/
      category.ttr        → package shop.catalog
      product.ttr         → package shop.catalog
      db.ttr              → package shop.catalog
      map.ttr             → package shop.catalog
    sales/
      customer.ttr        → package shop.sales
      order.ttr           → package shop.sales
      order_line.ttr      → package shop.sales
      db.ttr              → package shop.sales
      map.ttr             → package shop.sales
  graphs/
    sales_er.ttrg
```

Each file declares the package it belongs to as its first line, and it must match the folder:

```ttr
package shop.catalog

schema er namespace entity

def entity product { … }
```

If the declared package disagrees with the directory, that is a `package-declaration-mismatch` error — the tooling keeps the two in sync deliberately. Files at the root with no `package` line live in the *default package*, which is allowed but discouraged for anything beyond a quick sketch.

## Qualified names, decoded

Every definition has a fully-qualified name (a *qname*) built from its location:

```
shop.catalog . er . entity . product
└── package ──┘  │     │        └── definition name
                 │     └── namespace
                 └── schema
```

For nested objects, append the sub-name: `shop.catalog.er.entity.product.sku` is the `sku` attribute of the `product` entity. Physical paths follow the same rule: `shop.sales.db.dbo.ORDERS.ORDER_ID`.

A fully-qualified name **always works**, anywhere, with no import. That is why every cross-package reference in the examples can be written out in full.

## Referring to other definitions

Inside one package you never need the package prefix — definitions in the same package see each other by their shorter `schema.namespace.name` path:

```ttr
// in package shop.sales
def relation order_customer {
    from: er.entity.order,        // same package — no prefix needed
    to: er.entity.customer,
    join: [{ from: er.entity.order.customer_id, to: er.entity.customer.id }]
}
```

To reach *another* package, you have two options. Write the full path:

```ttr
// in package shop.sales, referring to shop.catalog
to: shop.catalog.er.entity.product
```

…or `import` it once at the top of the file and then use the short path:

```ttr
package shop.sales

import shop.catalog.er.entity.product

schema er namespace entity

def relation line_product {
    from: er.entity.order_line,
    to: er.entity.product,        // resolves via the import
    join: [{ from: er.entity.order_line.product_id, to: er.entity.product.id }]
}
```

## Import forms

There are two:

- **Named import** — `import shop.catalog.er.entity.product` brings exactly one definition into scope under its short path.
- **Wildcard import** — `import shop.catalog.*` brings every top-level definition of that package into scope.

Wildcards are **not recursive**: `import shop.*` does not include `shop.catalog`. Import each package you actually use. The `cnc` stock vocabulary is auto-imported, so roles need no import.

## Resolution order

When the checker sees a reference, it tries, in order:

1. **Lexical scope** — an attribute inside the same entity, a column inside the same table.
2. **Same package** — any definition in the file's own package.
3. **Named imports** — paths brought in by `import a.b.c.name`.
4. **Wildcard imports** — paths brought in by `import a.b.*`.
5. **Auto-imports** — the `cnc.*` roles.
6. **Fully-qualified name** — an explicit, complete path.

If none match, you get an `unresolved-reference`. If a bare name matches definitions in two different wildcard-imported packages, that is an `ambiguous-reference` — qualify it to disambiguate.

## Import-related diagnostics

The tooling keeps imports honest, which plain text includes never did:

| Diagnostic | Meaning |
|---|---|
| `unimported-reference` | A bare reference to a package you did not import. |
| `ambiguous-reference` | A bare name two wildcard imports both provide. |
| `unused-import` | An import nothing in the file uses — safe to delete. |
| `duplicate-import` | The same import written twice. |
| `wildcard-with-no-matches` | `import x.*` where `x` has no definitions. |
| `circular-package-dependency` | Package A imports B and B imports A. |

These keep a multi-package model navigable: the imports at the top of a file are an accurate, checked list of what it depends on.

The next page covers `.ttrg` files, which use these qualified names to assemble [curated diagrams](11-graphs.md).
