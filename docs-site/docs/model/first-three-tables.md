<!-- SPDX-License-Identifier: Apache-2.0 -->
# Tutorial: model your first three tables

> **Stub (SV-P4·S4·T7).** The `ttr import-schema` engine is built and proven end-to-end; S6 turns
> this into a full worked tutorial against the sample database.

The fastest way to a model is to let `import-schema` draft it, then shape three tables by hand so
you learn the vocabulary. Outline:

1. **Import.** Run `ttr import-schema` against the sample database (see
   [Import a database](../get-running/import-schema.md)). You get `db.*.ttrm`, `er.ttrm` and the
   review checklist.
2. **Read the `db` mirror.** Find your three tables — note the columns, types, primary keys and
   any declared foreign keys the tool captured.
3. **Shape the `er` entities.** Open `er.ttrm`. For each of the three, refine the entity: give it a
   plural label, mark the name attribute, drop columns that are pure plumbing.
4. **Accept the relations.** Walk the [review checklist](../get-running/review-checklist.md): accept
   the declared and verified relations between your three tables, and decide any proposed
   header/detail fold.
5. **Commit.** The `er` model is now yours — re-runs never overwrite it.

_TODO(S6): concrete table names, before/after snippets, and the expected review-checklist entries._
