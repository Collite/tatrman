# Model

> **I own the semantics.**

The model is where your organisation's meaning lives: what a customer *is*, which
table is the truth, who may see which rows. Tatrman treats it as the deployment
artifact — you write it in TTR-M, review it in a pull request, and ship it.

This track is for the person who owns that meaning. It does not assume you write
code for a living; it assumes you know the business.

## What will live here

<!-- TODO(S6): the existing user manual (`docs/manual/`) migrates INTO this track
     — the manual's future is this site, not a parallel document. The language
     reference is generated from the grammar/schema where possible, so it cannot
     drift from what the parser accepts. Structure follows the pilot's DFP wiki
     pattern: that shape is field-tested by real analysts, not invented here. -->

- **Model your first three tables** — the tutorial: start from what
  `ttr import-schema` gave you and make it mean something.
- **The layers** — `db`, `er`, `cnc`, `md`: what each layer is for, why the
  mirror of your database is not yet a model, and who owns which layer.
- **Language reference** — TTR-M in full: packages and areas, bindings, aliases
  and search hints, named and pattern queries, the `security` block, worlds and
  composition.
- **Why the model is the deployment artifact** — the explanation piece: what you
  get by putting semantics in git instead of in a BI tool.
