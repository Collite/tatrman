# Your first model

This page gets a working TTR file in front of you in a few minutes. We will model one physical table — nothing conceptual yet — so the whole thing stays close to SQL. Later pages add the conceptual and mapping layers.

## Create a project

A TTR project is just a folder with a manifest file named `modeler.toml` at its root. Create a folder and put this inside it:

```toml
# modeler.toml
[project]
name = "retail-shop"

[language]
preferred = "en"

[schemas]
declared = ["db", "er", "map"]
namespaces = { db = "dbo", er = "entity", map = "er2db" }

[stock]
load = ["cnc-roles"]
```

The manifest tells the tooling this folder is a model root, which schemas you intend to use, and the default namespace for each. `[stock] load = ["cnc-roles"]` makes the standard warehouse roles (`fact`, `dimension`, …) available. The manifest is covered fully in [Packages and imports](10-packages-and-imports.md); for now, copy it.

## Write a table

Create a file `catalog.ttr` next to the manifest:

```ttr
schema db namespace dbo

def table PRODUCT {
    description: "Physical table holding catalog products.",
    primaryKey: ["PRODUCT_ID"],
    columns: [
        def column PRODUCT_ID { type: int, isKey: true, description: "Unique product identifier" },
        def column SKU        { type: text, optional: true, indexed: true, description: "Stock-keeping unit code" },
        def column NAME       { type: text, optional: true, description: "Display name" },
        def column UNIT_PRICE { type: { type: decimal, length: 19, precision: 2 }, optional: true }
    ]
}
```

Read top to bottom:

- `schema db namespace dbo` — everything below is part of the **physical** view, in the `dbo` namespace. This line is the TTR equivalent of "these are real database objects in schema `dbo`."
- `def table PRODUCT { … }` — defines a table named `PRODUCT`. `def` introduces every definition in TTR; the word after it (`table`) is the *kind*.
- `primaryKey: ["PRODUCT_ID"]` — names the key column(s).
- Each `def column …` inside `columns: [ … ]` is a column. `type` is the data type; `optional: true` means nullable (columns are **not** nullable by default — the opposite of SQL); `isKey: true` marks a key column; `indexed: true` requests an index.
- `{ type: decimal, length: 19, precision: 2 }` is the long form of a type when you need precision — equivalent to `DECIMAL(19,2)`.

That is a complete, valid TTR file. Open the folder in VS Code with the TTR extension installed and you will get syntax highlighting, an outline of your table and columns, and live error checking as you type.

## Add a foreign key

Add a second table and a foreign key to the same file:

```ttr
def table CATEGORY {
    primaryKey: ["CATEGORY_ID"],
    columns: [
        def column CATEGORY_ID { type: int, isKey: true },
        def column NAME        { type: text, optional: true }
    ]
}

def fk fk_product_category {
    description: "Product references its parent category.",
    from: [db.dbo.PRODUCT.CATEGORY_ID],
    to: [db.dbo.CATEGORY.CATEGORY_ID]
}
```

For the foreign key to resolve, `PRODUCT` needs a `CATEGORY_ID` column — add one:

```ttr
def column CATEGORY_ID { type: int, optional: true, indexed: true }
```

A `def fk` names the relationship explicitly (unlike inline SQL `REFERENCES`) and points at columns by their **fully-qualified path**: `db.dbo.PRODUCT.CATEGORY_ID` reads as *schema `db`, namespace `dbo`, table `PRODUCT`, column `CATEGORY_ID`*. `from` and `to` are lists because a foreign key can span multiple columns. If you misspell a column here, the editor underlines it immediately — that is the resolver checking your reference.

## What you just learned

You wrote physical tables, keys, and a foreign key — the SQL-shaped part of TTR. You have not yet touched the conceptual (`er`) or mapping (`map`) layers, and that is fine: a `db`-only model is perfectly valid and already useful for diagrams and documentation.

From here:

- [Syntax basics](05-syntax-basics.md) explains the punctuation rules (`:` vs `=`, strings, lists, types) you have been copying.
- [The DB schema](06-db-schema.md) covers the rest of the physical kinds — views, indexes, constraints.
- [The ER schema](07-er-schema.md) introduces the conceptual side, which is where TTR starts to do things SQL cannot.
