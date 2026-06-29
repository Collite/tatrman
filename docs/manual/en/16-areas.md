# Areas

`def area` names a **subject area** — a named grouping of packages (and, optionally, individual entities) that delimits which part of the model a single [agent](17-agents.md) loads. It is an ordinary definition: it lives in a normal `.ttrm` file, contributes a resolvable symbol to the project, and can be imported.

> An area replaces the older `domain` block and the `.ttrd` file kind from v2.3. Since v3.0 the keyword `domain` is freed for the multidimensional (MD) model.

## What it is for

A real model splits across many [packages](10-packages-and-imports.md). An area groups several of them under one name, so you can speak about "accounting" or "sales" as a whole — usually in order to delimit the scope a single agent sees.

An area is an **editor and registry concept**, not part of the runtime graph: the ai-platform metadata loader does **not** load areas into the model. They exist to delimit scope, and they surface in the `resolved-packages.json` artifact that the [agent registry](17-agents.md) reads.

## Syntax

```ttr
package domains

def area accounting {
    description: "Accounting and the downstream business documents",
    tags: ["finance"],
    packages: [
        accounting,
        business_documents
    ]
}
```

An area belongs to no model (`db` / `er` / `binding` / `cnc`) — it is a perspective-independent definition, much like `def project`. By convention areas live in a `domains/` folder, one file per area (or several areas in one file).

## Properties

| Property | Required | Value |
|---|---|---|
| `description` | – | string — a description of the area |
| `tags` | – | list of strings |
| `packages` | ✓¹ | list of package names; nested dotted names allowed (`shop.sales`) |
| `entities` | ✓¹ | list of canonical entity qnames (`shop.sales.er.entity.order`) |

¹ Provide **at least one** of `packages` / `entities`.

Membership in `packages` is **recursive**: an area pulls in a package *and* all of its sub-packages — unlike `import x.*`, which is not recursive (see [Packages and imports](10-packages-and-imports.md)). The `entities` field adds individual entities on top of whole packages.

```ttr
package domains

def area sales_360 {
    description: "A slice for sales reporting",
    tags: ["reporting", "core"],
    packages: [shop.sales, shop.catalog],
    entities: [shop.sales.er.entity.order]
}
```

## Relationship to agents

An agent (Golem) references an area by name in its `shem.areas` field (see [Agents](17-agents.md)). At runtime the platform **expands** the area into its packages and entities. Instead of listing every package in every agent, you define the area once and share it across agents.

After any change to areas, regenerate the snapshot that CI and the agent registry read:

```
modeler resolve-packages model-ttr --out generated/resolved-packages.json
```

## Area diagnostics

| Diagnostic | Severity | Meaning |
|---|---|---|
| `ttr/area-member-not-found` | warning | A `packages:`/`entities:` member doesn't resolve to anything in the model. |
| `ttr/area-empty` | warning | A `def area` with no members — it scopes nothing. |
| `ttr/duplicate-area` | error | Two `def area` definitions declare the same area name. |
| `ttr/area-redundant-member` | info | An `entities:` entry already covered by a recursive `packages:` member. |

See also the [Areas section](10-packages-and-imports.md#areas) of *Packages and imports* for how areas sit alongside packages and graphs.
