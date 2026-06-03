# T4 — Samples + docs

Make the in-repo samples exercise the new surface, and refresh the
forward-looking plan docs that still describe `searchable` as a top-level
property. Don't rewrite historical/completed docs — only the ones a future
implementer would act on.

## Tasks

- [ ] **Add a positive sample for the widened surface.** In
  `samples/v1-metadata/db.ttr`, add a `search { searchable: true, fuzzy: true }`
  block to one `column` (and optionally a table-level `search`). Keep it small;
  this file is parsed by sample-driven tests, so it must stay error-free.

- [ ] **Add an er-side sample.** In `samples/v1-metadata/er.ttr`, add a
  `search { searchable: true, fuzzy: true, keywords { cs: ["..."], en: ["..."] } }`
  to one `attribute`, demonstrating booleans + localized lists together.

- [ ] **Add broken fixtures for the two diagnostics.** Under `samples/broken/`:
  - `search-fuzzy-without-searchable.ttr` — an element with
    `search { fuzzy: true }` and no `searchable`.
  - `search-duplicate-subproperty.ttr` — `search { patterns: [...], patterns: [...] }`.
  These can be referenced by the T3 tests or by the broken-recovery sample test.

- [ ] **Refresh forward-looking plan docs.** Update references that list
  `searchable` as a top-level property so future work targets the new surface:
  - `docs/v1-1/plan/tasks/H2-other-completion.md` — completion should offer
    `search` on table/column/view/relation (in addition to entity/attribute/
    query/role), offer `searchable`/`fuzzy` **inside** the `search` block, and
    must **not** offer top-level `searchable`.
  - `docs/v1-1/plan/tasks/E1-graph-picker.md` — adjust any mention of the
    `searchable` column/attribute property.
  - Leave `docs/v1/plan/tasks-phase-0{1,2}-*.md` as historical record (they
    describe the original Phase 1/2 surface; do not rewrite history).

- [ ] **Cross-check the architecture doc.** Grep `docs/v1/design/architecture.md`
  for `searchable`/`search`; if it documents the property surface, add a note
  that `searchable` now lives inside the `search` block and `fuzzy` was added.
  (`grep -n "search" docs/v1/design/architecture.md`.)

- [ ] **Run the sample-driven tests.**
  ```bash
  pnpm --filter @modeler/parser test
  pnpm --filter @modeler/integration-tests test
  ```

## Verification

- [ ] All sample `.ttr` files still parse with the expected error counts
  (0 for `v1-metadata/*`, >0 for the new `broken/*`).
- [ ] `grep -rn "searchable" docs samples` shows no stale top-level usage in
  forward-looking docs or `.ttr` samples (YAML under `samples/yaml/**` keeps
  `searchable:` — that's intentional input for T5).
