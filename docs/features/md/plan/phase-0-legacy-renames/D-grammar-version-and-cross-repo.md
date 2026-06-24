# Stage D — Grammar 3.0 bump, cross-repo sync, cleanup

Goal: finalize Phase 0 as a coherent **grammar major release (3.0)**, propagate it to `ai-platform`
and the Kotlin conformance harness, document the breaking changes, and provide a migration path.

Prereq: A, B, C merged and all in-repo gates green.

**No grammar sync.** `TTR.g4` is the single canonical source for all three generated parsers (TS,
Kotlin, Python); it is **not vendored**. Consumers bump the **published artifact** version. The old
`sync-to-ai-platform.sh` / `check-sync.sh` scripts and the `grammar-sync.yml` workflow have been
**removed**. Follow [`docs/grammar-master/new-grammar-version-process.md`](../../../../grammar-master/new-grammar-version-process.md)
for the full procedure — this stage is the Phase-0-specific application of it.

References (verified):
- `packages/grammar/scripts/`: `generate-typescript-parser.sh`, `extract-property-map.ts` (the
  sync scripts are gone).
- Three targets: TS `packages/parser` (`antlr-ng`); Kotlin `packages/kotlin/ttr-parser` +
  `ttr-semantics` (Gradle plugin reads `.g4` directly); **Python `packages/python/ttr-parser`**
  (ANTLR jar via `scripts/generate-python-parser.sh` + Hatchling build hook).
- `.github/workflows/conformance.yml` (TS↔Kotlin↔Python conformance: `dump`/`dump-sem`, Kotlin
  `*ConformanceSpec*`, `py-dump`/`py-sem-dump`), `publish.yml` (Kotlin Maven, `kotlin/v<x.y.z>`),
  `publish-python.yml` (PyPI, `python/v<x.y.z>`).
- `ai-platform` repo `~/Dev/ai-platform`: consumes the **published** `ttr-parser` artifact; its agent
  registry must switch to discovering `def area` (was `.ttrd`). See memory [[ai_platform_repo]].

---

- [x] **D1 — Set the version.** In `TTR.g4` set `@grammar-version: 3.0`. Confirm the parser prebuild
  extracts it into `@modeler/grammar`'s exported `TTR_GRAMMAR_VERSION`; update any test asserting the
  version string.

- [x] **D2 — CHANGELOG.** In `packages/grammar` (and repo `CHANGELOG.md` if present) document the
  three breaking changes under **3.0**: `schema map → binding`; `domain` block/`.ttrd` removed,
  replaced by `def area`; `.ttr → .ttrm`. List the migration steps (point to D6).

- [x] **D3 — Re-port + conform all three targets.** The A/B/C grammar edits changed the parse shape
  (`binding` schema, `area` def, `.ttrm`). Mirror them in every target's hand-written walker +
  semantics, then prove lock-step:
  - **Kotlin:** update `ttr-parser` walker + `ttr-semantics` port; `./gradlew
    :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-semantics:test :packages:kotlin:ttr-writer:test`.
  - **Python:** rebuild so `generate-python-parser.sh` regenerates the parser; update the Python
    walker + semantics port (the `.ttrd`/`domain` handling and `map` schema literal); `cd
    packages/python/ttr-parser && pytest && ruff check && mypy --strict`.
  - **Conformance:** `pnpm --filter @modeler/conformance dump-all` (commit `out-ts{,-sem}`), then the
    Kotlin and `py-dump`/`py-sem-dump` diffs; all three targets must agree on the new shape.

- [ ] **D4 — Publish + downstream consume (no sync).**
  - Tag-driven publish per `PUBLISHING.md`: `kotlin/v<x.y.z>` (Maven) and `python/v<x.y.z>` (PyPI).
  - In `ai-platform`: **bump the `ttr-parser` dependency version** to the new release (no grammar
    copy). Update its **agent registry** to discover `def area` instead of `.ttrd`, and its loader
    for `schema binding` + `.ttrm`. Run ai-platform's suite. (Cross-repo PR lands in lockstep with
    the modeler release; do not announce the release until it is green.)

- [x] **D5 — Dead-code cleanup (`.ttrl`).** Remove the dead `.ttrl` layout-sidecar references
    flagged in Stage C (`designer/src/fs/file-system.ts`, `Header.tsx` `accept`, `App.tsx`),
    consistent with CLAUDE.md decision D4 (layout now lives in `.ttrg`). Guard with a test that the
    designer still opens `.ttrg` layout correctly.

- [x] **D6 — Migration helper.** Extend the `migrate` CLI (`packages/migrate`) with a `phase0`
    subcommand (or a standalone script) that, given a project dir: renames `*.ttr` → `*.ttrm`,
    rewrites `schema map` → `schema binding`, rewrites the inline `mapping:` property → `binding:`
    (Stage AA), and converts `domain {…}` blocks in `.ttrd` files into `def area {…}` in a `.ttrm`
    file. Add a fixture-based test (before/after project tree). `ai-models` (Stage E) is the first
    real consumer of this helper.

- [ ] **D7 — Final verify + release.**
  - Full gates: `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`.
  - `pnpm --filter @modeler/integration-tests test`.
  - Phase 0 DONE checklist (INDEX) all ticked. Only then tag the Kotlin grammar release per
    `PUBLISHING.md` (`kotlin/v<x.y.z>`), with the ai-platform PR landing in lockstep.

- [x] **D8 — Commit.** `Section Phase0-D: grammar 3.0, cross-repo sync, migration`.
