# Tasks · M0 · Stage 0.1 — `world` schema grammar + toolchain

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Feature decisions MD1–MD8 → `../../architecture/architecture.md` §8 · TTR-P decision IDs → `../../../ttr-p/design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The `world` model code lands in `TTR.g4` (grammar **4.0 → 4.1**, additive) with `def world` carrying nested `def engine` / `def executor` / `def storage` (+ nested `def schema` on storages), `extends:`, `hosts: [pkg]`, `staging:` boolean, and free-form manifest properties (T6 data transported, never interpreted — MD5). All grammar targets regenerate per CLAUDE.md (TS `antlr-ng` prebuild, Kotlin ANTLR-Gradle, Python hook, TextMate); ttr-writer round-trips world docs byte-stable; ttr-semantics (Kotlin **and** the TS twin in `@modeler/semantics`) registers the new kinds + warning-level validators (hard errors stay in M2's `WorldResolver`, MD5); conformance corpus entries prove TS/Kotlin/Python agree; the spec-version cut follows `docs/grammar-master/new-grammar-version-process.md`.
**DONE bar (plan M0.1):** the world golden fixture parses in TS + Kotlin parsers with identical ASTs (conformance harness) and round-trips byte-stable through ttr-writer.

**Syntax note (read before writing any fixture):** the TTR-P docs (s1.3 fixture design, `06-model-binding-options.md` D-d) spell the directive `schema world` — that text predates grammar **4.0**, which renamed the directive to `model <code>` (`modelDirective : MODEL modelCode (SCHEMA id)?`). s1.3 T1.3.1 explicitly says *"Exact world-doc syntax follows whatever `ttr-parser` ships — adjust spellings, keep the object roster."* So M0 world docs open with **`model world`**; "schema world" remains the informal kind name in prose. Do not rename the directive back.

## Pre-flight (all must pass before T0.1.1)

- [x] Read `docs/grammar-master/new-grammar-version-process.md` end-to-end (this stage executes it) and skim `docs/grammar-master/contracts.md`, `docs/grammar-master/AST-NAMING.md`.
- [x] Read TTR-P world-shape sources: `docs/ttr-p/design/06-model-binding-options.md` §D-d (+ RESOLVED block) and `docs/ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` T1.3.1 (the `acme.worlds` roster this stage's golden fixture must match).
- [x] TTR-M baseline green: `pnpm -r build && pnpm -r test` · `./gradlew build` · `pnpm --filter @modeler/conformance dump-all && pnpm --filter @modeler/conformance diff && pnpm --filter @modeler/conformance diff-sem` (record the baseline commit hash here: `____`).
- [x] `grep -n "WORLD" packages/grammar/src/TTR.g4` returns nothing (nobody landed a partial world grammar since this list was written).

## Tasks

### T0.1.1 · Golden + negative world fixtures and spec skeletons (TEST-FIRST)

- [x] Golden fixture — create `tests/conformance/fixtures/31-world.ttrm` (this one file is also the source the M1/M2 fixture repos copy). Content = the s1.3 `acme.worlds.dev` roster, spelled for grammar 4.x:

  ```ttr
  package acme.worlds
  model world

  def world dev {
      description: "acme dev world (M0 golden fixture — roster from ttr-p tasks-p1-s1.3 T1.3.1)"
      def engine erp_pg   { type: postgres, version: "16", extensions: [pg_trgm] }
      def engine polars   { type: polars }
      def executor sh     { type: bash }
      def storage erp_db  { type: postgres, via: erp_pg, hosts: [erp] }
      def storage files   {
          type: local_dir, path: "/data/files",
          def schema sales_csv { customer: string, region: string, amount: decimal }
      }
      def storage stage   { type: local_dir, path: "/data/stage", staging: true }
  }
  ```

  Roster invariants (must match s1.3 verbatim): engines `erp_pg`/`polars`, executor `sh`, storages `erp_db` (via + hosts), `files` (with named schema `sales_csv` — D-c world home), `stage` (`staging: true`). `extensions: [pg_trgm]` and `path:` ride the free-form manifest fallback (T6 data). **Lexer-collision check:** every free-form manifest key must lex as `idPart` — `version`, `path`, `extensions` are checked in T0.1.2; if a fixture key collides with a keyword token not in `idPart`, extend `idPart`, don't respell the fixture.
- [x] Second golden fixture `tests/conformance/fixtures/32-world-extends.ttrm` — grammar coverage for the overlay input (resolution itself is M2): a world whose members use `extends:` (`def engine erp_pg { extends: acme.types.postgres16 }`, `def storage stage { extends: acme.types.scratch_dir, staging: true }`). `extends:` takes a dotted id; what the qname resolves to is M2's `ExtendsUnresolved` concern, not M0's.
- [x] Negative grammar fixtures under `packages/kotlin/ttr-parser/src/test/resources/world-negative/` (parser-level rejects; each file header-comments its expectation):
  - `neg-01-toplevel-engine.ttrm` — `def engine x { … }` at document top level (engine/executor/storage/world-schema exist **only** inside `def world` — grammar-enforced nesting, see T0.1.2 rationale) → syntax error.
  - `neg-02-staging-nonbool.ttrm` — `staging: "yes"` → syntax error (BOOLEAN_LITERAL only).
  - `neg-03-hosts-strings.ttrm` — `hosts: ["erp"]` → syntax error (`listOfIds` only; packages are ids).
  - `neg-04-nested-world.ttrm` — `def world inner { … }` inside a world body → syntax error.
  - `neg-05-extends-string.ttrm` — `extends: "acme.types.x"` → syntax error (id, not string).
- [x] Kotest spec skeletons (red), package `org.tatrman.ttr.parser.model`: `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/model/WorldParseSpec.kt` — parses `31-world.ttrm` + `32-world-extends.ttrm` (load via the same resource-path convention `ConformanceSpec.kt` uses to reach `tests/conformance/fixtures/`), asserts: 1 `WorldDef` named `dev`, 2 engines / 1 executor / 3 storages, `erp_db.hosts == ["erp"]`, exactly one member with `staging == true`, `files` carries 1 nested `WorldSchemaDef` with 3 fields, manifest map of `erp_pg` contains `extensions`; plus a table-driven negative case per `world-negative/*.ttrm` asserting `!result.ok`.
- [x] TS test skeletons (red): `packages/parser/src/__tests__/world.test.ts` (same assertions as `WorldParseSpec`, incl. source-location sanity on `WorldDef` per the CLAUDE.md `SourceLocation` invariant) and `packages/semantics/src/__tests__/world-validate.test.ts` (see T0.1.4 codes; skeleton with `it.todo` entries now).
- [x] Writer round-trip spec skeleton (red): `packages/kotlin/ttr-writer/src/test/kotlin/org/tatrman/ttr/writer/WorldRoundTripSpec.kt` — parse `31-world.ttrm` → render → re-parse → dump-equal, plus render-twice byte-equality.
  - **Verify:** `./gradlew :packages:kotlin:ttr-parser:test --tests '*WorldParseSpec*'` and `pnpm --filter @modeler/parser test -- world` run **red** (fixtures load, grammar doesn't exist yet).

### T0.1.2 · `TTR.g4` — the `world` productions (grammar 4.1)

- [x] Bump the marker: `// @grammar-version: 4.1` + a "Changes in 4.1 (additive — ttr-metadata M0, D-d-α)" entry in the header comment block (list new tokens/rules exactly like the 3.1 entry does). Keep the file target-neutral (process doc §0: no actions/predicates/headers).
- [x] Wire the directive + top-level def. Existing productions being extended (transcribed so you match the style — comma-optional brace bodies, `propSep`, one `*Def`/`*Property` pair per kind):

  ```antlr
  modelCode
    : DB | ER | BINDING | QUERY | CNC | MD      // MD (3.1) — multidimensional logical model
    ;
  objectDefinition
    : PROJECT        id  projectDef
    | TABLE          id  tableDef
    …
    | AREA           id  areaDef            // v3.0 — subject area (replaces the .ttrd domain block)
    ;
  areaDef          : LBRACE (areaProperty (COMMA? areaProperty)* COMMA?)? RBRACE ;
  hierarchiesProperty  : HIERARCHIES propSep? listOfIds ;
  ```

  Changes: `modelCode` gains `| WORLD` (4.1 comment); `objectDefinition` gains **one** alternative `| WORLD id worldDef` — engine/executor/storage/world-schema are **not** top-level alternatives. Rationale (record as a grammar comment): they are meaningless outside a world and D-d-α specifies nesting; this is a *structural* fact like graph bodies, not a per-schema validity rule, so grammar-enforcing it does not violate "parser stays mechanical". Per-model validity of `def world` itself (world defs only in `model world` files) **is** semantic — T0.1.4.
- [x] New parser rules (write exactly; keep alt-order: typed properties before the free-form fallback so ANTLR's first-match resolves them):

  ```antlr
  // ----- v4.1 world model (ttr-metadata M0; D-d-α, D-d-i, D-f, T6) -----
  worldDef         : LBRACE (worldMember (COMMA? worldMember)* COMMA?)? RBRACE ;
  worldMember
    : DEF ENGINE   id engineDef
    | DEF EXECUTOR id executorDef
    | DEF STORAGE  id storageDef
    | worldProperty
    ;
  worldProperty    : descriptionProperty | tagsProperty | extendsProperty ;

  engineDef        : LBRACE (enginePartProperty (COMMA? enginePartProperty)* COMMA?)? RBRACE ;
  executorDef      : LBRACE (enginePartProperty (COMMA? enginePartProperty)* COMMA?)? RBRACE ;
  // Shared engine/executor body: typed alts first, then the free-form manifest
  // fallback (T6 β data — transported opaque, interpreted by TTR-P Stage 2.2 only).
  enginePartProperty
    : descriptionProperty | tagsProperty | typeProperty | versionProperty
    | extendsProperty | propertyEntry
    ;

  storageDef       : LBRACE (storageProperty (COMMA? storageProperty)* COMMA?)? RBRACE ;
  storageProperty
    : descriptionProperty | tagsProperty | typeProperty | extendsProperty
    | viaProperty | hostsProperty | stagingProperty
    | DEF SCHEMA id worldSchemaDef                      // D-c world home for named schemas
    | propertyEntry
    ;

  extendsProperty  : EXTENDS  propSep? id ;             // instance ⊕ type overlay input (resolved in M2)
  hostsProperty    : HOSTS    propSep? listOfIds ;      // D-d-i: model packages this storage hosts
  stagingProperty  : STAGING  propSep? BOOLEAN_LITERAL ;// D-f: exactly-one checked in semantics/WorldResolver
  viaProperty      : VIA      propSep? id ;             // storage reached via engine (VIA token reused from 3.1)

  worldSchemaDef   : LBRACE (worldSchemaField (COMMA? worldSchemaField)* COMMA?)? RBRACE ;
  worldSchemaField : id propSep? dataType ;             // { customer: string, amount: decimal }
  ```

  Reuse notes: `type:` rides the existing `typeProperty` (`DATA_TYPE` token, `dataType` accepts bare ids like `postgres`); `version:` rides `versionProperty` (STRING_LITERAL — hence `version: "16"` in the fixture); `via:` reuses the existing `VIA` token (3.1 `levelEntry` infix).
- [x] New lexer tokens, placed with the other def-kind keywords (before `IDENT`; longest-match notes where needed): `WORLD : 'world' ;` · `ENGINE : 'engine' ;` · `EXECUTOR : 'executor' ;` · `STORAGE : 'storage' ;` · `EXTENDS : 'extends' ;` · `HOSTS : 'hosts' ;` · `STAGING : 'staging' ;`.
- [x] Extend `idPart` with all seven new tokens (cross-refs like `acme.worlds.dev` and manifest keys must keep lexing; process doc §1 rule). While here, run the **fixture-key lex check** from T0.1.1: for each free-form key used in the golden fixtures (`path`, `extensions`, `version`, …) confirm the token it lexes to is in `idPart` (`version` → `VERSION` is **not** currently in `idPart` — it doesn't need to be, because `version:` matches `versionProperty`, but any *free-form* key colliding with a non-`idPart` keyword must be added; add `VERSION` to `idPart` anyway so world manifests can carry `version` under `propertyEntry` in executor bodies too — comment it `// v4.1 world manifests`).
  - **Verify:** `cd packages/parser && pnpm run prebuild` regenerates with zero ANTLR warnings; `./gradlew :packages:kotlin:ttr-parser:generateGrammarSource` succeeds (remember the flat-output caveat baked into `ttr-parser/build.gradle.kts` — do NOT nest `outputDirectory`).

### T0.1.3 · TS target: walker, AST, TextMate, `@modeler/semantics` registration

- [x] `packages/parser/src/ast.ts`: new nodes in the file's existing style (see the `area` node ~line 882): `WorldDef { kind: 'world'; name; members: (EngineDef|ExecutorDef|StorageDef)[]; extends?; description?; tags?; location }`, `EngineDef { kind: 'engine'; … manifest: Record<string, Value> }`, `ExecutorDef { kind: 'executor'; … }`, `StorageDef { kind: 'storage'; via?; hosts: string[]; staging?: boolean; schemas: WorldSchemaDef[]; manifest }`, `WorldSchemaDef { kind: 'worldSchema'; fields: { name; type }[] }`. Kind strings are canonical cross-target ids (AST-NAMING.md): `world`, `engine`, `executor`, `storage`, `worldSchema` — the Kotlin `kindOf` in T0.1.4 must return the identical strings.
- [x] `packages/parser/src/walker.ts`: walk the new contexts. **Source-location invariant** (CLAUDE.md): every node gets an accurate `SourceLocation`; multi-token spans use `endColumn = stopToken.column + stopTokenLength` — reuse `makeSourceLocation`, do not re-derive. Free-form `propertyEntry` values land in `manifest` as the walker's generic value shapes (no interpretation — MD5).
- [x] TextMate: `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`; commit the regenerated `syntaxes/ttr.tmLanguage.json` (the only committed generated grammar file).
- [x] `@modeler/semantics` (TS twin — keeps the LSP/Designer accepting `.ttrm` world files):
  - `packages/semantics/src/qname.ts`: `ModelCode` type + `MODEL_CODES` set gain `'world'`.
  - `packages/semantics/src/default-schema.ts` `modelForKind`: `'world' | 'engine' | 'executor' | 'storage' | 'worldSchema' → 'world'`.
  - Symbol registration: `def world dev` registers `acme.worlds.dev`; nested members register under the world (`acme.worlds.dev.erp_pg`, `…dev.files.sales_csv`) — mirror how inline defs (columns/attributes) nest today; duplicates inside one world reuse the existing duplicate-definition diagnostic.
  - Validators (warning severity — MD5 keeps hard errors in M2's `WorldResolver`): `world/duplicate-staging` (two+ `staging: true` in one world — lists all offending storages), `world/hosts-unknown-package` (a `hosts:` entry matches no known package in the project), `world/wrong-model-kind` (a `world`-family def in a non-`model world` file, and vice versa — mirror the existing per-model kind-validity mechanism).
  - Fill `packages/semantics/src/__tests__/world-validate.test.ts` from the T0.1.1 skeleton: fixture with two staging storages → 1 warning `world/duplicate-staging`; `hosts: [nosuch]` → `world/hosts-unknown-package`; the golden fixture → zero world diagnostics.
  - **Verify:** `pnpm --filter @modeler/parser test -- world` green · `pnpm --filter @modeler/semantics test -- world` green · `pnpm -r typecheck && pnpm -r lint`.

### T0.1.4 · Kotlin target: `Definition.kt`, `TtrWalker`, `Kinds.kt`, semantics validators

- [x] `packages/kotlin/ttr-parser/src/main/kotlin/org/tatrman/ttr/parser/model/Definition.kt`: `WorldDef`, `EngineDef`, `ExecutorDef`, `StorageDef`, `WorldSchemaDef` data classes mirroring the TS shapes field-for-field (same optionality, same defaults; conformance dumps must byte-match). `packages/kotlin/ttr-parser/.../walker/TtrWalker.kt`: walk the new contexts with the tested `SourceLocation` logic already in the walker.
- [x] `packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/Kinds.kt`: `kindOf` arms for the five new types returning exactly `"world"`, `"engine"`, `"executor"`, `"storage"`, `"worldSchema"`; `MODEL_CODES` gains `"world"`; `modelForKind` maps the five kinds → `"world"`.
- [x] Kotlin validators in `Validator.kt`, same codes + severities as T0.1.3's TS list (`world/duplicate-staging` warning, `world/hosts-unknown-package` warning, `world/wrong-model-kind`) — codes are cross-target contract (`SemanticsConformanceSpec` diffing).
- [x] Fill `WorldParseSpec` assertions from T0.1.1 + add `WorldSemanticsSpec.kt` in ttr-semantics mirroring `world-validate.test.ts` case-for-case.
  - **Verify:** `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-semantics:test` green, including all five `world-negative/*` rejects.

### T0.1.5 · ttr-writer round-trip

- [x] `packages/kotlin/ttr-writer/src/main/kotlin/org/tatrman/ttr/writer/TtrRenderer.kt`: render `WorldDef` (nested members indented one level, member order preserved as parsed, manifest entries in parse order — the writer is deterministic, not canonicalizing), `model world` directive, `hosts: [a, b]`, `staging: true`, nested `def schema`.
- [x] Fill `WorldRoundTripSpec.kt` (T0.1.1 skeleton): (a) parse `31-world.ttrm` → render → re-parse → conformance-dump equality with the original parse; (b) render(parse(render(x))) == render(x) byte-stable; (c) same for `32-world-extends.ttrm`.
  - **Verify:** `./gradlew :packages:kotlin:ttr-writer:test --tests '*WorldRoundTripSpec*'` green.

### T0.1.6 · Conformance lock-step: TS ⇄ Kotlin ⇄ Python

- [x] Python target: regenerate via the Hatchling hook (`packages/python/ttr-parser/scripts/generate-python-parser.sh`, needs Java) and extend the Python walker + semantics port with the five kinds (same kind strings, same dump shape). `cd packages/python/ttr-parser && pytest && ruff check . && mypy --strict .`
- [x] Refresh TS baselines: `pnpm --filter @modeler/conformance dump-all`; commit the new `tests/conformance/out-ts/31-world.json`, `32-world-extends.json` (+ `out-ts-sem/` entries).
- [x] Cross-target diffs (mirrors `conformance.yml`): `./gradlew :packages:kotlin:ttr-parser:test --tests '*ConformanceSpec*' :packages:kotlin:ttr-semantics:test --tests '*SemanticsConformanceSpec*'` · Python `py-dump`/`py-sem-dump` per the process doc · `pnpm --filter @modeler/conformance diff && pnpm --filter @modeler/conformance diff-sem`. All three targets agree on both world fixtures.
  - **Verify:** the three commands above exit 0; `git status` shows only the two new baseline files (+ fixtures) — no churn in existing baselines (proof the change is additive).

### T0.1.7 · Spec-version cut (grammar-master process) + stage sweep

Execute the remaining checklist of `docs/grammar-master/new-grammar-version-process.md` (§0–§2 were T0.1.2–T0.1.6; copy the full checklist into the PR description and tick there too). Version: **4.1**, kind: **additive (minor)**.

- [x] §4 Docs: `packages/grammar/CHANGELOG.md` 4.1 entry (tokens, rules, fixtures — follow the 3.1 entry's format) · update any test asserting `TTR_GRAMMAR_VERSION == "4.0"` · `CLAUDE.md` invariant touch-up (the schema/model-code roster now includes `world` — one line) · `docs/grammar-master/contracts.md` if the dump schema section enumerates def kinds.
- [x] §5 Repo gates: `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test` · `pnpm --filter @modeler/integration-tests test` · `./gradlew build`.
- [ ] §6 Publish: push `kotlin/v<next-minor>` (bundle: parser+writer+semantics — world needs all three) and `python/v<next-minor>`; verify both artifacts resolve at the new version (`gh api` the package feed or a scratch `mvn dependency:get`). Record versions here: kotlin `____` · python `____`.
- [x] §7 Downstream: **do NOT bump kantheon/ai-platform in this stage** — kantheon adopts via M4 (core freeze holds, plan §Cross-cutting); note the published version in `docs/ttr-metadata/implementation/v1/progress-phase-M0.md` for M1's pre-flight.
- [x] Stage DONE sweep: re-run the two DONE-bar commands (conformance diff on world fixtures; `WorldRoundTripSpec`) from a clean checkout (`git stash -u` guard) and tick §Definition of DONE.
  - **Verify:** all §Definition-of-DONE boxes below check against fresh command output, not memory.

## Definition of DONE (stage)

- [x] `31-world.ttrm` + `32-world-extends.ttrm` parse in TS and Kotlin with identical conformance dumps; Python agrees (`diff`/`diff-sem` + Kotest conformance specs all green).
- [x] Both world fixtures round-trip byte-stable through ttr-writer (`WorldRoundTripSpec` green).
- [x] All five `world-negative/*.ttrm` fixtures rejected by both parsers.
- [x] `world/duplicate-staging`, `world/hosts-unknown-package`, `world/wrong-model-kind` fire as **warnings** in TS and Kotlin semantics with matching codes (hard-error twin stays in M2 WorldResolver — MD5).
- [x] Grammar 4.1 cut per the grammar-master checklist; `kotlin/v*` + `python/v*` tags published and resolvable; CHANGELOGs updated.
- [x] Full repo gates green (`pnpm -r …` suite, integration tests, `./gradlew build`).

## Blockers

- **2026-07-05 — §6 publish tags DEFERRED (not a hard blocker).** `kotlin/v*` + `python/v*`
  publish artifacts to Maven/PyPI consumed by other repos; deferred until feature review (the
  user explicitly said the whole feature is reviewed before merge). M1 uses in-repo Kotlin
  modules, so it is not blocked. Reviewer pushes the tags post-approval — see
  `progress-phase-M0.md`.
- **Deviation (fixture numbers):** golden fixtures are `57-world.ttrm` / `58-world-extends.ttrm`
  (task named `31/32`, which were already taken). Roster identical; numeric prefix only.
- **Deviation (idPart):** `EXTENDS`/`HOSTS`/`STAGING` deliberately NOT added to `idPart` (task
  said "all seven") — required so `staging: "x"` / `hosts: ["x"]` / `extends: "x"` hard-error
  (DONE-bar: neg-02/03/05 must be parser rejects). `WORLD/ENGINE/EXECUTOR/STORAGE/VERSION` are
  in `idPart`. Rationale in the TTR.g4 4.1 header note + CHANGELOG.
- **Deviation (TextMate cmd):** used `pnpm run regen-tmgrammar` (CLAUDE.md's
  `node scripts/generate-tm-grammar.ts` can't run TS under Node); output file is
  `ttrm.tmLanguage.json`.

## References

- **MD4** world grammar is Phase M0 here (resolves TTR-P R3) · **MD5** mechanism/policy split — TTR-M semantics warns, `WorldResolver` (M2) hard-errors on staging conflict; manifest data transported opaque (T6).
- **D-d (α)** world = TTR schema kind, nested engine/executor/storage, instance `extends` type · **D-d-i** storage `hosts: [pkg]` · **D-f** one `staging: true` · **D-c (δ)** named schemas' world home. Content sketch: `docs/ttr-p/design/06-model-binding-options.md` lines 97–108.
- Golden roster source: `docs/ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` T1.3.1 (`models/acme/worlds.ttrm` bullet) — object roster is binding, spelling follows shipped grammar (their own caveat).
- Grammar style precedents in `packages/grammar/src/TTR.g4`: brace/comma-optional bodies (`areaDef` ~line 208), typed-props-then-fallback is new but mirrors `restrictClause`'s open-key stance (~line 318); token placement + `idPart` rules (lines 640–664); `VIA` token (line 737).
- Process: `docs/grammar-master/new-grammar-version-process.md` (all §§ executed in T0.1.2–T0.1.7) · Kotlin ANTLR flat-output caveat: `packages/kotlin/ttr-parser/build.gradle.kts` lines 26–36.
- Cross-target kind strings: `docs/grammar-master/AST-NAMING.md`; Kotlin `kindOf`/`modelForKind`: `packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/Kinds.kt`; TS twins: `packages/semantics/src/qname.ts`, `default-schema.ts`.
- Consumed by: M1.1 fixture repo (world doc included), M2.1 `WorldResolver`, TTR-P s1.3 pre-flight #3 (flips to "done" via plan §6 amendment).
