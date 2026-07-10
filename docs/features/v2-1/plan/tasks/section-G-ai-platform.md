# Section G — ai-platform mirror (Kotlin parser + synthesizer + validator)

Sync the grammar to ai-platform, regenerate the Kotlin parser, mirror Sections C–E on the Kotlin side. The PR aims to ship both repos together as one deployment unit (different from v1.1's "modeler ships independently" approach).

**Depends on:** Sections B–F (modeler-side feature complete).

**Spec:** [`docs/v2-1/design/grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md) §4 + §5.

**Important:** The `ai-platform` repo (`~/Dev/ai-platform`) is **not mounted in the modeler workspace**. Every path and symbol named below is written against the *described* behaviour and the vendoring contract in `CLAUDE.md`. **Verify each one against the actual ai-platform code before editing.** When in doubt, search the actual repo:
```bash
grep -rn "Er2dbEntity\|er2db_entity\|TTRParser" ~/Dev/ai-platform/shared
```

Useful reference for ai-platform conventions: [`docs/ai-platform-upgrade.md`](../../../ai-platform-upgrade.md) and [`docs/features/search-block/T5-ai-platform-yaml.md`](../../../features/search-block/T5-ai-platform-yaml.md).

---

## Tasks

### G.0 — Switch the Cowork folder (if running through Cowork)

- [ ] If you (or the agent) is running through Cowork mode mounted on `~/Dev/modeler`, the ai-platform repo is not accessible. Either:
  - Mount `~/Dev/ai-platform` instead (switch folders in Cowork settings), OR
  - Do this section directly from a terminal with both repos available.

  Sync and check commands run from the **modeler** side; the actual Kotlin edits happen in the **ai-platform** side.

### G.1 — Create the ai-platform branch

- [ ] **In `~/Dev/ai-platform`:**
  ```bash
  cd ~/Dev/ai-platform
  git checkout main
  git pull
  git checkout -b feat/v2.1-inline-mappings
  ```

### G.2 — Sync the grammar

- [ ] **From `~/Dev/modeler` (still on `feat/v2.1-inline-mappings` branch):**
  ```bash
  cd ~/Dev/modeler
  packages/grammar/scripts/sync-to-ai-platform.sh ~/Dev/ai-platform
  packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform   # hashes must match
  ```
  The synced file lands at `<ai-platform>/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4` with a vendoring header carrying the modeler commit hash.

- [ ] **Spot-check the synced grammar.**
  ```bash
  grep -n "MAPPING\|mappingProperty\|columns" ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4
  ```
  Expect the `MAPPING` token, `mappingProperty`/`mappingValue`/`mappingBlock`/`mappingColumnsProperty` rules, and the relaxed `targetProperty`.

- [ ] **Verify the version marker.**
  ```bash
  head -10 ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4 | grep "@grammar-version"
  ```
  Should be `2.1`.

### G.3 — Regenerate the Kotlin parser

- [ ] **In ai-platform**, run its existing ANTLR build step. Locate it:
  ```bash
  grep -rn "antlr" ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/build.gradle.kts
  find ~/Dev/ai-platform -name "build.gradle*" -path "*ttr-parser*"
  ```
  Run the appropriate Gradle task (likely `./gradlew :shared:libs:kotlin:ttr-parser:generateGrammarSource` or `:generateKotlinGrammarSource`).

- [ ] **Confirm generated files have the new rules.**
  ```bash
  grep -l "mappingProperty\|MappingProperty\|MappingBlock" ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/build/generated/
  ```
  Expect hits in the generated `TTRParser.kt` (or whatever Kotlin generator output is named).

- [ ] **Run the existing parser-suite tests** to confirm no regressions:
  ```bash
  cd ~/Dev/ai-platform
  ./gradlew :shared:libs:kotlin:ttr-parser:test
  ```
  All existing 17 (or however many) parser tests should still pass.

### G.4 — Extend the Kotlin AST

- [ ] **Locate the AST node definitions.** Likely a Kotlin sealed class hierarchy under `<ai-platform>/shared/libs/kotlin/ttr-parser/src/main/kotlin/.../ast/`:
  ```bash
  find ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser -name "*.kt" | xargs grep -l "data class EntityDef\|sealed class Definition" 2>/dev/null
  ```

- [ ] **Add `MappingProperty` and helpers.** Mirror the TypeScript shape from `packages/parser/src/ast.ts`:

  ```kotlin
  sealed class MappingProperty {
      abstract val source: SourceLocation
  }
  data class MappingPropertyBareId(
      val id: Reference,
      override val source: SourceLocation
  ) : MappingProperty()

  data class MappingPropertyBlock(
      val target: TargetValue?,                       // sealed: Object or Reference
      val columns: List<MappingColumnEntry>?,         // entity-level only
      val fk: Reference?,                             // relation-level only
      override val source: SourceLocation
  ) : MappingProperty()

  sealed class TargetValue { abstract val source: SourceLocation }
  data class TargetObject(val obj: ObjectValue, override val source: SourceLocation) : TargetValue()
  data class TargetReference(val ref: Reference, override val source: SourceLocation) : TargetValue()

  data class MappingColumnEntry(
      val name: String,
      val value: MappingColumnValue,
      val source: SourceLocation
  )

  sealed class MappingColumnValue { abstract val source: SourceLocation }
  data class MappingColumnBareId(val id: Reference, override val source: SourceLocation) : MappingColumnValue()
  data class MappingColumnObject(val obj: ObjectValue, override val source: SourceLocation) : MappingColumnValue()
  ```

- [ ] **Add `mapping: MappingProperty?` field** to `EntityDef`, `AttributeDef`, `RelationDef` data classes.

- [ ] **Widen `Er2dbEntityDef.target` and `Er2dbAttributeDef.target`** to `TargetValue?` (was `ObjectValue?`). This accommodates the `target: <bareId>` relaxation in explicit declarations too.

### G.5 — Extend the Kotlin walker / AST builder

- [ ] **Locate the walker.** The Kotlin equivalent of `walker.ts` is typically named `AstBuilder.kt` or `TTRAstBuilder.kt`:
  ```bash
  find ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser -name "*.kt" | xargs grep -l "fun walkEntityDef\|override fun visitEntityDef" 2>/dev/null
  ```

- [ ] **Add `visitMappingProperty` (or the equivalent).** Mirror the TS structure from `packages/parser/src/walker.ts` `walkMappingProperty`. The Kotlin code path will call this from `visitEntityDef`, `visitAttributeDef`, `visitRelationDef`.

- [ ] **Add `visitTargetValue` helper** that accepts either an `id` or an `object_` child of `targetProperty` and produces a `TargetValue`.

- [ ] **Wire `mapping = visitMappingProperty(p.mappingProperty())`** into the three host walkers. Mirror the TypeScript approach: iterate the per-kind property list and dispatch by accessor (`p.descriptionProperty()`, `p.mappingProperty()`, etc.).

- [ ] **Update the explicit er2db_entity / er2db_attribute walkers** to use `visitTargetValue` so the `target: <bareId>` form works in explicit declarations on the Kotlin side too.

### G.6 — Add Kotlin parser unit tests

- [ ] **Locate the existing parser test directory.** Likely `<ai-platform>/shared/libs/kotlin/ttr-parser/src/test/kotlin/...`:
  ```bash
  find ~/Dev/ai-platform -name "*Test.kt" -path "*ttr-parser*"
  ```

- [ ] **Mirror the modeler test file** `packages/parser/src/__tests__/inline-mappings.test.ts`. Tests for each form, plus source-location assertion. Use whatever assertion library the Kotlin tests already use (Kotest / JUnit / etc. — match the existing style).

### G.7 — Extend the Kotlin semantic-table representation

- [ ] **Locate the symbol table / metadata index.** Likely under `<ai-platform>/metadata/` or `<ai-platform>/shared/libs/kotlin/.../symbols/`:
  ```bash
  grep -rn "Er2dbEntity\|er2db_entity" ~/Dev/ai-platform --include=*.kt | head
  ```

- [ ] **Add `mappingSource: MappingSource?`** to the symbol entry data class (or the Kotlin equivalent of `SymbolEntry`):
  ```kotlin
  sealed class MappingSource {
      object Explicit : MappingSource()
      data class Inline(val hostKind: String) : MappingSource()    // hostKind ∈ "entity", "attribute", "relation"
  }
  ```

- [ ] **Set `mappingSource = MappingSource.Explicit`** when an `er2db_*` symbol is added from a `def er2db_*` declaration. Mirror modeler's symbol-table.ts edit.

### G.8 — Implement the Kotlin synthesizer

- [ ] **Create `MappingSynthesizer.kt`** (or equivalent file) mirroring `packages/semantics/src/mapping-synthesizer.ts`. Same algorithm:
  - Walk `ast.definitions`.
  - For each entity with `mapping` block: synthesize one `Er2dbEntitySymbol` + one `Er2dbAttributeSymbol` per `columns` entry.
  - For each attribute with `mapping`: synthesize one `Er2dbAttributeSymbol`.
  - For each relation with `mapping`: synthesize one `Er2dbRelationSymbol`.
  - All carry `mappingSource = MappingSource.Inline(hostKind)`.
  - All have source locations pointing at the inline `mapping:` *value*.
  - Synthesized qnames use the host file's package, with `map` schema prefix.

- [ ] **Wire the synthesizer into the metadata loader.** After the per-file parse + symbol-table assembly, call `synthesizeMappings(symbolTable, uri, ast)`. Find the existing equivalent of modeler's `upsertDocument` call and add the synthesis call immediately after.

- [ ] **Mirror modeler's "schemaless" decision.** Synthesized symbols live in the project-level index, NOT in any per-file `DocumentSymbolTable` for the `map` schema. This is C6 in the design doc — confirm by reading it before coding.

### G.9 — Implement the Kotlin conflict validator

- [ ] **Add `validateDuplicateMappings()`** mirroring `packages/semantics/src/validator.ts`. Walks all qnames in the project index, groups er2db_* entries, emits the diagnostic on each side of any collision where at least one entry has `mappingSource = MappingSource.Inline(...)`.

- [ ] **Register the `ttr/duplicate-mapping` diagnostic code** in ai-platform's DiagnosticCode enum (or equivalent). Match the modeler-side string exactly: `"ttr/duplicate-mapping"`.

- [ ] **Wire `validateDuplicateMappings()`** into the project-validation pipeline alongside the existing validators (`ttr/duplicate-definition`, etc.).

### G.10 — Add Kotlin synthesizer + validator tests

- [ ] **Mirror modeler's `mapping-synthesizer.test.ts` and `duplicate-mapping.test.ts`** on the Kotlin side. Same shapes, same expected qnames.

- [ ] **Share a fixture set if possible.** If ai-platform's test infrastructure can read modeler's `samples/broken/v2.1/` fixtures directly (or via a copied snapshot), use them — guarantees the two sides interpret the same input identically.

### G.11 — Run ai-platform's full test suite

- [ ] **Build + test.**
  ```bash
  cd ~/Dev/ai-platform
  ./gradlew build
  ./gradlew test
  ```
  All green. Pay attention to:
  - The existing TTR parser-suite (17 cases per `grammar-v1-1-changes.md` §6) — no regressions.
  - The metadata loader's existing tests — synthesized symbols shouldn't change the loader's output for projects that don't use inline mappings.

### G.12 — Verify sync hashes

- [ ] **From modeler:**
  ```bash
  cd ~/Dev/modeler
  packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform
  ```
  Exit 0. If it fails, ai-platform has edited the vendored copy — re-sync.

---

## Verification

- [ ] `check-sync.sh ~/Dev/ai-platform` returns 0.
- [ ] ai-platform's Kotlin parser regenerates without errors.
- [ ] ai-platform's existing parser tests all pass (no regressions from the grammar change).
- [ ] New Kotlin inline-mapping parser tests pass.
- [ ] New Kotlin synthesizer tests pass.
- [ ] New Kotlin duplicate-mapping validator tests pass.
- [ ] ai-platform's full `./gradlew build && ./gradlew test` is green.
- [ ] Each fixture under modeler's `samples/broken/v2.1/`, loaded into ai-platform's metadata service, emits exactly `ttr/duplicate-mapping`.

## Notes / gotchas

- **Single deployment unit.** Unlike v1.1, neither side ships in isolation. Both PRs are reviewed together and merging is coordinated. If the modeler PR is merged before the ai-platform PR, ai-platform's metadata service breaks on any file containing inline mappings — and vice versa.
- **Vendoring header.** `sync-to-ai-platform.sh` prepends a `// Vendored from modeler@<commit-hash> — DO NOT EDIT DIRECTLY` line. ai-platform should never edit the vendored grammar by hand; if a fix is needed, fix it in modeler and re-sync.
- **Kotlin equivalents of TypeScript discriminated unions** are sealed classes. The grammar-changes doc §4.3 shows the canonical Kotlin shape — mirror it exactly so the two sides' representations stay readable side-by-side.
- **The metadata service is the consumer of synthesized symbols.** Make sure its loader treats `MappingSource.Inline` and `MappingSource.Explicit` identically for serving purposes (lookups, foreign-key resolution, etc.). They're only distinguished by the validator and by any future Designer-side UI hint.
- **No `.ttrg` parsing needed.** Inline mappings are a `.ttr`-only feature. ai-platform continues to ignore `.ttrg` files entirely.
- **Don't touch ai-platform's YAML loader or YAML→TTR converter for this feature.** Those layers are out of scope for v2.1 — they emit explicit `def er2db_*` today and can continue to do so. The inline form is a user-authoring sugar, not a generated-output shape (unless you decide otherwise in a future release).
- **If the gradle generator name differs from what's documented**, find it by searching the build file: `grep -n "antlr\|grammar" ~/Dev/ai-platform/shared/libs/kotlin/ttr-parser/build.gradle.kts`.
- **Watch for snake_case vs camelCase** in the qname kind token. Modeler's qname is `<pkg>.map.er2dbEntity.<name>` (camelCase, matching the AST `kind` field) or `<pkg>.map.er2db_entity.<name>` (snake_case, matching the keyword) — verify which one the Kotlin side uses today and match it. The two sides MUST produce identical qnames for cross-loader fixtures to round-trip.
