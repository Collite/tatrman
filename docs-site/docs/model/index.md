# Model

> **I own the semantics.**

The model is where your organisation's meaning lives: what a customer *is*, which
table is the truth, who may see which rows. Tatrman treats it as the deployment
artifact — you write it in TTR-M, review it in a pull request, and ship it.

This track is for the person who owns that meaning. It does not assume you write
code for a living; it assumes you know the business.

## The pages

- [**Model your first three tables**](first-three-tables.md) — the tutorial: start
  from what `ttr import-schema` gave you and make it mean something.
- [**The layers**](layers.md) — `db`, `er`, `cnc`, `md`: what each layer is for, why
  the mirror of your database is not yet a model, and who owns which layer.
- [**Language reference**](language-reference.md) — TTR-M in full: definition kinds,
  packages and areas, bindings, aliases and search hints, named and pattern queries,
  governance through roles, worlds and composition. (Skeleton for grammar v0.9;
  worked examples migrate in from the user manual.)
- [**Why the model is the deployment artifact**](why-model-is-the-artifact.md) — the
  explanation piece: what you get by putting semantics in git instead of in a BI tool.
