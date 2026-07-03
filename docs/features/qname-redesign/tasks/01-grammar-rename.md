# 01 — Grammar rename (modeler / `TTR.g4`) — **breaking**

Goal: `TTR.g4` uses `project` / `model` / `schema`; all three parsers regenerate; the
old forms fail to parse. See [`../architecture.md`](../architecture.md) §2,
[`../plan.md`](../plan.md) Phase 1.

**Pre-flight:** `pnpm install`; `pnpm -r typecheck && pnpm -r test` green;
`./gradlew :packages:kotlin:ttr-parser:test` green. Read `CLAUDE.md` "Grammar
regeneration".

## Tasks

- [ ] **1.1 Tests first (parser).** Add failing `@modeler/parser` cases: `def project P
  { version: "1" }` parses; `model db schema dbo` parses; `model er` (no schema)
  parses; `def model M`, `schema db`, `namespace x` now **error**.
- [ ] **1.2 Token changes.** Add `PROJECT : 'project'`; keep `MODEL : 'model'`
  (TTR.g4:670) for the directive; **delete** `NAMESPACE` (TTR.g4:649); keep `SCHEMA`
  (TTR.g4:648) now used for the namespace id. Add old keywords (`schema`, `model`) to
  `idPart` so they stay usable as identifiers.
- [ ] **1.3 Rule changes.** `objectDefinition`: `MODEL id modelDef` →
  `PROJECT id projectDef`. `schemaDirective : SCHEMA schemaCode (NAMESPACE id)?` →
  `modelDirective : MODEL modelCode (SCHEMA id)?`. `graphSchemaProperty` →
  `graphModelProperty : MODEL propSep? modelCode`. Rename `modelDef`/`modelProperty`
  → `projectDef`/`projectProperty` (body unchanged). **`schemaCode` rule →
  `modelCode : DB | ER | BINDING | CNC | MD`** — drop `QUERY` (D14); the `QUERY` token
  stays for the `def query` objectDefinition alt.
- [ ] **1.4 Regenerate TS.** `cd packages/parser && pnpm run prebuild`; build.
- [ ] **1.5 Regenerate Kotlin + Python parsers.** Gradle ANTLR + the Python reference
  jar; both compile against the new rule names.
- [ ] **1.6 Regenerate TextMate.** `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`;
  confirm `project`/`model`/`schema` highlight, old `namespace` keyword removed.

## Done when

- [ ] `pnpm --filter @modeler/parser test` green; old forms rejected.
- [ ] `./gradlew :packages:kotlin:ttr-parser:test` + Python parser tests green.
- [ ] `git status` shows only `TTR.g4`, generation scripts, and
  `syntaxes/ttr.tmLanguage.json` changed (generated dirs stay gitignored).

**Verify:** `pnpm --filter @modeler/parser test` · `./gradlew :packages:kotlin:ttr-parser:test`
