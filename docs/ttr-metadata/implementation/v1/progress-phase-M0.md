# Progress · Phase M0 · `world` schema grammar

> `[x]` = developer intent; verify against runtime before trusting (repo `/review` cadence).

## Stage M0.1 — `world` grammar + toolchain

**Status:** code-complete & green locally 2026-07-05. Publish tags **deferred** (see below).

**Baseline commit (pre-flight):** `cb7e69a`. TTR-M suites green at baseline except a
**pre-existing** migration E2E failure (`tests/integration/src/migration.test.ts` — 2 tests,
"no `.ttrg` created"), unrelated to grammar; it fails identically before and after M0 (M0 is
additive grammar and never touches the migrator).

### What landed

- **Grammar 4.1** (`packages/grammar/src/TTR.g4`, additive): `model world`; `def world` with
  nested `def engine`/`def executor`/`def storage` (+ nested `def schema`), `extends:`,
  `hosts: [pkg]`, `staging:` bool, free-form manifest fallback (`propertyEntry`). New tokens
  `WORLD/ENGINE/EXECUTOR/STORAGE/EXTENDS/HOSTS/STAGING`; `idPart` gains
  `WORLD/ENGINE/EXECUTOR/STORAGE/VERSION` (EXTENDS/HOSTS/STAGING excluded on purpose — see the
  4.1 header note + CHANGELOG; makes the neg-02/03/05 malformed-value cases hard parse errors).
- **All three targets** walk + dump the world model identically:
  - TS: `packages/parser` (ast/walker), `tests/conformance/dump.ts`, `@modeler/semantics`
    (qname/default-schema/symbol-table registration + `validateWorldDocument`).
  - Kotlin: `Definition.kt`, `TtrWalker.kt`, `ConformanceDump.kt`, `Kinds.kt`, `SymbolTable.kt`,
    `WorldValidator.kt`, `TtrRenderer.kt` (writer).
  - Python: `model.py`, `walker.py`, `tests/conformance/dump.py`, `semantics/default_schema.py`
    + `symbol_table.py`.
- **TextMate**: `ttrm.tmLanguage.json` regenerated (world keywords highlighted).
- **Fixtures**: `tests/conformance/fixtures/57-world.ttrm` (golden roster from ttr-p s1.3
  T1.3.1) + `58-world-extends.ttrm`; parser-reject roster in
  `packages/kotlin/ttr-parser/src/test/resources/world-negative/` (5 files). **Note:** numbers
  57/58 used because 31/32 (named in the task list) were already taken — object roster is
  identical; only the numeric prefix differs.
- **Warning validators** `world/duplicate-staging`, `world/hosts-unknown-package`,
  `world/wrong-model-kind` in TS + Kotlin (+ Python has the symbols; hard errors stay in M2's
  WorldResolver per MD5). Kept as a standalone entry point, excluded from the semantics
  conformance dump (matches TS `diagnostics: []` on world fixtures).

### DONE-bar verification (fresh command output)

- `pnpm --filter @modeler/conformance diff` → **All 55 fixtures match across TS and Kotlin**
  (incl. 57/58).
- `pnpm --filter @modeler/conformance diff-sem` → **All 58 fixtures match across TS and Kotlin
  semantics** (incl. 57/58).
- Python: `pytest tests/conformance/test_conformance.py` → **116 passed** (parse + sem vs TS
  golden).
- Writer: `./gradlew :packages:kotlin:ttr-writer:test --tests '*WorldRoundTripSpec*'` → green
  (render-twice byte-stable + structural roster survives round-trip).
- Negatives: `WorldParseSpec` rejects all five `world-negative/*.ttrm` in the Kotlin parser.
- Repo gates: `pnpm -r typecheck && lint && build` green; `pnpm -r test` green **except** the
  pre-existing migration E2E; `./gradlew build` green (incl. ktlint).
- Grammar version auto-bumped to **4.1** (`TTR_GRAMMAR_VERSION`); grammar CHANGELOG updated.

### Deferred: §6 publish tags

`kotlin/v<next-minor>` + `python/v<next-minor>` **not pushed**. Pushing them publishes to
Maven / PyPI (consumed by other repos) — an outward-facing, hard-to-reverse action. Deferred
until the feature is reviewed (the user said "we will review the whole feature"). M1 uses the
in-repo Kotlin modules directly, so it is not blocked by this; M4 (kantheon) is what actually
needs the published artifacts. **Action for reviewer:** once approved, push the two tags and
record the resolved versions here (kotlin `____` · python `____`).
