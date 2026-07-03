# Section H — Wrap-up + PR

Progress log, final verification on both repos, PR descriptions ready. This is the closeout step — short, ceremonial, but skipping it means the next person doesn't know what shipped.

**Depends on:** Section G (both repos green).

**Files:**
- New file: `docs/v2-1/plan/progress-phase-v2.1.md`.
- `CLAUDE.md` — update *only if* a new invariant emerged during implementation.
- Two PR descriptions (drafted here as Markdown, pasted into GitHub/GitLab when opened).

---

## Tasks

### H.1 — Write the progress log

- [ ] **Create `docs/v2-1/plan/progress-phase-v2.1.md`.** Mirror `docs/v1-1/plan/progress-phase-v1.1.md`'s shape (read it for style — one line per section, with commit hash, dates, and any deviations from the plan).

  Template:
  ```md
  # v2.1 — Progress log

  **Status:** Complete, YYYY-MM-DD. Feature: inline mappings (syntactic sugar for `def er2db_*`).
  Design: [`../design/v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md).
  Plan:   [`implementation-plan-v2.1.md`](implementation-plan-v2.1.md).

  ## Sections

  | Section | Commit  | Notes |
  | ------- | ------- | ----- |
  | A — Design docs                  | `<hash>` | Three docs landed; no deviations. |
  | B — Grammar additions            | `<hash>` | `MAPPING` token + 8 new rules + `targetProperty` relaxation. |
  | C — Parser AST + walker          | `<hash>` | New types: `MappingProperty`, `MappingColumnEntry`, `MappingColumnValue`. Source locations point at value, not keyword. |
  | D — Semantic synthesizer         | `<hash>` | `synthesizeMappings()` adds project-table entries only; per-document `map`-schema tables untouched. |
  | E — Conflict validator           | `<hash>` | `ttr/duplicate-mapping` fires per side of any inline ↔ explicit collision. |
  | F — Integration tests + samples  | `<hash>` | `samples/2.1/` rewritten to four canonical forms; broken fixtures cover all 3 kinds. |
  | G — ai-platform mirror           | `<hash>` (ai-platform branch) | Kotlin parser regenerated; synthesizer + validator + tests mirror modeler. `check-sync.sh` green. |
  | H — Wrap-up                       | `<hash>` | This file. |

  ## Deviations from the plan

  (List any. If none: "None.")

  ## Open follow-ups

  (Anything explicitly punted. If none: "None.")
  ```

  Fill in the hashes once each section's commit lands.

### H.2 — Update `CLAUDE.md` (only if needed)

- [ ] **Re-read `CLAUDE.md` and check whether any v2.1 invariant deserves a new bullet** under "Key invariants" (~mid-file). Candidates:
  - "Synthesized er2db_* symbols live in the project symbol table only — never in any `DocumentSymbolTable` for the `map` schema. If you find yourself iterating `map`-schema document tables and not seeing inline-mapped entries, that's by design."
  - "The `mappingSource` field on `SymbolEntry` is what discriminates inline-synthesized from explicit declarations. Don't conflate `duplicate-definition` and `duplicate-mapping` — they have different gating conditions."

  Only add bullets that surface invariants the next developer would otherwise trip over. If everything went smoothly and nothing surprised you, skip this step.

- [ ] **Confirm "Project" section header still reads correctly.** No v2.1-specific change here unless the inline-mapping feature shifted the language scope notably.

### H.3 — Final verification on modeler

- [ ] **From `~/Dev/modeler`, on `feat/v2.1-inline-mappings`:**
  ```bash
  pnpm install                          # in case any new deps got added
  pnpm -r build
  pnpm -r test
  pnpm -r typecheck
  pnpm -r lint
  ```
  All green.

- [ ] **Confirm the version constant ships.**
  ```bash
  cat packages/grammar/src/generated/version.ts
  ```
  Should show `TTR_GRAMMAR_VERSION = '2.1' as const;`.

- [ ] **Confirm the CHANGELOG entry is in place.**
  ```bash
  head -30 packages/grammar/CHANGELOG.md
  ```
  Top entry should be `## 2.1 — <date>`.

### H.4 — Final verification on ai-platform

- [ ] **From `~/Dev/ai-platform`, on `feat/v2.1-inline-mappings`:**
  ```bash
  ./gradlew clean build test
  ```
  All green.

