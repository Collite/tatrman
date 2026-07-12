# Grammar-master migration — make Modeler the canonical owner of TTR

**Status:** Plan v1, 2026-05-30. Two-phased refactor that removes the
modeler↔ai-platform grammar vendoring step and reduces (eventually eliminates)
the cross-repo duplication of walker and loader-semantics work.

## Why

Today, every grammar change is a two-repo dance: edit `TTR.g4` in modeler →
`sync-to-ai-platform.sh` → bump version markers in lockstep → regenerate
ai-platform's Kotlin parser → port walker changes by hand → port resolver
changes by hand (see `docs/ai-platform-upgrade.md` Section A2 for the recurring
proof). The grammar file is currently in sync (body hashes match; only the
vendoring header differs), which is the *good* state — but it relies on humans
remembering to sync, and it doesn't help with the walker or semantics duplication
above the grammar.

**End state:** Modeler publishes Kotlin artifacts to GitHub Packages.
ai-platform consumes them like any other Maven dependency. The `.g4` file is
never vendored. The Kotlin walker exists in one place. Eventually, the loader
semantics (resolver, symbol table, stock vocab) exist in one place too.

## Audit — current state (verified 2026-05-30)

- **modeler** owns the canonical `TTR.g4` at `packages/grammar/src/TTR.g4`
  (`@grammar-version: 2.2`). Hashes match against ai-platform's vendored copy
  today.
