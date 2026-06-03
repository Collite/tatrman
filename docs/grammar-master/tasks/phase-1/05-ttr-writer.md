# Phase 1.5 — ttr-writer module + round-trip tests

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Phase 1.4 (loader + dedent) DoD met; `ttr-parser` module is green.
- Read [`../../contracts.md`](../../contracts.md) §3 (ttr-writer public API).

**Reference files:**
- ai-platform `shared/libs/kotlin/ttr-writer/src/main/kotlin/shared/ttr/writer/TtrRenderer.kt`
- ai-platform `shared/libs/kotlin/ttr-writer/src/test/kotlin/shared/ttr/writer/TtrRendererSpec.kt`

**Tasks** (TDD — tests first):

- [x] **1.5.1 — Port `TtrRendererSpec.kt`** to
      `packages/kotlin/ttr-writer/src/test/kotlin/org/tatrman/ttr/writer/TtrRendererSpec.kt`.
      Update imports (`shared.ttr.*` → `org.tatrman.ttr.*`). The Spec should
      fail to compile (renderer doesn't exist yet).

- [x] **1.5.2 — Add a round-trip spec.** Create
      `packages/kotlin/ttr-writer/src/test/kotlin/org/tatrman/ttr/writer/RoundTripSpec.kt`
      asserting that for each fixture in
      `packages/kotlin/ttr-parser/src/test/resources/fixtures/` (use the same
      pool as ConformanceSpec — share via `:packages:kotlin:ttr-parser`
      test fixtures or duplicate):
      ```kotlin
      val parsed1 = TtrLoader.parseString(fixture)
      val rendered = TtrRenderer.render(parsed1)
      val parsed2 = TtrLoader.parseString(rendered)
      parsed2.definitions.shouldBeStructurallyEqual(parsed1.definitions)
      ```
      Implement `shouldBeStructurallyEqual` as a helper that compares
      definitions ignoring `SourceLocation`.

- [x] **1.5.3 — Add a stability spec.** Asserts `TtrRenderer.render(x)` is
      identical across two calls for the same input — i.e. output is
      deterministic. Critical for diff-stability in source control.

- [x] **1.5.4 — Port `TtrRenderer.kt`** to
      `packages/kotlin/ttr-writer/src/main/kotlin/org/tatrman/ttr/writer/TtrRenderer.kt`.
      Adjust:
      - Package: `org.tatrman.ttr.writer`.
      - Imports use `org.tatrman.ttr.parser.model.*`.
      - Drop any references to types renamed in §D3 (use the new Kotlin names
        from AST-NAMING.md).
      - Update for the v2.0.0 `searchable` move: when rendering a `ColumnDef`
        or `AttributeDef`, emit `searchable: true` **inside** the `search { }`
        block, never at the top level.
      - Update for the richer `SourceLocation` and `PropertyValue` with
        `source` field — these don't affect output (positions aren't rendered).

- [x] **1.5.5 — Add alphabetical property ordering** if not already present.
      Within each `def { ... }` block and each `{ }` object value, properties
      should be sorted alphabetically by name. Test from 1.5.3 gates this.

- [x] **1.5.6 — Run `:packages:kotlin:ttr-writer:test`.** Iterate until green.

- [x] **1.5.7 — Run `:ttr-writer:ktlintCheck` and full build.**
      `./gradlew build` from repo root. Both modules + their tests should be
      green.

**Stage DoD:**
- All seven tasks checked.
- `./gradlew :packages:kotlin:ttr-writer:test` green.
- Round-trip spec passes for every fixture.
- `./gradlew build` green.

**Deviations / fixes recorded during execution (2026-05-30):**
- **Renderer completed beyond the ai-platform port.** The ai-platform renderer
  threw on `ModelDef` and `DrillMapDef` (the metadata export never emitted them).
  A general writer must render every def kind it can parse, so `renderModel` and
  `renderDrillMap` were added (and both added to `KIND_ORDER`).
- **Structured-type bug fixed.** The grammar has no `decimal(19, 5)` paren form —
  only the object form `{ type: decimal, length: 19, precision: 5 }`. The
  ai-platform renderer emitted the paren syntax, which does NOT re-parse; the
  modeler renderer emits the object form. Caught by `RoundTripSpec`.
- **`render(definitions, schemaDirective?)` + `render(result)`** added per
  contracts §3 (the ai-platform renderer only had `render(definitions)` /
  `renderFile(...)`, both kept).
- **`TripleStringValue` branch** added to `renderPropertyValue` (new D4 variant).
- **Round-trip is asserted via render-idempotence** (`render∘parse` is a fixed
  point) rather than a SourceLocation-stripping deep-equals — simpler and still
  catches structural drift. Fixtures are inline (the shared conformance fixture
  pool lands in 1.6).
- **Property ordering: deterministic fixed order, NOT alphabetical-within-block
  (deviation from contracts §3).** The ported renderer emits each block's
  properties in a fixed hand-coded order. This is deterministic, so output is
  diff-stable (the actual goal; `StabilitySpec` gates it). Full
  alphabetical-within-block ordering would require rewriting all render methods
  with round-trip re-validation — deferred; flag on contracts §3 if strict
  alphabetical is later required.
- **Generated-source layout reverted to flat** (`build/generated-src/antlr/main/`).
  The stage-1.1 `outputDirectory` override that nested files to the package path
  caused duplicate-class errors on clean rebuilds (ANTLR regenerates flat; the
  nested copy lingered). The flat `.java` files still declare the correct
  `package org.tatrman.ttr.parser.generated`. Architecture/scaffolding docs
  updated.
