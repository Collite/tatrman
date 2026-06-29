# Queries and procedures

Beyond tables and entities, TTR can record the **queries** and **stored procedures** that belong to a model. These let you capture reusable SQL, parameterized lookups, and procedure signatures as first-class, documented objects rather than as loose strings scattered around an application.

Both are **db-layer objects**: a `def query` and a `def procedure` live in the `db` model, in a schema (default `dbo`), just like a table — so the tables a query references resolve in that same schema. They sit in a normal `db` file:

```ttr
model db schema dbo
```

> Earlier versions of TTR had a separate `query` perspective. As of grammar 4.0, queries (and drill maps) fold into the `db` model — there is no separate `query` schema to select. A query is addressed like any db object, under its schema (`db.dbo.<name>`).

## Queries

A `query` names a piece of query logic, in a stated language, with optional parameters:

```ttr
def query top_products_by_revenue {
    description: "Best-selling products by total line revenue.",
    language: SQL,
    parameters: [
        { name: from_date, type: date, label: "From date" },
        { name: to_date,   type: date, label: "To date" }
    ],
    sourceText: """
        SELECT p.NAME, SUM(ol.QUANTITY * ol.UNIT_PRICE) AS revenue
        FROM ORDER_LINE ol
        JOIN PRODUCT p ON p.PRODUCT_ID = ol.PRODUCT_ID
        JOIN ORDERS o ON o.ORDER_ID = ol.ORDER_ID
        WHERE o.ORDER_DATE BETWEEN :from_date AND :to_date
        GROUP BY p.NAME
        ORDER BY revenue DESC
    """
}
```

- **`language`** — one of `SQL`, `TRANSFORMATION_DSL`, `DATAFRAME_DSL`, or `REL_NODE`. It tells consumers how to interpret `sourceText`.
- **`parameters`** — a list of `{ name, type, label }` objects. `name` is a bare identifier, `type` uses the standard type system, and `label` is a display string.
- **`sourceText`** — the query body, usually a triple-quoted string. TTR stores it verbatim; it does not parse or validate the SQL.

The value of declaring a query in TTR is documentation and discoverability: it appears in the symbol outline, carries a description, states its parameters explicitly, and sits in version control alongside the model it queries.

## Procedures

A `procedure` captures a stored procedure's **signature** — its parameters (with direction) and its result columns:

```ttr
def procedure close_order {
    description: "Marks an order closed and returns the affected row count.",
    parameters: [
        { name: order_id, type: int, label: "Order ID", direction: IN },
        { name: closed_count, type: int, label: "Rows closed", direction: OUT }
    ],
    resultColumns: [
        def column ORDER_ID { type: int },
        def column STATUS   { type: int }
    ]
}
```

- **`parameters`** — like a query's, plus a `direction` of `IN`, `OUT`, or `INOUT`.
- **`resultColumns`** — the shape of what the procedure returns, written as inline `def column` definitions (same kind as a table's columns).

As with views and queries, TTR records the *contract*, not the implementation body. That is enough for the model to document what the procedure expects and returns, and for tooling to surface it.

## When to use these

Reach for `query` and `procedure` when a piece of SQL is part of the model's vocabulary — a canonical metric, a standard lookup, a procedure other systems call — and you want it described, named, and versioned in one place. For one-off, application-private SQL, there is no need to model it. Like the rest of TTR, these are descriptions meant to be read and checked, not executed by TTR itself.

The next page leaves the language behind and covers the editor — [the VS Code experience](13-vscode.md) — where all of this becomes interactive.
