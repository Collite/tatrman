# Phase 1.3 — Model + walker (with v2.0.0 fixes and D4 superset)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–2 days.

**Pre-flight:**
- Phase 1.2 (tests first) DoD met — tests are red, fail with compile errors
  matching the contract.
- Read [`../../contracts.md`](../../contracts.md) §2.4–§2.7 (SourceLocation,
  Definition hierarchy, PropertyValue, other model types).
- Read [`../../AST-NAMING.md`](../../AST-NAMING.md) fully.

**Reference files (port from):**
- ai-platform `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/model/Definition.kt`
- ai-platform `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/walker/TtrWalker.kt`
- modeler TS reference (for D4 / v2.0.0 corrections):
  - `packages/parser/src/ast.ts`
  - `packages/parser/src/walker.ts` (especially `makeSourceLocation` — note the
    `endColumn = stopToken.column + stopTokenLength` invariant)

**Tasks:**

- [x] **1.3.1 — Port `Definition.kt` with v2.0.0 + D3 + D4 corrections.** Create
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/model/Definition.kt`
      based on the ai-platform file with:
      - Package: `org.tatrman.ttrm.parser.model`.
      - **Drop top-level `searchable` from `ColumnDef`** (moves into
        `search: SearchHintsValue`). **Keep `indexed`** top-level — it is a
        column-level grammar property, matches canonical TS, and is NOT part of
        `SearchHintsValue` (see "Deviations recorded" below).
      - **Drop top-level `searchable` from `AttributeDef`**. Move into `search`.
      - Adopt the richer `SourceLocation` per contract §2.4
        (file, line, column, endLine, endColumn, offsetStart, offsetEnd).
      - Keep all Kotlin type names as-is (`Er2DbEntityDef`, `SearchHintsValue`,
        `LocalizedStringValue` — see AST-NAMING.md for justification).

- [x] **1.3.2 — Add `source` field to every `PropertyValue` variant** (D4).
      Touches: `StringValue`, `TripleStringValue` (new — see 1.3.3), `NumberValue`,
      `BoolValue`, `NullValue` (becomes data class), `IdValue`, `ListValue`,
      `ObjectValue`, `FunctionCall`. The sealed interface gets
      `val source: SourceLocation`.

- [x] **1.3.3 — Add `TripleStringValue` variant to `PropertyValue`.** Today
      ai-platform's walker folds triple-strings into `StringValue` after dedent.
      Split them out per TS shape so the dump schema (§5) can distinguish them.

- [x] **1.3.4 — Add `parts: List<String>` to `PropertyValue.IdValue`.** Walker
      splits the reference on `.` when constructing it.

- [x] **1.3.5 — Add `PackageDeclaration` type.** Mirrors `ImportStatement`'s
      shape. Populated by walker from the `packageDecl` rule.

- [x] **1.3.6 — Port `TtrWalker.kt` with the new `SourceLocation` superset.**
      Copy from ai-platform; rewrite `toSourceLocation(ctx)` to populate all
      seven fields:
      ```kotlin
      private fun ParserRuleContext.toSourceLocation(file: String): SourceLocation {
          val start = this.start
          val stop = this.stop ?: this.start
          val stopText = stop.text ?: ""
          return SourceLocation(
              file = file,
              line = start.line,
              column = start.charPositionInLine,
              endLine = stop.line,
              endColumn = stop.charPositionInLine + stopText.length,
              offsetStart = start.startIndex,
              offsetEnd = stop.stopIndex + 1,   // ANTLR stopIndex is inclusive; we expose exclusive
          )
      }
      ```
      **Critical:** the multi-token-span invariant from `CLAUDE.md` —
      `endColumn = stop.charPositionInLine + stopText.length` — NOT
      `startColumn + spanLength`. Test `SourceLocationSpec` (1.2.3) gates this.

- [x] **1.3.7 — Update walker to construct `PropertyValue` variants with
      `source`.** Every existing call site to `PropertyValue.StringValue(raw)`
      etc. becomes `PropertyValue.StringValue(raw, ctx.toSourceLocation(file))`.
      Update `IdValue` construction to populate `parts`.

- [x] **1.3.8 — Update walker to handle `searchBlock` correctly per v2.0.0.**
      Map `searchable: true` / `fuzzy: true` sub-properties inside `search { }`
      onto `SearchHintsValue.searchable` / `SearchHintsValue.fuzzy`. If the
      walker encounters a top-level `searchable: true` on a column or attribute
      (v1 syntax), emit a parse error — the v2 grammar should reject this at
      the syntactic level, but defend with a walker-level check too.

- [x] **1.3.9 — Run the tests; iterate until green.**
      `./gradlew :packages:kotlin:ttr-parser:test`. The tests from 1.2 should
      go from compile-error → pass. If `ConformanceDumpSchemaSpec` still fails,
      that's fine — it's owned by stage 1.6.

**Stage DoD:**
- All nine tasks checked.
- Tests from 1.2 (except `DumpSchemaSpec`) are green.
- Walker compiles cleanly against the generated ANTLR Java classes.
- `./gradlew :packages:kotlin:ttr-parser:ktlintCheck` green (run after
  formatting — `ktlintFormat` fixes most issues automatically).

**Deviations recorded during execution (2026-05-30):**
- **`indexed` stays top-level on `ColumnDef`.** The contract draft said to drop
  `searchable` *and* `indexed`, but the canonical TS `ColumnDef` keeps `indexed`
  (it is a column-level grammar property, not part of `SearchHintsValue`).
  Dropping it would break conformance against TS. Only `searchable` is dropped.
  `contracts.md` §2.5 and `AST-NAMING.md` corrected accordingly.
- **Definition source span includes `def`.** The grammar is
  `definition : DEF objectDefinition`; the ai-platform walker used
  `location(od)` (starts at the kind keyword, excludes `def`). The canonical TS
  walker (`walkDefinition` on `DefinitionContext`) spans the whole `def … ` form,
  so the Kotlin walker now reads the parent (`defSource(od)`) to match.
- **Loader + Dedent pulled forward from stage 1.4.** To actually *exercise* the
  walker (and turn the 1.2 specs green), `Dedent.kt` and `TtrLoader.kt`
  (`parseString`/`parseFile`/`parseDirectory` + `ParseResult`/`ParseError`/
  `ParseWarning`) were ported here. Stage 1.4 still owns the `DiagnosticCode`
  enum, `ParseError.code`, and the `.modeler`/`node_modules`/`.git` directory
  exclusions.
- **`ConformanceDump` stub.** A throwing `ConformanceDump.dump` stub was added so
  the test module compiles; `DumpSchemaSpec` stays red at runtime until stage 1.6.

**Common pitfalls (from `CLAUDE.md`):**
- Forgetting the `endColumn` invariant for multi-token spans. The
  `SourceLocationSpec` test catches this; do not "fix" the test to match a
  wrong impl.
- Using `as Any` or `Suppress` to silence type-mismatch errors when porting.
  If the type doesn't fit, the model shape is wrong — fix the model.
