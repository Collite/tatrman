# Packages, imports, and areas

A real model outgrows a single file. TTR organizes larger models into **packages** — folders of related definitions — and lets one package reference another through **imports**. Larger models also group packages into **areas** — named, reusable slices of the model that downstream tools load as a unit. This page explains the project layout, how names are formed, the rules for resolving a reference, and how areas scope what a consumer sees.

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

[packages]
root   = ""           # optional module-style prefix on every package; "" = none
layout = "flexible"   # how strictly a package declaration must match its folder

[lint]
strict = false
require-descriptions = false
```

- **`[schemas] declared`** lists the schemas the project uses, and `namespaces` sets the default namespace for each — why you write `schema db namespace dbo` and the objects land at `db.dbo.…`.
- **`[stock] load`** pulls in standard vocabularies; `cnc-roles` gives you `fact`, `dimension`, and the rest (see [CNC roles](09-cnc-roles.md)).
- **`[packages]`** tunes the package model: an optional `root` prefix and how strictly a `package` declaration must agree with its folder (`layout`). Both are optional and default to the values above — see [The root prefix and the no-cascade rule](#the-root-prefix-and-the-no-cascade-rule).
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

## Packages and folders

By convention a file's package is its folder path beneath the root, dotted. In the retail model:

```
retail-shop/
  modeler.toml
  shop/
    catalog/
      category.ttrm        → package shop.catalog
      product.ttrm         → package shop.catalog
      db.ttrm              → package shop.catalog
      map.ttrm             → package shop.catalog
    sales/
      customer.ttrm        → package shop.sales
      order.ttrm           → package shop.sales
      order_line.ttrm      → package shop.sales
      db.ttrm              → package shop.sales
      map.ttrm             → package shop.sales
    domains/
      sales_360.ttrm       → def area sales_360 (not a package)
  graphs/
    sales_er.ttrg
```

Each file declares the package it belongs to as its first line:

```ttr
package shop.catalog

schema er namespace entity

def entity product { … }
```

**The declaration wins.** The in-file `package` line is the source of truth for a file's package; the folder is a *checked convention*. If a file omits the `package` line, the package is derived from the folder path instead. A file at the root with no `package` line lives in the *default package* — allowed, but discouraged beyond a quick sketch.

When a declaration and its folder disagree, the tooling flags it, but the **severity is yours to choose** via `[packages] layout`:

| `layout` | A declaration that doesn't match its folder |
|---|---|
| `"flexible"` (default) | `package-declaration-mismatch`, a **warning** |
| `"strict"` | the same, an **error** |
| `"off"` | not reported |

A *leaf-only* override — same parent path, different last segment (folder `shop/catalog`, declared `shop.katalog`) — is the ordinary `package-declaration-mismatch`. But if a **non-leaf** segment diverges (folder `shop/catalog`, declared `warehouse.catalog`, or a different segment count), that earns the louder `package-prefix-divergence`: the file is effectively orphaned from anything resolving through its folder path. Prefix-divergence stays a warning even under `flexible` (an error under `strict`) and is never silently ignored. Treat the declaration override as an escape hatch — to rename a whole subtree, rename the folder.

### The root prefix and the no-cascade rule

`[packages] root` prepends a module-style prefix to every directory-derived package, Go-module style. With `root = "cz.dfpartner"`, the file at `shop/catalog/product.ttrm` derives the package `cz.dfpartner.shop.catalog`. The prefix is **elidable** in references: `shop.catalog.er.entity.product` and `cz.dfpartner.shop.catalog.er.entity.product` resolve to the same object, so you can keep writing the short form even after a root is configured. The default `root = ""` adds no prefix, and everything below reads exactly as written.

Derivation is **non-cascading**: each file's package comes only from *its own* declaration or *its own* folder path — never from a parent folder's declaration. Renaming one package's declaration does not silently re-home the packages nested beneath it; those still derive from `root` + their own path. This is the reason prefix-divergence is called out separately: it's the one case where an override would otherwise quietly detach a subtree.

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

## Areas

Packages organize the model for *authors*; **areas** organize it for *consumers*. An area is a named, curated grouping of packages (and, when you need finer grain, individual entities) — a reusable slice of the model that a downstream tool loads as a unit. A reporting agent might load the `sales_360` area rather than spelling out a list of packages in its own config; when the area grows, every consumer that references it picks up the change.

An area is **not** part of any qualified name — it never appears in a `db.…` or `er.…` path. It is a separate concern layered over packages: a package is the unit of *import resolution*; an area is the unit of *consumer scoping*.

### `def area`

An area is a plain definition — `def area <id> { … }` — that lives in an ordinary `.ttrm` model file. There is no separate file kind and no one-per-file rule: an `area` can sit alongside other `def`s in the same file, or in a file of its own. A common convention is a `domains/` folder of area-only files, named after the area:

```ttr
// shop/domains/sales_360.ttrm
def area sales_360 {
    description: "Everything a sales report touches",
    tags: ["sales", "reporting"],

    packages: [
        shop.sales,
        shop.catalog
    ],

    entities: [
        shop.inventory.er.entity.stock_level   // one entity from a package we don't want wholesale
    ]
}
```

- **`packages:`** lists whole packages. Membership is **recursive**: `shop.sales` pulls in `shop.sales` *and* every descendant (`shop.sales.returns`, `shop.sales.returns.rma`, …).
- **`entities:`** lists individual entity qnames to add on top — the "load just this one object from an otherwise-excluded package" case.
- `description` and `tags` are optional, exactly as on any other `def`.

> **Recursive vs. non-recursive — the one place they differ.** An area's `packages:` membership *is* recursive (`shop.sales` ⇒ the whole subtree). An `import shop.sales.*` is *not* (top-level definitions of `shop.sales` only). Same-looking subtree, deliberately different operations: imports resolve compile-time references; areas select a runtime scope. Don't conflate them.

### Where areas live, and who reads them

`def area` definitions are an **editor/registry concept**. The metadata model loader does **not** load them — they carry no `db`/`er` objects, only references to packages and entities that live elsewhere. Their job is to give downstream consumers (and the people configuring them) a named, validated handle on a slice of the model, with editor support for free: go-to-definition on a `packages:` or `entities:` member jumps to the real files, and a member that doesn't exist is flagged rather than silently mis-loaded.

Areas are plain `def`s, so they can live in any model file and need no dedicated file kind — there is no file-kind rule to satisfy when you write one.

### Area diagnostics

| Diagnostic | Severity | Meaning |
|---|---|---|
| `ttr/domain-member-not-found` | warning | A `packages:`/`entities:` member doesn't resolve to anything in the model. |
| `ttr/domain-empty` | warning | A `def area` with no members — it scopes nothing. |
| `ttr/duplicate-domain` | error | Two `def area` definitions declare the same area name. |
| `ttr/domain-redundant-member` | info | An `entities:` entry already covered by a recursive `packages:` member. |

The next page covers `.ttrg` files, which use these qualified names to assemble [curated diagrams](11-graphs.md).
