# Phase 1.2 — Tests first: Kotest fixtures + harness

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Phase 1.1 (scaffolding) DoD met.
- Read [`../../contracts.md`](../../contracts.md) §2.1–§2.4 (Loader, ParseResult,
  ParseError, SourceLocation).

**Reference files:**
- ai-platform tests to port:
  - `shared/libs/kotlin/ttr-parser/src/test/kotlin/shared/ttr/parser/loader/TtrLoaderSpec.kt`
  - `shared/libs/kotlin/ttr-parser/src/test/kotlin/shared/ttr/parser/loader/InlineMappingsSpec.kt`
  - `shared/libs/kotlin/ttr-parser/src/test/kotlin/shared/ttr/parser/loader/DrillMapParserSpec.kt`
  - `shared/libs/kotlin/ttr-parser/src/test/kotlin/shared/ttr/parser/walker/DedentSpec.kt`
- modeler TS tests that exercise grammar v2.2 (for fixture content):
  - `packages/parser/src/__tests__/grammar-v2.test.ts`
  - `packages/parser/src/__tests__/search-block.test.ts`
  - `packages/parser/src/__tests__/inline-mappings.test.ts`
  - `packages/parser/src/__tests__/drill-map.test.ts`

**Goal of this stage:** every test is **red** (compilation fails because the
production code isn't there yet) — confirming that stage 1.3+ is delivering
something specific. Per the planning skill TDD requirement.

**Tasks:**

- [x] **1.2.1 — Port test fixtures from ai-platform.** Copy the four `*Spec.kt`
      files into
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/...`
      preserving subpackage structure (`loader/`, `walker/`). Replace
      `package shared.ttr.parser.*` with `package org.tatrman.ttr.parser.*`
      and `import shared.ttr.parser.*` with `import org.tatrman.ttr.parser.*`
      throughout.

- [x] **1.2.2 — Add new tests for v2.0.0 search-block correctness.** Create
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/loader/SearchBlockSpec.kt`
      asserting:
      - `def column X { type: int, search { searchable: true } }` parses; the
        `ColumnDef.search.searchable` is `true`; **`ColumnDef` has NO top-level
        `searchable` field** (compile-time check via `ColumnDef::class.memberProperties.none { it.name == "searchable" }`).
      - `def column X { type: int, searchable: true }` (old v1 shape) **fails to
        parse** — the test asserts `result.errors.isNotEmpty()`.
      - `def attribute Y { type: text, search { fuzzy: true } }` warns
        `ttr/fuzzy-without-searchable`.

- [x] **1.2.3 — Add a new test for the `SourceLocation` superset (D4).**
      Create
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/model/SourceLocationSpec.kt`
      with cases for:
      - Single-token span: `line == endLine`, `endColumn == column + token.length`.
      - Multi-token span (e.g. a `def entity X { ... }` spanning multiple
        lines): `endLine > line`, `offsetEnd - offsetStart` equals the byte
        length of the source slice.
      - Multi-token-span invariant: `endColumn == stopToken.column + stopToken.length`
        (NOT `startColumn + spanLength`). This is the bug-prevention check from
        modeler's `CLAUDE.md`.

- [x] **1.2.4 — Add a new test for `PropertyValue.source`.** Create
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/model/PropertyValueSourceSpec.kt`
      asserting every `PropertyValue` variant carries a `source` field after
      parsing a fixture like `def model M { version: "1.2.3", tags: ["a", "b"] }`.

- [x] **1.2.5 — Add a new test for `IdValue.parts`.** Same file or sibling,
      asserting that `mapping: db.dbo.fk_artikl_produkt` produces an
      `IdValue` with `parts == listOf("db", "dbo", "fk_artikl_produkt")`.

- [x] **1.2.6 — Add a new test for the conformance dump schema.** Create
      `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/conformance/DumpSchemaSpec.kt`
      asserting that the (to-be-implemented) `ConformanceDump.dump(result)`
      produces JSON matching the schema in `contracts.md` §5 — keys
      alphabetical, `kind` = TTR keyword, no `SourceLocation` anywhere.
      Use Kotlinx Serialization JSON's pretty printer, then string-compare
      against an expected JSON snapshot stored alongside the test.

- [x] **1.2.7 — Run the tests; confirm they fail with compile errors only.**
      `./gradlew :packages:kotlin:ttr-parser:test` — expect compile errors on
      `TtrLoader`, `ColumnDef.search`, `SourceLocation.endLine`,
      `PropertyValue.X.source`, `IdValue.parts`, `ConformanceDump`. **Not**
      runtime failures (no production code yet to fail at runtime). If any
      test compiles and runs (e.g. by depending on something that already
      exists by accident), revisit — the test is testing the wrong thing.

- [x] **1.2.8 — Commit "red tests".** `git commit -m "Phase 1.2: failing
      Kotest specs for ttr-parser v2.0.0 + Kotlin shape upgrades"`.

**Stage DoD:**
- Eight tasks checked.
- `./gradlew :packages:kotlin:ttr-parser:test` fails with compile errors that
  point at concrete missing types — every error corresponds to a contract in
  `contracts.md`.
- No runtime test failures (because no production runtime yet).
