# T5 — ai-platform: YAML loader + YAML→TTR converter

This task is in the **separate `ai-platform` repo** (`~/Dev/ai-platform`), which
is **not mounted** in this workspace. Steps below are written against the
described behaviour and the vendoring contract in `CLAUDE.md` — **verify every
path and symbol against the actual code before editing.**

## Background

- The TTR grammar is **vendored** into `ai-platform`; its Kotlin parser
  regenerates from that vendored copy. Any grammar change here (T1) must be
  synced and regenerated there, or ai-platform will reject the new
  `search { searchable, fuzzy }` shape and still accept the removed top-level
  `searchable`.
- The metadata service loads entity/attribute metadata from YAML. In this repo's
  copies of that YAML (`samples/yaml/entities/*.yaml`), the relevant fields look
  like:
  ```yaml
  - name: "jméno_uživatele"
    column: "JMENO_UZIV"
    type: "varchar(100)"
    searchable: true
    search_hint: "Hledání uživatele podle jména. Příklad: ..."
  ```
  Today the loader reads `searchable` and the converter emits it as a **top-level**
  TTR property. After T1 that's a syntax error — it must be emitted **inside** a
  `search { … }` block.

## Tasks

- [ ] **Sync the grammar and regenerate the Kotlin parser.** From this repo:
  ```bash
  packages/grammar/scripts/sync-to-ai-platform.sh ~/Dev/ai-platform
  packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform   # hashes must match
  ```
  Then regenerate ai-platform's Kotlin parser from its vendored copy (follow
  ai-platform's own generation step) and rebuild it.

- [ ] **Update the YAML→TTR converter** (metadata service). Find where it emits a
  `searchable` property for an attribute/column and change it to fold `searchable`
  into a `search { … }` block:
  - If the source row has `searchable: true`, emit `search { searchable: true }`.
  - If the row already produces other search content (e.g. `search_hint` →
    `patterns`/`descriptions`, or any existing `search` mapping), **merge** into a
    single `search { … }` block rather than emitting two blocks.
  - Locate it: `grep -rn "searchable" ~/Dev/ai-platform` and look for the
    metadata/TTR emission code (likely the `metadata` service's YAML loader /
    serializer).

- [ ] **Update the YAML loader's reading of `searchable`.** Wherever the loader
  parses the attribute and currently maps `searchable` onto a top-level model
  field, route it through the same in-block representation the converter now
  produces, so loader and converter agree on one shape.

- [ ] **Decide `fuzzy` provenance (scope check).** This feature only *relocates*
  `searchable`; there is no YAML `fuzzy` source field today. Either (a) leave
  `fuzzy` unset by the converter, or (b) add a YAML `fuzzy:` field and map it.
  Confirm with the model owner which is wanted before adding (a) is the default —
  no YAML change.

- [ ] **Update ai-platform's loader-side `search`-block validator** if present.
  The grammar comment ("a parse-time warning is emitted by the loader's
  validator") refers to ai-platform. If that validator enumerates allowed
  sub-properties, add `searchable` and `fuzzy`; mirror the two diagnostics from
  T3 (fuzzy-without-searchable, duplicate sub-property) only if ai-platform wants
  parity.

- [ ] **Update ai-platform tests/fixtures.** Any fixture asserting a top-level
  `searchable` in generated TTR must move it inside `search { … }`. Run the
  metadata service test suite.

## Verification

- [ ] `check-sync.sh` reports matching hashes (vendored grammar == canonical).
- [ ] ai-platform builds with the regenerated Kotlin parser.
- [ ] Round-trip: a YAML entity with `searchable: true` produces TTR containing
  `search { searchable: true }` (merged with any other search content), and that
  TTR parses cleanly under the new grammar.
- [ ] ai-platform metadata test suite is green.

## Note for the editor-tooling reviewer

T1–T4 are independently shippable in this repo. T5 must land **before** any
ai-platform metadata that was generated with the old top-level `searchable` is
re-validated against the new grammar, otherwise that metadata will fail to parse.
Coordinate the merge order.
