# YAML vs TTR

If your team described models in YAML before TTR, this page maps the old shapes onto the new ones. If you have never used the YAML models, skip to [Your first model](04-your-first-model.md).

## The YAML you know

A legacy model file listed entities, and inside each entity, columns that carried *both* the business name and the physical database column. Relationships were listed separately, each with a literal SQL `join` string:

```yaml
entities:
  customer:
    table: "CUSTOMER"
    entity_type: master
    name_column: "full_name"
    code_column: "email"
    description: "A person or organization that places orders."
    columns:
      - name: "id"
        column: "CUSTOMER_ID"
        type: "int"
        primary_key: true
        description: "Unique customer identifier"
      - name: "email"
        column: "EMAIL"
        type: "varchar(255)"
        searchable: true
      - name: "full_name"
        column: "FULL_NAME"
        type: "varchar(255)"

  order:
    table: "ORDERS"
    entity_type: transaction
    columns:
      - name: "id"
        column: "ORDER_ID"
        type: "int"
        primary_key: true
      - name: "customer_id"
        column: "CUSTOMER_ID"
        type: "int"

relationships:
  order_customer:
    from: "order"
    to: "customer"
    type: "many-to-one"
    join: "ORDERS.CUSTOMER_ID = CUSTOMER.CUSTOMER_ID"
    description: "Each order is placed by one customer."
```

Notice three things, because they are exactly what changes in TTR:

1. The **business name and the physical column live on the same line** (`name` + `column`). The mapping is implicit and inline.
2. The **table lives on the entity** (`table: "CUSTOMER"`).
3. The **relationship is a SQL string** (`join: "..."`) that nothing checks.

## The same model in TTR

TTR pulls those three things into named, checkable pieces. The business side becomes an `er` entity, the physical side becomes a `db` table, and the correspondence becomes explicit `map` definitions.

```ttr
// er — the business view
schema er namespace entity

def entity customer {
    description: "A person or organization that places orders.",
    nameAttribute: full_name,
    codeAttribute: email,
    roles: [master],
    attributes: [
        def attribute id        { type: int, isKey: true },
        def attribute email     { type: text, optional: true },
        def attribute full_name { type: text, optional: true }
    ]
}
```

```ttr
// db — the physical view
schema db namespace dbo

def table CUSTOMER {
    primaryKey: ["CUSTOMER_ID"],
    columns: [
        def column CUSTOMER_ID { type: int, isKey: true },
        def column EMAIL       { type: text, optional: true },
        def column FULL_NAME   { type: text, optional: true }
    ]
}
```

```ttr
// map — the correspondence, now explicit and checked
schema binding

def er2db_entity customer { entity: er.entity.customer, target: { table: db.dbo.CUSTOMER } }
def er2db_attribute customer.id        { attribute: er.entity.customer.id,        target: { column: db.dbo.CUSTOMER.CUSTOMER_ID } }
def er2db_attribute customer.email     { attribute: er.entity.customer.email,     target: { column: db.dbo.CUSTOMER.EMAIL } }
def er2db_attribute customer.full_name { attribute: er.entity.customer.full_name, target: { column: db.dbo.CUSTOMER.FULL_NAME } }
```

And the relationship stops being an opaque SQL string. It becomes a `relation` with a real cardinality and a structured join over *attributes*, which the tooling resolves:

```ttr
def relation order_customer {
    description: "Each order is placed by one customer.",
    from: er.entity.order,
    to: er.entity.customer,
    cardinality: { from: "0..*", to: "1" },
    join: [{ from: er.entity.order.customer_id, to: er.entity.customer.id }]
}
```

## "But that's more typing"

It is, for the smallest models. Two things to keep in mind.

First, the split buys you correctness. In the YAML, nothing notices when `CUSTOMER.CUSTOMER_ID` is renamed, when two entities claim the same table, or when a `join` references a dropped column. In TTR every one of those is a diagnostic in your editor before you commit. That is the whole point — see [The VS Code experience](13-vscode.md).

Second, TTR has a **short form** for the common case where one entity maps cleanly to one table, one attribute to one column. You can keep the mapping inline on the attribute, much like YAML did, instead of writing separate `map` definitions:

```ttr
schema er namespace entity

def entity customer {
    nameAttribute: full_name,
    binding: { target: { table: db.dbo.CUSTOMER } },
    attributes: [
        def attribute id        { type: int, isKey: true, binding: { target: { column: db.dbo.CUSTOMER.CUSTOMER_ID } } },
        def attribute email     { type: text, optional: true, binding: EMAIL },
        def attribute full_name { type: text, optional: true, binding: FULL_NAME }
    ]
}
```

This is the closest analogue to the YAML style: the table sits on the entity, and each attribute names its column right there. You reach for the separate `map` schema only when the mapping is more than one-to-one. Both forms are covered in [Mapping](08-mapping.md), which deliberately starts with this simple inline case before showing the general one.

## Field-by-field translation

| Legacy YAML | TTR |
|---|---|
| `entities:` (top-level) | one `def entity` per entity, in the `er` schema |
| `table: "CUSTOMER"` | `er2db_entity … target: { table: db.dbo.CUSTOMER }` (or inline `mapping`) |
| `entity_type: master` | `roles: [master]` — see [CNC roles](09-cnc-roles.md) |
| `name_column:` / `code_column:` | `nameAttribute:` / `codeAttribute:` |
| column `name:` | the attribute's name (`def attribute id { … }`) |
| column `column:` | `er2db_attribute … target: { column: … }` (or inline `mapping`) |
| column `type:` | `type:` on the attribute and/or column |
| `primary_key: true` | `isKey: true` |
| `searchable: true` | `search { searchable: true }` — see [Reference](14-reference.md) |
| `relationships:` with `join: "A.x = B.y"` | `def relation` with structured `join: [{ from: …, to: … }]` |
| `aliases:` | `aliases: [...]` on the entity |

The information is the same. What changes is that TTR knows what each piece *means*, so the editor can check it, navigate it, and draw it.
