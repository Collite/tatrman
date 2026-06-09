# Mapping

Mapping is what makes TTR more than two disconnected pictures. It states, precisely and checkably, that *this* business entity is stored in *that* table, and *this* attribute is *that* column. We start with the simple case — which looks much like the legacy YAML — and then show the general form for when reality is messier.

## The idea

After the [ER](07-er-schema.md) and [DB](06-db-schema.md) pages, you have two parallel descriptions:

```
er.entity.customer            db.dbo.CUSTOMER
  ├─ id                         ├─ CUSTOMER_ID
  ├─ email                      ├─ EMAIL
  └─ full_name                  └─ FULL_NAME
```

Nothing yet connects `customer.id` to `CUSTOMER.CUSTOMER_ID`. Mapping draws those arrows. Once drawn, the tooling can tell you the mapping is complete, catch a column rename that breaks it, and follow it with "go to definition."

## The simple case: inline mapping

For the common situation — one entity, one table; one attribute, one column — you can keep the mapping **right on the entity and its attributes**, exactly where the legacy YAML kept it. The table sits on the entity; each attribute names its column.

```ttr
schema er namespace entity

def entity customer {
    nameAttribute: full_name,
    mapping: { target: { table: db.dbo.CUSTOMER } },
    attributes: [
        def attribute id        { type: int, isKey: true, mapping: { target: { column: db.dbo.CUSTOMER.CUSTOMER_ID } } },
        def attribute email     { type: text, optional: true, mapping: EMAIL },
        def attribute full_name { type: text, optional: true, mapping: FULL_NAME }
    ]
}
```

Two shorthands are at work here:

- On the entity, `mapping: { target: { table: db.dbo.CUSTOMER } }` says "this entity is stored in `CUSTOMER`."
- On an attribute, `mapping: EMAIL` is the shortest form: a bare column name, resolved against the entity's mapped table. `mapping: { target: { column: db.dbo.CUSTOMER.CUSTOMER_ID } }` is the explicit equivalent. Use the bare form when the column lives in the entity's own table; use the explicit form when you need a full path.

If you are migrating from YAML, this is the form to reach for first. It carries the same information in the same place, plus the editor now checks every column name.

## Mapping a relation inline

A relation can name the foreign key that implements it, inline:

```ttr
def relation order_customer {
    from: er.entity.order,
    to: er.entity.customer,
    cardinality: { from: "0..*", to: "1" },
    mapping: { fk: db.dbo.fk_orders_customer }
}
```

This ties the conceptual relationship to the physical `fk` that enforces it.

## The general case: the `map` schema

Inline mapping assumes a clean one-to-one correspondence. Real systems break that assumption constantly: an attribute comes from a joined table, two entities share one table distinguished by a flag, a physical column feeds two attributes. For those, TTR has a dedicated `map` schema whose definitions are **standalone**, so a mapping is no longer pinned to one entity or one attribute.

The same customer mapping, written the general way:

```ttr
schema map

def er2db_entity customer {
    entity: er.entity.customer,
    target: { table: db.dbo.CUSTOMER }
}

def er2db_attribute customer.id        { attribute: er.entity.customer.id,        target: { column: db.dbo.CUSTOMER.CUSTOMER_ID } }
def er2db_attribute customer.email     { attribute: er.entity.customer.email,     target: { column: db.dbo.CUSTOMER.EMAIL } }
def er2db_attribute customer.full_name { attribute: er.entity.customer.full_name, target: { column: db.dbo.CUSTOMER.FULL_NAME } }
```

There are three mapping kinds:

- **`er2db_entity`** — maps an entity to a table. `entity:` names the source, `target: { table: … }` the destination.
- **`er2db_attribute`** — maps an attribute to a column. `attribute:` and `target: { column: … }`.
- **`er2db_relation`** — maps a relation to a foreign key. `relation:` and `fk:`.

```ttr
def er2db_relation order_customer {
    relation: er.entity.order_customer,
    fk: db.dbo.fk_orders_customer
}
```

The inline form and the `map` form are two spellings of the same thing. The inline form is sugar for these definitions. Choose inline for simple one-to-one models; choose the `map` schema when mappings get complex or when you simply prefer to keep all mappings in one place, separate from the entities.

> Do not map the same attribute both inline *and* in the `map` schema — that is a duplicate-mapping error. Pick one home for each mapping.

## Conditional mapping: `whereFilter`

Sometimes one physical table backs an entity only for certain rows — a status flag, a soft-delete column. `er2db_entity` accepts a `whereFilter` that restricts which rows belong to the entity:

```ttr
def er2db_entity active_customer {
    entity: er.entity.active_customer,
    target: { table: db.dbo.CUSTOMER },
    whereFilter: { active: 1 }
}
```

This expresses "the *active customer* entity is the `CUSTOMER` table filtered to `active = 1`" — something the inline form cannot say, and the legacy YAML could only smuggle into a `table:` string like `"CUSTOMER WHERE ACTIVE = 1"`.

## Choosing a style

| Situation | Use |
|---|---|
| One entity ↔ one table, names line up | inline `mapping:` on entity + attributes |
| Migrating an existing YAML model | inline `mapping:` (closest equivalent) |
| Attribute sourced from a different table | `map` schema (`er2db_attribute`) |
| Entity is a filtered subset of a table | `er2db_entity` with `whereFilter` |
| You want all mappings in one reviewable file | `map` schema |

The retail example keeps its mappings in dedicated `map.ttr` files (the general form) so they are easy to review in one place. With ER, DB, and MAP all in hand, the [CNC roles](09-cnc-roles.md) page adds the warehouse meaning on top.
