# Phase 0 — Legacy Renames (management doc)

Status: **ready for implementation** · Precedes all MD-model work · Owner: editor-tooling

Phase 0 fixes the *current* language so the MD model (see [`../../design.md`](../../design.md)) can
land cleanly. It is a **pure rename/refactor**: no new MD behaviour. Because the changes are
breaking (grammar keywords, schema code, file extension), Phase 0 is a **grammar major bump to
3.0** and must be synced to `ai-platform` + the Kotlin conformance harness.

## Why these renames

| Rename | From | To | Reason |
|---|---|---|---|
| Subject-area concept | `domain` block in `.ttrd` files | **`def area`** in `.ttrm` files (no file kind) | Free `domain` for the MD value-set; "area" is model content, so a plain def is cleaner than a file kind. |
| Cross-model mapping schema | `schema map` | **`schema binding`** | Free "map"/"mapping" for the MD primitive. `er2db_*` defs are unchanged, just relocated to the new schema code. |
| Inline mapping property | `mapping:` on entity/attribute/relation | **`binding:`** | Keep the cross-model vocabulary consistent with the schema rename (Stage AA). |
| Model file extension | `.ttr` | **`.ttrm`** ("Tatrman Model") | Disambiguate model files; aligns the family (`.ttrm`, `.ttrg`). |

Out of scope / unchanged: `.ttrg` (graph) keeps its extension; `attribute` keeps its keyword in
both `er` and `md`; the `.ttrl` layout sidecar is already dead (CLAUDE.md decision D4) — flagged for
cleanup in Stage D but not required.

## Stages (each is its own mini task list, 6–8 tasks, TDD-ordered)

- [x] **Stage A** — [`schema map` → `schema binding`](A-schema-map-to-binding.md)
- [x] **Stage AA** — [inline `mapping:` keyword → `binding:`](AA-inline-mapping-to-binding.md)
- [x] **Stage B** — [`domain`/`.ttrd` → `def area`](B-domain-to-area.md)
- [x] **Stage C** — [`.ttr` → `.ttrm` extension](C-ttr-to-ttrm-extension.md)
- [ ] **Stage D** — [grammar 3.0 bump + publish + cross-repo cleanup](D-grammar-version-and-cross-repo.md)
- [ ] **Stage E** — [`ai-models` content migration + metadata-service check](E-ai-models-migration.md)

## Sequencing

Do **A → AA → B → C → D → E**. A and AA are the two halves of the `map/mapping → binding` rename
(schema code, then inline property) and share files — AA runs immediately after A. A/AA/B are
grammar/semantics renames that share the regenerate-and-test loop; doing them before C means the
mass file rename in C happens once, over already-correct content. D closes out the version bump and publishes the 3.0 artifacts after the in-repo work is
green. **E migrates the real model content in the separate `ai-models` repo** (`~/Dev/ai-models`,
`model-ttr/`) and verifies ai-platform's metadata service loads it — it runs last because it needs
both the 3.0 CLI (C) and the ai-platform loader branch (D).

Each stage ends green on the repo gates before the next starts:

```
pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test
```

## Working rules for the implementer

- **Check every checkbox the moment its task is done** — these docs are the progress record.
- **TDD:** within each stage, do the test-update task(s) first (red), then the code change (green).
- **Grammar regen is not optional:** after any `TTR.g4` edit run the two regen steps from
  `CLAUDE.md` → "Grammar regeneration" before building (`packages/parser` prebuild, then
  `vscode-ext` TextMate regen). `generated/` is gitignored; only `TTR.g4`, the scripts, and
  `ttr.tmLanguage.json` are committed.
- **Don't hand-edit `packages/*/src/generated/**`** — it is regenerated from `TTR.g4`.
- **Cross-repo:** Stage D owns the ai-platform sync + Kotlin conformance; earlier stages must not be
  pushed as a tagged grammar release until D is done.

## Definition of DONE for Phase 0

1. No occurrence of `schema map`, the `domain` block, the `.ttrd` extension, or the `.ttr`
   extension remains in `packages/**` source, fixtures, or `docs/**` (except historical CHANGELOG
   entries).
2. All four repo gates pass; the Kotlin conformance harness passes against the vendored 3.0 grammar.
3. `ai-platform` consumes the 3.0 grammar (schema `binding`, `area` defs) and its agent registry
   discovers areas by `def area` instead of `.ttrd` files.
4. `CHANGELOG.md` documents the 3.0 breaking changes; a migration path exists (Stage D, task D6).
5. The `ai-models` `model-ttr/` content is migrated (`.ttrm` + `def area`), its
   `resolved-packages.json` regenerates with no drift, and **ai-platform's metadata service loads
   the migrated files with zero errors** (Stage E).
