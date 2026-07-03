# TTR Modeling Language — User Manual

TTR (Tatrman) is a declarative language for describing a data model from several angles at once: the physical database, the conceptual entities behind it, and the binding that ties the two together. Each angle is a *model* (`db`, `er`, `binding`, `cnc`, `md`), selected with the `model` directive. If you know SQL but have never built a formal model, this manual is written for you.

This wiki is organized as a short course. The early pages build a mental model and walk you through your first file; the middle pages cover each part of the language; the later pages cover organizing larger models, the editor tooling, and a complete reference.

## Getting started

1. [Introduction](01-introduction.md) — what TTR is, what it replaces, and how to read this manual.
2. [TTR for SQL users](02-ttr-for-sql-users.md) — the one idea that makes everything else click.
3. [YAML vs TTR](03-yaml-vs-ttr.md) — for readers coming from the legacy YAML models.
4. [Your first model](04-your-first-model.md) — a working file in a few minutes.

## The language

5. [Syntax basics](05-syntax-basics.md) — `model`, `def`, properties, types, values.
6. [The DB model](06-db-schema.md) — tables, columns, keys, indexes, foreign keys.
7. [The ER model](07-er-schema.md) — entities, attributes, relations.
8. [Binding](08-mapping.md) — connecting ER to DB, from the simple case up.
9. [CNC roles](09-cnc-roles.md) — fact, dimension, and the rest of the warehouse vocabulary.
10. [The MD model](15-md-model.md) — measures, dimensions, cubelets (multidimensional).

## Organizing larger models

11. [Packages, imports, and areas](10-packages-and-imports.md) — splitting a model across files and folders, and scoping it.
12. [Graphs and diagrams](11-graphs.md) — curated `.ttrg` views.
13. [Queries and procedures](12-queries-and-procedures.md) — `query` and `procedure` definitions.
14. [Areas](16-areas.md) — `def area`, named slices of the model for downstream consumers.
15. [Agents](17-agents.md) — the Golem agent registry that loads those slices.

## Tooling

16. [The VS Code experience](13-vscode.md) — live linting, highlighting, navigation. The payoff over YAML.

## Reference

17. [Reference](14-reference.md) — every definition kind, the type system, the diagnostic catalog, a grammar cheat-sheet, and a full worked model.

---

A complete, runnable example model lives in [`examples/retail/`](examples/retail/). Every code block in this manual is taken from it.
