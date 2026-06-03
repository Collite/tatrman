# Phase 1.6 — Conformance harness (TS↔Kotlin AST diff)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.

**Pre-flight:**
- Phase 1.5 (ttr-writer) DoD met; both Kotlin modules are green.
- Read [`../../architecture.md`](../../architecture.md) §"Conformance harness"
  and [`../../contracts.md`](../../contracts.md) §5 (JSON dump schema).
- Read [`../../AST-NAMING.md`](../../AST-NAMING.md) — the rename map is
  load-bearing for the harness.

**Tasks** (TDD — tests first):

- [x] **1.6.1 — Curate fixture set.** Create `tests/conformance/fixtures/`
      with 25–40 `.ttr` files covering:
      - One per def kind (15 files: model, table, view, column, index,
        constraint, fk, procedure, entity, attribute, relation, er2db_entity,
        er2db_attribute, er2db_relation, query, role, er2cnc_role, drill_map).
      - Triple-string edge cases (4 files: leading newline, blank-line
        normalisation, mixed indent, no-newline-after-quotes).
      - Search-block cases (3 files: minimal, full, fuzzy-without-searchable).
      - Package/import cases (3 files: default package, named import, wildcard
        import non-recursion).
      - Inline mapping cases (4 files: bare-id mapping, block mapping with
        columns, relation FK mapping, attribute target).
      - Recovery / partial cases (2 files): only on success path; recovery is
        out of scope for conformance.
      Seed by copying from `packages/parser/src/__tests__/` fixtures and
      `samples/` where appropriate.

- [x] **1.6.2 — Implement `ConformanceDump.dump` in Kotlin.** Create
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/conformance/ConformanceDump.kt`
      producing JSON per `contracts.md` §5:
      - Keys sorted alphabetically.
      - `kind` = TTR keyword (lowercased; e.g. `er2db_entity`, `drill_map`).
      - No `SourceLocation` anywhere.
      - Property names = TTR surface names (use the AST-NAMING.md rename
        map). Hard-code the map in one place — `ConformanceDump.kt` — so it's
        the single source of truth on the Kotlin side.
      - Uses `kotlinx.serialization.json` for output (add dependency to
        `ttr-parser/build.gradle.kts`: `implementation(libs.kotlinx.ser.json)`
        — add to `libs.versions.toml` if not already present, version
        `1.10.0` per ai-platform's pin).
      `DumpSchemaSpec` from 1.2.6 turns green.

- [x] **1.6.3 — Implement `conformanceDump` in TypeScript.** Create
      `tests/conformance/dump.ts` consuming `ParseResult` from `@modeler/parser`
      and emitting identical JSON. Use `JSON.stringify(obj, Object.keys(obj).sort(), 2)`
      with a recursive sorter helper. Apply the same TTR-surface-name rename
      map (declare it once at the top of `dump.ts`).

- [x] **1.6.4 — Add a TypeScript unit test for the dump.** Create
      `tests/conformance/dump.test.ts` (Vitest) parsing two or three fixtures
      and snapshot-asserting the JSON output. Catches accidental dump
      changes.

- [x] **1.6.5 — Add a Kotlin spec dumping all fixtures.** Create
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/conformance/ConformanceSpec.kt`
      that iterates over fixtures, parses each, dumps to
      `build/conformance/kt/<fixture>.json`, and asserts no parse errors.

- [x] **1.6.6 — Add a TS script dumping all fixtures.** `tests/conformance/run-ts.ts`
      iterates fixtures, parses with `@modeler/parser`, dumps to
      `tests/conformance/out-ts/<fixture>.json`. Run via `pnpm tsx`.

- [x] **1.6.7 — Implement the diff tool.** `tests/conformance/diff.ts`
      reads both output dirs, byte-compares each pair, reports any drift with
      a precise per-fixture diff (use Node's `diff` package). Exit 0 on match,
      1 on drift.

- [x] **1.6.8 — Wire CI workflow.** Create `.github/workflows/conformance.yml`
      with two parallel jobs (setup-node + setup-java) emitting their dumps
      into a shared GHA artifact, and a third job that downloads and diffs.
      Path filters: `packages/grammar/**`, `packages/parser/**`,
      `packages/kotlin/ttr-parser/**`, `tests/conformance/**`. Triggers:
      `pull_request` + `push: branches: [main]`.

**Stage DoD:**
- All eight tasks checked.
- `tests/conformance/diff.ts` exits 0 locally for every fixture (27 fixtures).
- CI workflow runs green on a representative PR (test by opening a no-op PR
  touching `packages/grammar/`).
- `DumpSchemaSpec` from 1.2 is now green (un-parked — bang removed).
- **Try it the bad way:** intentionally break one Kotlin walker production
  (e.g. set `EntityDef.labelPlural` to always be null), confirm the harness
  catches it — then revert. ✅ Verified: the break surfaced `09-entity.json`
  drift and `diff.ts` exited 1; reverted, all 27 match again.

**Notes / outcomes (2026-05-30):**
- **The harness caught a real bug on its first run.** TS `dedent` did NOT drop
  the leading newline after `"""` (contract §2.9 requires it; the Kotlin
  `Dedent` and `DedentSpec` enforce it). Three triple-string fixtures drifted.
  **Fixed the TS side** (`packages/parser/src/walker.ts` `dedent` rewritten to
  mirror the Kotlin algorithm: drop leading newline, longest common space/tab
  prefix, blank lines → empty). No existing TS test depended on the old
  behaviour; all 126 TS parser tests stay green. This is exactly the drift the
  harness exists to catch.
- **Canonical JSON printer instead of raw kotlinx pretty-print.** The Kotlin
  dumper builds a `kotlinx` `JsonElement` tree but serialises via a small
  printer matching JS `JSON.stringify(_, null, 4)` byte-for-byte (kotlinx's
  default pretty-printer differs on empty collections and renders whole doubles
  as `0.0`). Whole numbers are emitted as integers on both sides.
- **Present-only normalisation.** Both dumpers omit false booleans and empty
  lists/objects, so a TS-absent field and a Kotlin-default field both vanish —
  essential for parity (TS uses optional fields, Kotlin uses defaults).
- **Procedure/query params normalised** from the Kotlin walker's `ObjectValue`
  representation to the TS `ParameterDef` surface `{ name, type?: {name}, label?,
  direction? }`. **`relation.join`** is unwrapped on the TS side (TS stores a
  `ListValue`; Kotlin stores the item list) so both emit the same array.
- **New workspace member `@modeler/conformance`** (`tests/conformance`) added to
  `pnpm-workspace.yaml`; carries `dump`/`diff` (tsx) + a Vitest snapshot. The
  3-job `conformance.yml` (ts-dump ∥ kt-dump → diff) is wired per §6.2.
- **Generated dumps are gitignored** (`tests/conformance/out-ts/`; Kotlin dumps
  live under `build/`).
