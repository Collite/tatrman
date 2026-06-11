# The DB schema

The `db` schema is the physical view: real tables, columns, keys, indexes, and foreign keys. It is the part of TTR closest to SQL DDL, so this page is mostly about naming the pieces.

Every file (or section) that defines physical objects opens with:

```ttr
schema db namespace dbo
```

The `namespace` is the database schema name in the SQL sense — `dbo`, `public`, `sales`, whatever your database uses. It becomes part of every object's path: `db.dbo.PRODUCT`.

## Tables and columns

```ttr
def table PRODUCT {
    description: "Physical table holding catalog products.",
    primaryKey: ["PRODUCT_ID"],
    columns: [
        def column PRODUCT_ID { type: int, isKey: true, description: "Unique product identifier" },
        def column SKU        { type: text, optional: true, indexed: true },
        def column NAME       { type: text, optional: true },
        def column UNIT_PRICE { type: { type: decimal, length: 19, precision: 2 }, optional: true },
        def column CATEGORY_ID { type: int, optional: true, indexed: true }
    ]
}
```

A `column` carries these properties:

- **`type`** — the data type, simple (`int`) or structured (`{ type: decimal, length: 19, precision: 2 }`).
- **`optional`** — `true` makes the column nullable. **Columns are NOT NULL by default**, the reverse of SQL convention, so you mark what is nullable rather than what is not.
- **`isKey`** — marks the column as part of the table's key. List the key column names in the table's `primaryKey` as well; `isKey` is the per-column flag.
- **`indexed`** — requests a single-column index without writing a separate `index` definition. Use it for the common "this column should be indexed" case.
- **`description`** — free text, shown on hover in the editor.

## Indexes

For multi-column indexes, or when you want to name an index, use `def index` inside the table's `indices` list:

```ttr
def table PRODUCT {
    primaryKey: ["PRODUCT_ID"],
    columns: [ … ],
    indices: [
        def index IX_PRODUCT_SKU      { columns: ["SKU"] },
        def index IX_PRODUCT_CATEGORY { columns: ["CATEGORY_ID"] }
    ]
}
```

An index's `columns` is a list of column-name strings, in order. Index `type` may be one of `primary`, `secondary`, `ordered`, `btree`, or `fulltext`; omit it for a plain index.

## Constraints

Table constraints go in the `constraints` list:

```ttr
constraints: [
    def constraint UQ_PRODUCT_SKU { type: unique, columns: ["SKU"] }
]
```

`type` is `unique` or `not_null`, and `columns` lists the affected columns. (Single-column NOT NULL is usually expressed with `optional: false` on the column, which is the default; use a `not_null` constraint when you want it named explicitly.)

## Foreign keys

A foreign key is a **standalone** definition, not nested in a table, because it relates two tables:

```ttr
def fk fk_product_category {
    description: "Product references its parent category.",
    from: [db.dbo.PRODUCT.CATEGORY_ID],
    to: [db.dbo.CATEGORY.CATEGORY_ID]
}
```

`from` and `to` are lists of column paths, so composite keys work — pair the columns positionally:

```ttr
def fk fk_line_order_composite {
    from: [db.dbo.ORDER_LINE.ORDER_ID, db.dbo.ORDER_LINE.LINE_NO],
    to:   [db.dbo.ORDERS.ORDER_ID,     db.dbo.ORDERS.LINE_NO]
}
```

Both endpoints are checked. If `db.dbo.CATEGORY.CATEGORY_ID` does not exist, the reference is flagged.

A foreign key can cross packages. In the retail model, an order line in `shop.sales` points at a product table in `shop.catalog`, so the target uses the full path:

```ttr
def fk fk_order_line_product {
    from: [db.dbo.ORDER_LINE.PRODUCT_ID],
    to:   [shop.catalog.db.dbo.PRODUCT.PRODUCT_ID]
}
```

See [Packages and imports](10-packages-and-imports.md) for how those longer paths work.

## Views

A `view` is like a table but backed by SQL rather than storage. Give it columns and a `definitionSql`:

```ttr
def view active_customers {
    description: "Customers that are not closed.",
    definitionSql: "SELECT * FROM CUSTOMER WHERE STATUS = 1",
    columns: [
        def column CUSTOMER_ID { type: int },
        def column FULL_NAME   { type: text, optional: true }
    ]
}
```

TTR does not parse or validate the SQL string — it is stored verbatim. The columns you declare are what the rest of the model (and the diagrams) see.

## A complete physical model

Put together, the physical side of the retail catalog looks like this:

```ttr
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

This is everything SQL would express with `CREATE TABLE` and `ALTER TABLE … ADD CONSTRAINT`. The next page lifts these tables up into business concepts with [the ER schema](07-er-schema.md).