- [ ] **Re-check sync hashes.**
  ```bash
  cd ~/Dev/modeler
  packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform
  ```
  Exit 0.

### H.5 — Draft the modeler PR description

- [ ] **Stage it** in `docs/v2-1/pr-description-modeler.md` (or in a scratch file — this isn't a file to commit, it's a paste target). Template:

  ```md
  # v2.1: inline mappings

  Adds `mapping:` syntactic sugar on `def entity`, `def attribute`, and `def relation`. Synthesizes the equivalent `def er2db_entity` / `er2db_attribute` / `er2db_relation` symbols at load time. Backward-compatible — every v2.0 file parses unchanged.

  **Pairs with:** ai-platform PR [link]. Single deployment unit — both merge together.

  ## Why

  In real projects, most `def er2db_attribute` declarations are one-line, mechanically determined from an entity attribute and its target column. Forcing them into a separate `map.ttr` doubles the typing and splits the cognitive context. v2.1 lets the author put the mapping next to what it maps.

  ## What's in the PR

  - **Section A** — Design docs under `docs/v2-1/`. Decisions C1–C10, four surface forms, `ttr/duplicate-mapping` semantics.
  - **Section B** — Grammar additions in `TTR.g4` + property-map regen + TextMate regen. `@grammar-version` bumped to `2.1`.
  - **Section C** — Parser AST extensions: `MappingProperty`, `MappingColumnEntry`, `MappingColumnValue`. Walker wires entity/attribute/relation. Parser tests cover all four forms + source-location accuracy.
  - **Section D** — Semantic synthesizer: `synthesizeMappings()` produces synthesized `er2db_*` symbols in the project table, schemaless (not added to `map`-schema document tables).
  - **Section E** — `ttr/duplicate-mapping` validator: errors on both sides of any inline ↔ explicit collision.
  - **Section F** — `samples/2.1/` rewritten to demonstrate all four forms cleanly. Broken fixtures under `samples/broken/v2.1/`. Three new integration tests.

  ## Backward compatibility

  - Every existing v2.0 file parses unchanged.
  - Every existing `def er2db_*` declaration continues to work. `map` schema is not deprecated.
  - The grammar version is a minor bump (2.0 → 2.1), additive only.

  ## Testing

  - `pnpm -r test` — green.
  - `pnpm -r typecheck` — clean.
  - `samples/v1.1-mini` and `samples/v1.1-metadata` parse and resolve unchanged.
  - All four inline forms covered by parser, synthesizer, and integration tests.
  - All four conflict shapes covered by validator tests.

  ## Design references

  - [`docs/v2-1/design/v2.1-inline-mappings.md`](docs/v2-1/design/v2.1-inline-mappings.md) — full design.
  - [`docs/v2-1/design/grammar-v2-1-changes.md`](docs/v2-1/design/grammar-v2-1-changes.md) — ai-platform contract.
  - [`docs/v2-1/plan/implementation-plan-v2.1.md`](docs/v2-1/plan/implementation-plan-v2.1.md) — implementation plan.
  - [`docs/v2-1/plan/progress-phase-v2.1.md`](docs/v2-1/plan/progress-phase-v2.1.md) — completion log.

  ## Reviewer checklist

  - [ ] Grammar diff matches `grammar-v2-1-changes.md` §3.
  - [ ] Synthesized symbols' source locations point at inline mapping *value*, not the `mapping` keyword.
  - [ ] `ttr/duplicate-mapping` fires on both sides of every conflict.
  - [ ] No `er2db_*` synthesized entries leak into any `DocumentSymbolTable` for a `map`-schema file.
  - [ ] All v1.1 samples still parse and resolve.
  ```

### H.6 — Draft the ai-platform PR description

