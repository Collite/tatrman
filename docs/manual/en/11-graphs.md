# Graphs and diagrams

A model of any size has too many objects to show at once. A **graph** — a `.ttrg` file — is a curated slice: a named, hand-picked set of objects from one schema, optionally with saved positions, that the graphical designer renders as a diagram. Graphs are how you make focused, shareable pictures of a model without exporting anything.

## What a graph is

A `.ttrg` file contains exactly one `graph` block (not `def` definitions — those live in `.ttr` files):

```ttr
graph sales_er {
  schema: er,
  description: "The sales star: order_line surrounded by order, customer and product.",
  tags: ["core-domain", "sales"],
  objects: [
    shop.sales.er.entity.customer,
    shop.sales.er.entity.order,
    shop.sales.er.entity.order_line,
    shop.catalog.er.entity.product,
    shop.catalog.er.entity.category,
    shop.sales.er.entity.order_customer,
    shop.sales.er.entity.line_order,
    shop.sales.er.entity.line_product,
    shop.catalog.er.entity.product_category
  ]
}
```

- **`schema`** — required. One graph shows one perspective: an `er` graph or a `db` graph, not a mix.
- **`objects`** — required. A list of fully-qualified names to include. Always full paths, because a graph can pull from several packages (this one spans `shop.sales` and `shop.catalog`).
- **`description`** and **`tags`** — optional metadata for organizing and finding graphs.

## What gets drawn

The designer draws an object only if it is **listed in `objects`**. For an edge — a relation or a foreign key — to appear, both of its endpoints *and* the edge object itself must be in the list. This is deliberate: a graph shows exactly what you put in it, with no surprise nodes pulled in by association.

In the example above, the relation `shop.sales.er.entity.order_customer` is listed alongside both entities it connects (`order` and `customer`), so the edge is drawn. Drop the relation from the list and the two entities still appear, just unconnected.

## Layout

A graph can remember where each node sits, so a diagram you arrange stays arranged:

```ttr
graph sales_er {
  schema: er,
  objects: [ … ],
  layout: {
    viewport: { zoom: 1.0, panX: 0, panY: 0, displayMode: "with-types" },
    nodes: {
      shop.sales.er.entity.order_line: { x: 480, y: 320 },
      shop.sales.er.entity.order: { x: 200, y: 200 },
      shop.sales.er.entity.customer: { x: 200, y: 440 },
      shop.catalog.er.entity.product: { x: 760, y: 320 },
      shop.catalog.er.entity.category: { x: 1020, y: 320 }
    }
  }
}
```

- **`viewport`** — the saved zoom and pan, plus a `displayMode` (for example `"with-types"` to show attribute types on the nodes).
- **`nodes`** — an `{ x, y }` position per object. Objects without a saved position are auto-placed.

You normally do not hand-write `layout`. You arrange the diagram in the designer and it writes these positions back into the `.ttrg` file. Because the result is plain text, layout changes diff and review like any other change — you can see in a pull request that someone moved the fact to the center.

## Graphs vs. the model

A graph references the model; it does not contain it. The objects live in `.ttr` files, and the graph just names them. So:

- Editing an entity updates every graph that shows it — there is one source of truth.
- Listing a name in a graph that no longer exists raises a `graph-object-not-found` warning, so stale diagrams surface instead of silently lying.
- Deleting a graph throws away a *view*, never any model data.

## Using graphs in practice

Make several small, purposeful graphs rather than one giant one: a per-domain overview, a "just the fact and its dimensions" star, a physical-only `db` graph for the DBAs. The retail model ships `graphs/sales_er.ttrg` as a conceptual star; a parallel `db` graph would list the `shop.*.db.dbo.*` tables and their `fk` objects instead.

A `.ttrg` that holds a `def` instead of a `graph`, or a `.ttr` that holds a `graph`, is a `wrong-file-kind` error — the two file types stay distinct so the tooling always knows whether it is looking at model or view.

The remaining language surface — `query` and `procedure` — is covered next, in [Queries and procedures](12-queries-and-procedures.md).
