# TTR Modeling Language — User Manual

TTR (Tatrman) is a declarative language for describing a data model from several angles at once: the physical database, the conceptual entities behind it, and the mapping that ties the two together. If you know SQL but have never built a formal model, this manual is written for you.

This wiki is organized as a short course. The early pages build a mental model and walk you through your first file; the middle pages cover each part of the language; the later pages cover organizing larger models, the editor tooling, and a complete reference.

## Getting started

1. [Introduction](01-introduction.md) — what TTR is, what it replaces, and how to read this manual.
2. [TTR for SQL users](02-ttr-for-sql-users.md) — the one idea that makes everything else click.
3. [YAML vs TTR](03-yaml-vs-ttr.md) — for readers coming from the legacy YAML models.
4. [Your first model](04-your-first-model.md) — a working file in a few minutes.

## The language

5. [Syntax basics](05-syntax-basics.md) — `schema`, `def`, properties, types, values.
6. [The DB schema](06-db-schema.md) — tables, columns, keys, indexes, foreign keys.
7. [The ER schema](07-er-schema.md) — entities, attributes, relations.
8. [Mapping](08-mapping.md) — connecting ER to DB, from the simple case up.
9. [CNC roles](09-cnc-roles.md) — fact, dimension, and the rest of the warehouse vocabulary.

## Organizing larger models

10. [Packages and imports](10-packages-and-imports.md) — splitting a model across files and folders.
11. [Graphs and diagrams](11-graphs.md) — curated `.ttrg` views.
12. [Queries and procedures](12-queries-and-procedures.md) — `query` and `procedure` definitions.

## Tooling

13. [The VS Code experience](13-vscode.md) — live linting, highlighting, navigation. The payoff over YAML.

## Reference

14. [Reference](14-reference.md) — every definition kind, the type system, the diagnostic catalog, a grammar cheat-sheet, and a full worked model.

---

A complete, runnable example model lives in [`examples/retail/`](examples/retail/). Every code block in this manual is taken from it.