- **modeler** has a TS parser (`packages/parser`, walker 1709 LOC, AST 511 LOC)
  and a full semantics layer (`packages/semantics` — qname, package-graph,
  package-inference, resolver, symbol-table, validator, stock-loader, stock/*).
- **ai-platform** has `shared/libs/kotlin/ttr-parser` (Kotlin Gradle module,
  walker 1164 LOC, model 445 LOC; loader 180 LOC). The module uses the ANTLR
  Gradle plugin to generate Java parser/lexer; Kotlin walker wraps it. README
  says "Modeler-owned — do not hand-edit the `.g4`."
- **ai-platform** has a parallel semantics implementation in
  `infra/metadata/src/main/kotlin/infra/metadata/resolve/` (`ReferenceResolver`,
  `ReferenceResolutionPass`, `SymbolTable`, `DrillMapValidator` — ~795 LOC) and a
  stock source at `infra/metadata/src/main/kotlin/infra/metadata/source/BuiltinStockSource.kt`
  that parses a bundled `cnc-stock-roles.ttrm` via `TtrLoader`.
- **ai-platform** has `shared/libs/kotlin/ttr-writer` (TtrRenderer; renders
  `Definition`s back to TTR text). Depends on `ttr-parser` model types.
- **ai-platform** already publishes Kotlin artifacts to GitHub Packages: full
  workflow at `.github/workflows/publish.yml`, conventions in `PUBLISHING.md`,
  group `com.tatrman`, semver discipline, **no SNAPSHOTs** (storage cleanup
  rationale). We can mirror this infrastructure 1:1 on the modeler side.

**Drift discovered during audit (informs Phase 1 scope):**

1. ai-platform's `ColumnDef.searchable: Boolean` and
   `AttributeDef.searchable: Boolean` are still top-level fields — but TTR v2.0.0
   moved `searchable` *inside* the `search { … }` block. The Kotlin model is
   behind the canonical grammar. Publishing from modeler will surface this; any
   ai-platform code reading the old fields needs migration. (Cross-check
   `ai-platform-upgrade.md` Section B.)
2. ai-platform's `SourceLocation` is `(file, line, column)` only. Modeler's is
   ANTLR-style with `endLine`, `endColumn`, `offsetStart`, `offsetEnd` (offsets
   used by the LSP edit synthesizer). When the Kotlin AST moves under modeler,
   we adopt modeler's richer shape — minor migration on ai-platform consumers
   that currently call `loc.toString()` (still works) or destructure the data
   class (breaks if positional). Worth grepping at Phase 1 step 5.
3. AST naming differences: Kotlin `Er2DbEntityDef` vs TS `Er2dbEntityDef`,
   Kotlin `SearchHintsValue` vs TS `SearchBlock`, Kotlin `LocalizedStringValue`
   vs TS `LocalizedString`. Pick the canon during Phase 1 step 2.
4. `shared/libs/kotlin/ttr-parser/build.gradle.kts` declares
   `api(project(":shared:proto"))`, but the Kotlin source has no proto imports.
   Stale dep — remove before extracting. (Cleanup item: blocks publishing as a
   standalone artifact.)

## Cutoff — what lives where

**Modeler owns and publishes (Kotlin artifacts):**

- The Kotlin lexer/parser generated from `TTR.g4` (ANTLR Java target wrapped in
  a Kotlin Gradle module).
- Kotlin AST types (`Definition` sealed hierarchy, `PropertyValue`,
  `SourceLocation`, etc.) — adopting modeler's richer shape as the canon.
- Kotlin walker (parse-tree → typed AST), with Python-style triple-string dedent
  parity with the TS walker.
- `TtrLoader` entry point (`parseString` / `parseFile` / `parseDirectory`).
- Diagnostics enum (parser-level) and `ParseError` / `ParseWarning` shapes.
- **Phase 2:** Kotlin semantics (qname, symbol table, resolver, package
  inference, stock vocab loader, validator).

**Stays in ai-platform:**

- `infra/metadata/*` — everything that integrates with proto types, the
  metadata service, gRPC, the reconciler, the export pipeline.
- `BuiltinStockSource` boundary class (loads stock via the published loader; the
  stock `.ttrm` content itself moves to modeler in Phase 2).
- YAML import sources, git archive storage, CLI, refresh/registry/search code.
- Anything that touches proto (`com.tatrman.plan.v1.*`).
- The legacy YAML→TTR converter and any ai-platform-specific severity policy
  for diagnostic codes.

## Decisions (confirmed 2026-05-30)

| # | Decision | Resolution |
|---|---|---|
| D1 | Maven group ID for modeler artifacts | **`org.tatrman`** — distinct from ai-platform's `com.tatrman`. |
| D2 | GitHub Packages repo URL | **`https://maven.pkg.github.com/Collite/modeler`**. ai-platform's `settings.gradle.kts` gets a second Maven repo block alongside `AiPlatformPackages`. |
| D3 | Canonical AST naming when names differ between TS and Kotlin | **Use both** — Kotlin-idiomatic names (`Er2DbEntityDef`, `SearchHintsValue`, `LocalizedStringValue`) on the Kotlin side; existing TS names (`Er2dbEntityDef`, `SearchBlock`, `LocalizedString`) on the TS side. **Deliverable:** `docs/grammar-master/AST-NAMING.md` documents the mapping in one table — kept up to date as the AST evolves. Conformance test compares structure, not identifiers. |
| D4 | Richer `SourceLocation` on the Kotlin side | **Yes** — published Kotlin `SourceLocation` carries `endLine`, `endColumn`, `offsetStart`, `offsetEnd` from day one. Additive for ai-platform consumers. |
| D5 | ttr-writer ownership | **Move to modeler.** Same Phase 1 publish cycle: `org.tatrman:ttr-writer:0.1.0` ships alongside `org.tatrman:ttr-parser:0.1.0`. ai-platform's `shared/libs/kotlin/ttr-writer/` collapses to a dependency the same way `ttr-parser` does. |
| D6 | SNAPSHOT versions | **No SNAPSHOTs.** Cut real `0.x.y` versions; use `publishToMavenLocal` for tight iteration before any push. |
| D7 | Sequencing | **Two PRs.** (1) modeler publishes `0.1.0` and runs against its own conformance harness; (2) ai-platform consumer switch in a separate PR once `0.1.0` is live. |

## Phase 1 — Parser + walker artifact

**Goal:** ai-platform's `shared/libs/kotlin/ttr-parser` is reduced to a thin
build.gradle.kts that pulls `org.tatrman:ttr-parser:<v>`. The vendored `.g4` and
the Kotlin walker/model files are deleted there and live only in modeler.

**Deliverable per step:** one PR in modeler unless noted.

### P1-1 — Modeler scaffolding (modeler PR #1)

- Add Gradle to the modeler repo. New top-level files: `settings.gradle.kts`,
  `gradle/wrapper/*`, `gradle/libs.versions.toml`. The Gradle build coexists
  with pnpm — they don't share anything at build time, but the same `TTR.g4`
  file is read by both.
- New module path: `packages/kotlin/ttr-parser/` (mirrors the existing
  ai-platform path but rooted under `packages/kotlin/` so it sits next to the
  TS packages logically).
- `build.gradle.kts` mirrors ai-platform's but with:
  - **No** `api(project(":shared:proto"))` (drop the stale dep).
  - `antlr` plugin pointed at `../../grammar/src/TTR.g4` so the build reads the
    canonical file directly. No copy, no sync, no header rewriting.
  - `mavenPublication` block targeting `org.tatrman:ttr-parser`.
- Verify locally: `./gradlew :packages:kotlin:ttr-parser:build`.

### P1-2 — Port Kotlin source from ai-platform (modeler PR #1, same)

- Copy `loader/`, `model/`, `walker/` Kotlin packages from ai-platform's
  `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/` to the new
  modeler module.
- **Bring the model up to grammar v2.2:** remove top-level `searchable` from
  `ColumnDef` / `AttributeDef`; add them inside `SearchHintsValue`. Add walker
  paths for new v2.2 constructs (drill_map already in the Kotlin model — keep
  it; verify against the canonical grammar via `grammar-v2.test.ts` fixtures).
- Adopt the richer `SourceLocation` from modeler's TS shape (`endLine`,
  `endColumn`, `offsetStart`, `offsetEnd`). Populate from ANTLR tokens
  identically to `walker.ts`'s `makeSourceLocation` (note the multi-token-span
  invariant from `CLAUDE.md` — `endColumn = stopToken.column + stopTokenLength`).
- Copy tests across (`TtrLoaderSpec`, `DedentSpec`, `InlineMappingsSpec`,
  `DrillMapParserSpec`). They should pass against the canonical grammar
  unchanged.
- Also port `shared/libs/kotlin/ttr-writer/` to
  `packages/kotlin/ttr-writer/` (D5). It depends only on the model types from
  the new `ttr-parser` module via `api(project(":packages:kotlin:ttr-parser"))`.
  Bring its test (`TtrRendererSpec`) across.
- Add `docs/grammar-master/AST-NAMING.md` (D3) — one table mapping every TS AST
  type to its Kotlin counterpart. Single source of truth for the rename map;
  updated in the same PR as any AST shape change.

### P1-3 — TS↔Kotlin conformance harness (modeler PR #2)

- New directory `tests/conformance/` with a curated set of `.ttrm` fixtures
  exercising every grammar production. Reuse what's already in
  `packages/parser/src/__tests__/` and `samples/`.
- A small Node script and a JUnit test both parse each fixture, dump the AST
  to JSON (normalized field order, no SourceLocation), and assert byte-equal
  output across the two implementations.
- CI runs this on every PR touching `packages/grammar/` or either parser.
- Catches walker drift the next time either side adds a property — much earlier
  than catching it in ai-platform.

### P1-4 — Publishing pipeline (modeler PR #3)

- `.github/workflows/publish.yml` in modeler, modeled on ai-platform's. Trigger
  tags: `kotlin/v<x.y.z>` — publishes both `ttr-parser` and `ttr-writer` as a
  bundle (anticipating a future `kotlin-semantics/v<x.y.z>` tag in Phase 2 for
  the third artifact).
- First publish: cut `org.tatrman:ttr-parser:0.1.0` **and**
  `org.tatrman:ttr-writer:0.1.0` together. Smoke-test from
  `publishToMavenLocal` first.
- Add a `PUBLISHING.md` in modeler explaining the convention, the GitHub
  Packages auth caveat (PAT required even for public packages — same gotcha as
  ai-platform's existing doc), and the version-bump triggers (grammar version
  bump → minor; walker/AST shape change → minor; bugfix → patch; AST API break
  → major).
- Modeler-side `tsconfig`/pnpm scripts don't change — the Kotlin module is
  side-by-side, not upstream of the TS build.

### P1-5 — ai-platform consumer switch (ai-platform PR)

- Add modeler's GitHub Packages repo to ai-platform's `settings.gradle.kts`
  alongside the existing `AiPlatformPackages` block:
  ```kotlin
  maven {
      name = "ColliteModeler"
      url = uri("https://maven.pkg.github.com/Collite/modeler")
      credentials {
          username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
          password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
      }
  }
  ```
  (Same PAT works as long as it has `read:packages` and the user has read access
  to the `Collite/modeler` repo.)
- `gradle/libs.versions.toml`: add `tatrman-ttr-parser = "0.1.0"` and
  `tatrman-ttr-writer = "0.1.0"`.
- `shared/libs/kotlin/ttr-parser/build.gradle.kts`: replace ANTLR plugin block
  + sources dir with a single `api("org.tatrman:ttr-parser:0.1.0")` re-export so
  existing consumers (`infra/metadata`, `ttr-writer`) keep their
  `project(":shared:libs:kotlin:ttr-parser")` lines unchanged. (Or delete the
  module entirely and update the two consumers' build files — cleaner; pick
  during the PR.)
- **Delete** `shared/libs/kotlin/ttr-parser/src/main/antlr/` (vendored `.g4`
  and the dir).
- **Delete** `shared/libs/kotlin/ttr-parser/src/main/kotlin/shared/ttr/parser/`
  (loader, model, walker — now published).
- Run the full ai-platform test suite. Failures will mostly be:
  - Top-level `searchable` on Column/Attribute — already a Section B item in
    `ai-platform-upgrade.md`; resolve here.
  - `SourceLocation.toString()` callers — should still work; verify.
  - Any positional destructuring of `SourceLocation` — fix to named.

### P1-6 — Retire the sync scripts (modeler PR #4)

- Delete `packages/grammar/scripts/sync-to-ai-platform.sh` and `check-sync.sh`.
- Remove the "grammar sync" steps from `CLAUDE.md` (under "Grammar
  regeneration") and from `docs/v1/design/architecture.md` §9 and §8.5 (CI).
  The new contract is "ai-platform's `org.tatrman:ttr-parser` version is the
  drift detector — bumping it is a deliberate consumer-side action."
- Update `packages/grammar/README.md` to remove the sync section.

**Phase 1 done when:**

- `org.tatrman:ttr-parser:0.1.0` is published and pulled by ai-platform.
- ai-platform's full test suite is green against the published artifact.
- The conformance harness runs in modeler CI and is green.
- No `.g4` file exists anywhere outside `modeler/packages/grammar/src/`.

## Phase 2 — Semantics artifact

**Goal:** publish `org.tatrman:ttr-semantics` so ai-platform's
`infra/metadata/resolve/*` and `infra/metadata/source/BuiltinStockSource.kt`
collapse to consumers. The recurring "ai-platform must re-implement the new
resolver" task from `ai-platform-upgrade.md` Section A2 disappears.

**Prerequisite:** Phase 1 shipped and stable for at least one grammar bump
(proves the publishing rhythm).

### P2-1 — New Kotlin module `packages/kotlin/ttr-semantics/`

- Depends on `:packages:kotlin:ttr-parser`. No proto deps, no I/O beyond what
  the TS semantics layer does today.
- Port the TS modules from `packages/semantics/src/`:
  - `qname.ts` → `Qname.kt` — qualified-name shape + helpers.
  - `package-inference.ts` → `PackageInference.kt` — directory → package rule
    (spec §4.4).
  - `package-graph.ts` → `PackageGraph.kt` — package dependency graph,
    circular-detection.
  - `symbol-table.ts` → `SymbolTable.kt`.
  - `resolver.ts` → `Resolver.kt` — six-step resolver chain (spec §4).
  - `validator.ts` → `Validator.kt` — per-kind validator (cardinality strings,
    target shapes, type aliases). ai-platform's loader will still apply its
    own *additional* validators that touch proto, but the core grammar-level
    validation lives here.
  - `stock-loader.ts` + `stock/*.ttrm` → `StockLoader.kt` + bundled resource.
    Stock vocab content lives here as the single source of truth.
  - `mapping-synthesizer.ts` → defer or leave in modeler-TS-only (it's
    edit-synthesizer adjacent; ai-platform doesn't need it).
- Diagnostics: emit the v1.1 codes in `diagnostics.ts` (`ttr/unimported-reference`,
  `ttr/ambiguous-reference`, etc.) as a Kotlin enum.

### P2-2 — Conformance harness extension

- Extend the Phase 1 conformance harness with semantics: for each fixture, both
  resolvers must produce the same set of `(reference, resolvedQname)` pairs and
  the same diagnostic codes (per-diagnostic shape; severities are
  policy-of-the-consumer so just check codes/positions).

### P2-3 — ai-platform consumer switch

- Add `org.tatrman:ttr-semantics:<v>` to ai-platform.
- Delete `infra/metadata/src/main/kotlin/infra/metadata/resolve/` (the four files
  audited above). Update callers in `ReferenceResolutionPass.kt` consumers
  to use `org.tatrman.ttrm.semantics.Resolver`.
- `BuiltinStockSource`: stop bundling `cnc-stock-roles.ttrm` in
  `src/main/resources/builtin/`; instead, delegate to the published
  `StockLoader` which carries the canonical stock content. ai-platform's
  `BuiltinStockSource` wrapper continues to do the `SourceSnapshot` /
  `protectedQnames` shape conversion.
- Run the full metadata suite + `MetadataServiceFixtureSpec` +
  `ResolutionIntegrationSpec` etc.

**Phase 2 done when:**

- `org.tatrman:ttr-semantics:<v>` is published and consumed.
- ai-platform's resolver/symbol-table/stock-loader code is deleted.
- The next grammar change requires zero hand-written semantics work in
  ai-platform — just a version bump.

## Cross-cutting risks

1. **GitHub Packages PAT for reads.** Every ai-platform dev and CI runner
   already needs one for `com.tatrman:*`. Same shape for `org.tatrman:*` — they
   can add the modeler Maven repo block alongside the existing one; the same
   PAT works as long as it has `read:packages` and the PAT's owner has read
   access to the modeler repo. Worth being explicit in `PUBLISHING.md`.
2. **Modeler becomes polyglot.** Gradle + JVM toolchain land in the repo for the
   first time. CI gets a Java setup step. Iteration on TS-only changes is
   unaffected. Iteration on grammar changes goes from "Vitest green" to
   "Vitest green + Gradle test green + conformance green", with the option to
   publish a real version when ai-platform needs it. Plan for `~5-10 min` longer
   grammar-change PRs.
3. **AST shape changes ripple.** Adopting modeler's `SourceLocation` superset is
   additive; renaming `Er2DbEntityDef` etc. is not (keep current Kotlin names to
   avoid an unforced break). The conformance harness must compare *structure*,
   not *symbol identity*, for the AST cross-language diff to be meaningful.
4. **Two grammar generation paths still exist** (TS via `antlr-ng`; Kotlin via
   ANTLR Gradle plugin → Java). That's intrinsic — each target language gets its
   own generator. The grammar file is the same input. Conformance test covers
   the divergence risk.
5. **ai-platform's `ttr-parser` Gradle path** (`:shared:libs:kotlin:ttr-parser`)
   currently appears in many `build.gradle.kts` files. The cleanest Phase 1
   shape leaves a stub there that re-exports the published artifact, so the
   `project(...)` references don't change. Trade-off: small amount of cruft in
   exchange for a 1-file PR per consumer in Phase 1. Decide during P1-5.
6. **`ttr-writer` move (D5)** ships in the same Phase 1 publish cycle. The
   ai-platform-side delete happens in P1-5 alongside the `ttr-parser` delete,
   not as a separate step.

## Verification gates

- **Phase 1 verification:**
  - `./gradlew :packages:kotlin:ttr-parser:build :packages:kotlin:ttr-parser:test`
    green in modeler.
  - `pnpm -r test` still green in modeler (nothing changed for TS).
  - Conformance harness green on every fixture in `tests/conformance/`.
  - ai-platform `./gradlew build` and full test suite green against
    `org.tatrman:ttr-parser:0.1.0`.
  - No `.g4` outside `modeler/packages/grammar/`.
- **Phase 2 verification:**
  - All Phase 1 gates still hold.
  - Conformance harness extended to semantics, green.
  - ai-platform's metadata suite green (`MetadataServiceFixtureSpec`,
    `ResolutionIntegrationSpec`, `SearchBlockEndToEndSpec`,
    `Phase2_2ExpressivenessSpec` at minimum).
  - The next grammar minor bump (whatever lands first after Phase 2) requires
    zero edits to `infra/metadata/resolve/`.

## What this plan does NOT cover

- Migrating the TS parser/semantics to a different code structure. They stay
  exactly as they are; the LSP still needs them in-process for both Node and
  browser-worker builds.
- Anything proto-related. Proto stays an ai-platform concern.
- A separate `ttr-language` repo (Option C from the discussion). Skipped:
  modeler already owns the grammar package and the team is one person.
- IntelliJ plugin work — orthogonal to this refactor.
