# Phase 1.4 — Loader + dedent + error listeners

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Phase 1.3 (model + walker) DoD met. The walker compiles and the porting
  tests are green.
- Read [`../../contracts.md`](../../contracts.md) §2.1–§2.3 (TtrLoader,
  ParseResult, ParseError) and §2.9 (Dedent).

**Reference files (port from):**
- ai-platform `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/loader/TtrLoader.kt`
- ai-platform `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/walker/Dedent.kt`

**Tasks:**

- [x] **1.4.1 — Port `Dedent.kt`** to
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/walker/Dedent.kt`.
      Behaviour is unchanged (Python `textwrap.dedent` semantics). Only
      rename the package.

- [x] **1.4.2 — Port `TtrLoader.kt`** to
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/loader/TtrLoader.kt`.
      Adjust imports (`shared.ttr.parser.*` → `org.tatrman.ttr.parser.*`),
      including the generated parser/lexer (`org.tatrman.ttr.parser.generated.TTRLexer`,
      `TTRParser`).

- [x] **1.4.3 — Wire `ParseError.code = DiagnosticCode.ParseError`.** The
      ai-platform version of `ParseError` has no `code` field. Add it per
      contract §2.3 (default `DiagnosticCode.ParseError`). Update the error
      listener to set the field; downstream consumers can ignore it.

- [x] **1.4.4 — Add `DiagnosticCode` enum.** Create
      `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/diagnostics/DiagnosticCode.kt`
      mirroring `packages/parser/src/diagnostics.ts` exactly (every value
      identical). Also add `DiagnosticSeverity` enum (Error, Warning,
      Information, Hint).

- [x] **1.4.5 — Verify `parseDirectory` exclusions match TS.** Ensure the
      Kotlin loader skips `.modeler`, `node_modules`, `.git` directories per
      `packages/parser/src/index.ts`'s `parseDirectory`. The current
      ai-platform implementation only filters file extensions (`.ttr` in,
      `.ttrg` out). Add directory exclusions.

- [x] **1.4.6 — Verify all 1.2 tests pass.** Run
      `./gradlew :packages:kotlin:ttr-parser:test`. Every spec except
      `DumpSchemaSpec` should be green. `DumpSchemaSpec` stays red (covered by
      stage 1.6).

- [x] **1.4.7 — Run `ktlintCheck`.** Fix any remaining issues via
      `./gradlew :packages:kotlin:ttr-parser:ktlintFormat` then verify with
      `./gradlew :packages:kotlin:ttr-parser:ktlintCheck`.

**Stage DoD:**
- All seven tasks checked.
- `./gradlew :packages:kotlin:ttr-parser:test` is green (except
  `DumpSchemaSpec`).
- `ktlintCheck` green.
- The full v2.2 grammar — drill_map, inline mappings, search blocks,
  packages/imports, all 14 def kinds — parses correctly. (DrillMapParserSpec,
  InlineMappingsSpec, SearchBlockSpec, TtrLoaderSpec all green.)

**Notes on execution (2026-05-30):**
- **1.4.1 (`Dedent.kt`) and 1.4.2 (`TtrLoader.kt`) were ported in stage 1.3**
  so the walker could be exercised. This stage added the remaining pieces:
  `DiagnosticCode`/`DiagnosticSeverity` enums (1.4.4), `ParseError.code` wired
  to the typed enum (1.4.3 — the `err()` site now passes
  `DiagnosticCode.DuplicateSearchProperty`), and the `parseDirectory`
  `.modeler`/`node_modules`/`.git` prune (1.4.5, rewritten with `walkFileTree`),
  plus a `ParseDirectorySpec`.
- **`ParseWarning.code` deferred (deviation from contracts.md §2.3).** The
  walker emits several warnings with no dedicated `DiagnosticCode` (empty search
  block, unknown language code, duplicate pattern, keyword whitespace,
  graph-block-ignored). In the canonical TS architecture some of these (e.g.
  `fuzzy-without-searchable`) are *validator* diagnostics, not parser warnings.
  Coding `ParseWarning` cleanly needs either enum additions or moving those
  warnings to the Phase 2 validator — deferred until that alignment. Documented
  inline in `TtrLoader.kt`.