- [ ] **Same shape**, for the ai-platform side. Template:

  ```md
  # v2.1: inline mappings (TTR grammar bump + Kotlin loader)

  Adopts modeler's v2.1 inline-mapping shorthand. Vendored `TTR.g4` regenerated, Kotlin AST + synthesizer + validator mirror modeler's TypeScript implementation.

  **Pairs with:** modeler PR [link]. Single deployment unit — both merge together.

  ## Why

  Modeler v2.1 introduces a new optional `mapping:` property on `def entity` / `def attribute` / `def relation`. ai-platform's metadata service must understand the synthesized symbols to continue serving entity↔table mappings for projects that adopt the inline form.

  ## What's in the PR

  - **Vendored grammar updated** to `@grammar-version: 2.1` via `sync-to-ai-platform.sh`. `check-sync.sh` from modeler returns 0.
  - **Kotlin parser regenerated** from the new grammar.
  - **AST extended** with `MappingProperty` sealed class, `MappingColumnEntry`, `MappingColumnValue`. `EntityDef` / `AttributeDef` / `RelationDef` gain `mapping: MappingProperty?`.
  - **Synthesizer (`MappingSynthesizer.kt`)** produces project-level `Er2dbEntitySymbol` / `Er2dbAttributeSymbol` / `Er2dbRelationSymbol` entries from inline mappings, with `mappingSource = MappingSource.Inline(...)` and source locations pointing back at the inline `mapping:` value.
  - **Validator** emits `ttr/duplicate-mapping` (Error) on both sides of any inline ↔ explicit collision.
  - **Tests** cover all four surface forms + every conflict shape, mirroring modeler's integration tests.

  ## Backward compatibility

  - Existing parser-suite tests pass with no regressions.
  - Projects that don't use inline mappings see no change in metadata loader behaviour.
  - Existing `def er2db_*` declarations continue to work unchanged.

  ## Testing

  - `./gradlew build test` — green.
  - Existing parser-suite tests still pass.
  - New Kotlin tests cover parser, synthesizer, and validator paths.
  - `check-sync.sh` from modeler returns 0.

  ## Design references

  See modeler's docs:
  - `docs/v2-1/design/v2.1-inline-mappings.md` — feature design.
  - `docs/v2-1/design/grammar-v2-1-changes.md` — this PR's contract.
  ```

### H.7 — Open the PRs (coordinated)

- [ ] **Push both branches.**
  ```bash
  cd ~/Dev/modeler && git push -u origin feat/v2.1-inline-mappings
  cd ~/Dev/ai-platform && git push -u origin feat/v2.1-inline-mappings
  ```

- [ ] **Open both PRs.** Cross-link them in the descriptions. Mark both as draft if not ready for review immediately.

- [ ] **Coordinate the merge.** Neither should land in isolation. Sequence:
  1. Both PRs reviewed and approved.
  2. Merge modeler PR first.
  3. Re-run `check-sync.sh ~/Dev/ai-platform` to confirm hashes still match (modeler's main HEAD shouldn't have drifted between branch and merge; if it did, re-sync on ai-platform branch and force-push).
  4. Merge ai-platform PR.
  5. Re-run `check-sync.sh` once more after both are on main.

---

## Verification

- [ ] `progress-phase-v2.1.md` exists, with one row per section and the commit hashes filled in.
- [ ] If `CLAUDE.md` was updated, the new bullets are accurate and minimal.
- [ ] Both repos `build && test` green.
- [ ] `check-sync.sh ~/Dev/ai-platform` returns 0 after both branches are pushed.
- [ ] Both PR descriptions are drafted (in scratch files or the PR UI).
- [ ] PRs are cross-linked.
- [ ] No outstanding TODO comments in any v2.1 code path:
  ```bash
  grep -rn "TODO.*v2.1\|FIXME.*v2.1\|TODO.*inline.mapping" packages/ ~/Dev/ai-platform/shared/ 2>/dev/null
  ```

## Notes / gotchas

- **Commit hashes for the progress log** can only be filled in after each section is actually committed. Update the log as you go, or in one pass at the end.
- **The `CLAUDE.md` update is optional** — don't add filler. If everything went per plan and nothing surprised you, leave it alone.
- **PR cross-link timing.** GitHub doesn't render cross-repo links specially, but reviewers appreciate the explicit pointer in the description. Edit each PR's description once both URLs are known.
- **`check-sync.sh` is your safety net.** Run it any time you've touched grammar files or moved between branches. If it ever returns nonzero, do not merge until resolved.
- **Don't squash the section commits.** The "Section X: ..." style is the project convention (CLAUDE.md notes commit style "Section <X>: <description>" for phased plan work) — preserve them in the merged history. Use a merge commit, not squash.
