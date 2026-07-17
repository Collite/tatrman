# Changelog — TTR Modeler (VS Code)

All notable changes to the `collite.ttr-modeler-vsc` extension. Versions track
the grammar/IDE-support line owned by the `tatrman` repo.

## 0.9.9 — 2026-07-17

Syntax-highlighting fixes for the TextMate grammar (no parser/LSP change).

- **`semantics { … }` is highlighted again.** The `semantics` block keyword
  (and the rest of the grammar 4.1–4.4 keyword family — `world`, `lexicon`,
  `term`, `pattern`, `example`, `engine`, `executor`, `storage`, `extends`,
  `hosts`, `staging`, `for`, `forms`, `match`, `locale`) rendered white because
  they were missing from the scope map. They now color as keywords, consistent
  with sibling entity-body fields like `aliases`/`attributes`/`search`.
- **Primitive types and literals now colored.** `text`/`int`/`float`/`bool`/…
  render as types and `true`/`false`/`null` (plus the index-type, constraint-type
  and query-language enums) render as constants — previously these were emitted
  into the scope map but never wired into the grammar output, so they showed as
  plain text.
- **`locale` recolored** to match its directive sibling `schema` (it's the
  `model … locale <id>` header, not a body property).

> Provenance: this listing was previously published from the frozen `modeler`
> fork (through 0.9.8). From 0.9.9 it is published from `tatrman`, the canonical
> owner of the grammar + IDE support; the version series continues unbroken.
