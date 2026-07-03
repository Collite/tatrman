# Implementation plan — Qualified-name redesign

Nine phases across **three repos** — `modeler` (this), `ai-platform` (runtime
consumer of the grammar artifacts), `ai-models` (the real TTR content). This is a
**major grammar version** (D11) with a **hard cut** (D13): old keywords are removed,
the migrator rewrites every file, and the artifact bump is coordinated so the runtime
never sees a file it can't parse.

TDD throughout: the named suite is written and made to fail before the implementation
in that phase. Phases 1–6 are landable behind the new grammar before any content is
migrated; Phase 7 is the lockstep cutover.

Global pre-flight (all phases): `pnpm install`; baseline green
`pnpm -r typecheck && pnpm -r test`; Kotlin phases also baseline
`./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-semantics:test`.

> Tooling note: `packages/lsp/src/server.ts` contains a NUL byte — search it with
> `rg -a`; plain `grep` silently skips it.

---

## Phase 1 — Grammar rename (modeler / `TTR.g4`) — **breaking**

**Deliverable.** `TTR.g4` uses the new keywords; all three parsers regenerate; the
parser tests are updated to the new surface.

**Work.**
- `def model` → `def project`: new token `PROJECT : 'project'`; `objectDefinition`
  alt `MODEL id modelDef` → `PROJECT id projectDef`; rename `modelDef`/`modelProperty`
  → `projectDef`/`projectProperty` (body unchanged: description | tags | version).
- Directive: `schemaDirective : SCHEMA schemaCode (NAMESPACE id)?` →
  `modelDirective : MODEL modelCode (SCHEMA id)?`. Reuse the freed `MODEL` token
  (TTR.g4:670) for the type directive; `SCHEMA` (TTR.g4:648) now takes the namespace
  id; **delete** `NAMESPACE` (TTR.g4:649).
- `graphSchemaProperty : SCHEMA … schemaCode` → `graphModelProperty : MODEL … modelCode`.
- Keep the freed keywords (`schema`, old `model`) in `idPart` so they remain usable as
  identifiers.
- Regenerate: `cd packages/parser && pnpm run prebuild`; regenerate the Kotlin parser
  (Gradle ANTLR) and the Python parser; regenerate the TextMate grammar
  (`packages/vscode-ext`, `node scripts/generate-tm-grammar.ts`).

**Tests first.** `@modeler/parser` suites: `def project`, `model db schema dbo`,
`model er` (no schema), and that the old forms now **fail** to parse.

**DONE.** `pnpm --filter @modeler/parser test` green; Kotlin + Python parser suites
green; TextMate regenerated; old keywords rejected.

---

## Phase 2 — AST node renames (modeler / TS + Kotlin)

**Deliverable.** AST reflects the rename with no semantic change yet.

**Work.** `SchemaDirective` → `ModelDirective` (`schemaCode`→`modelCode`, add
`schema?`); `ModelDef`/`ModelProperty` → `ProjectDef`/`ProjectProperty`; update
`walker.ts`, `ast.ts`, the Kotlin model classes, and `docs/grammar-master/AST-NAMING.md`.

**Tests first.** AST-shape snapshots in parser + conformance dump
(`tests/conformance/dump.test.ts` — currently asserts `def model M`; update to
`def project M`).

**DONE.** Parser + conformance snapshots green in TS and Kotlin.

---

## Phase 3 — Manifest: named schemas + package bindings (modeler / TS)

**Deliverable.** `modeler.toml` accepts `[schemas.<name>]` bindings, `[packages.*]
default-schema`, `[defaults] schema`, `[lint] require-qualified-refs`; resolves to a
typed `ManifestConfig` (contracts §1–§2).

**Work.** `semantics/manifest.ts`: add `SchemaBinding`, `PackageConfig`; extend
`ResolvedManifest` (keep the old `namespaces` field readable for the migrator to lift,
then drop). Validate the no-collision rule (D9) at load: schema-name vs package-name
vs model-code vs kind-keyword.

**Tests first.** manifest suite: parse bindings; package default-schema; collision →
error; embedded-SQL `[[sql.namespace-map]]` equivalence (contracts §1.1).

**DONE.** Manifest tests green; `resolveManifest` returns `ManifestConfig`.

---

## Phase 4 — Slot-filling qname + resolver (modeler / TS) — the core

**Deliverable.** `qname.ts` rewritten to the uniform canonical key (contracts §3);
`classifyReference` + `resolveReference` (contracts §4) replace positional parsing;
scoped unique-match + diagnostics.

**Work.**
- Replace `Qname` with the §3 shape (`package, model, schema?, kind, parts`);
  `qnameToKey` package-first.
- `classifyReference(text, vocab)` — vocabulary classification, pure/total.
- `resolveReference(...)` — fill order of architecture §5; scoped unique-match (D10).
- Extract `modelForKind(kind)` from `defaultSchemaForKind`
  (`semantics/default-schema.ts`) as the single source for model + default-schema.
- `reference-index.ts`: drop `namespace || defaultNamespaceForSchema(schema) ||
  def.kind` (line 20); build keys from the uniform slots.
- Header `schema` applies to db/binding defs only (D12).

**Tests first.** `qname.test.ts` + `resolver.test.ts`: full elision
(`shop.sales.Orders`), context kind (`target: { table: X }`), er/md no-schema,
explicit `db.dbo.Orders`, ambiguity, mixed-model file (db def uses header schema, er
def ignores it).

**DONE.** semantics suites green; `pnpm -r typecheck` green.

---

## Phase 5 — Kotlin twin + conformance (modeler) — **normative parity**

**Deliverable.** Kotlin `ttr-semantics` mirrors the slot model; conformance compares
canonical-key + diagnostic-code sets, byte-identical.

**Work.** Port `modelForKind`, the slot fill, and the header-schema-is-db-only rule to
Kotlin; add conformance fixtures (contracts §6) for every surface form + collision
rule.

**DONE.** `./gradlew :packages:kotlin:ttr-semantics:test` green; conformance harness
green (TS dump ≡ Kotlin dump).

---

## Phase 6 — LSP, lint, hosts (modeler / TS)

**Deliverable.** Editor behaviour follows the new model end-to-end.

**Work.** LSP completion/hover/definition speak the new slots and offer schema-handle
completions from the manifest; `@modeler/lint` rules from contracts §5
(`schema-name-collision`, `unknown-package-schema`, `er-md-schema-on-logical`,
`ambiguous-reference`, `require-qualified-refs`); VS Code ext is a thin regen only
(no language logic). Integration tests in `tests/integration/`.

**DONE.** lsp + integration + lint suites green; F5 dev-host opens a `.ttrm` and
resolves `shop.sales.Orders`.

---

## Phase 7 — Migrator (modeler `@modeler/migrate`) — the hard cut

**Deliverable.** A codemod that rewrites any pre-version file + its `modeler.toml`,
runnable as `pnpm --filter @modeler/migrate cli -- migrate-qnames <root>` with
`--dry-run`. This is the tool Phase 8 (ai-platform) and Phase 9 (ai-models) run.

**Work** (extend `migrate/src`, mirror the `phase0.ts` pattern):
1. **Keyword rewrite** (token-aware, trivia-preserving — reuse the CST/trivia layer):
   `def model <id>` → `def project <id>`; directive `schema <code> [namespace <id>]`
   → `model <code> [schema <id>]`; `graph … schema <code>` → `graph … model <code>`.
2. **Manifest lift**: `[schemas] namespaces = { db = "dbo", … }` → one
   `[schemas.<ns>]` table per entry, carrying `database`/`db-schema`/`dialect` pulled
   from any existing `[[sql.namespace-map]]`/`[sql.defaults.*]`; drop the old keys.
3. **Reference rewrites**: *mandatory* for the forms whose meaning changes (D14/D15):
   `db.query.<X>`→`db.dbo.<X>` and `cnc.cnc.<rest>`→`cnc.<rest>`. *Optional* `--shorten`
   pass (off by default) strips other now-redundant model/kind prefixes; everything
   else keeps resolving via slot-filling untouched.
4. **Idempotency + verification**: re-parse every output file; fail the run if any
   file regresses to a parse/resolve error. `--dry-run` prints a unified diff.

**Tests first.** `migrate/src/__tests__`: golden before/after for each rewrite;
idempotency (running twice = no-op); a mixed-model file; a manifest lift; a file that
must **not** change.

**DONE.** migrate suite green; dry-run over `docs/manual/en/examples/retail` produces
a clean, re-parseable tree.

---

## Phase 8 — ai-platform: accept the new grammar (lockstep gate)

**Deliverable.** ai-platform builds and loads against the new **major** parser
artifacts *before* any ai-models content is migrated — so the runtime can parse the
new syntax the instant the content flips.

**Work.**
- Publish the new major `org.tatrman:ttr-parser` (+ `:ttr-semantics`) and the
  `ttr-parser` wheel from `modeler` via the tag-driven `publish.yml`
  (`kotlin/v<x.y.z>`); see `PUBLISHING.md`.
- Bump ai-platform's dependency to the new major; fix any renamed-AST breakages
  (`SchemaDirective`→`ModelDirective`, `ModelDef`→`ProjectDef`); update its
  `computePackageFromPath`/resolution paths if they read the directive.
- ai-platform's loader must accept **both** old and new content during the window?
  **No** — hard cut (D13). Instead, gate the flip: ai-platform on the new artifact is
  merged but the metadata service keeps pointing at the *current* ai-models commit
  until Phase 9 lands, then both advance together.

**DONE.** ai-platform CI green on the new artifacts; conformance parity confirmed; a
staging load of *migrated sample* content (Phase 7 output) succeeds.

---

## Phase 9 — ai-models: migrate the real content (the cutover)

**Repo.** `~/Dev/ai-models`, holding `model-ttr/` (the TTR content the ai-platform
metadata service loads). *Not yet inventoried here — see pre-flight.*

**Pre-flight (needs repo access).** Before writing the cutover PR:
- Inventory: `rg -l '^\s*(schema|def model)\b' model-ttr/` and
  `rg -l 'namespace\b' model-ttr/` for scope; count files, schemas, packages.
- Capture a **baseline**: run the *current* loader/metadata build and snapshot the
  resolved-symbol set (the invariant the migration must preserve).
- Read `model-ttr/modeler.toml` (or equivalent) to know which `namespaces`/databases
  must become `[schemas.*]` bindings.

**Work.**
1. Branch `ai-models`; run `@modeler/migrate migrate-qnames model-ttr/ --dry-run`,
   review the diff, then apply.
2. Lift the manifest (`[schemas] namespaces` → `[schemas.*]` bindings; add
   `[packages.*] default-schema` where a package targets a single db schema, so refs
   shorten cleanly).
3. **Verify equivalence**: re-run the loader against the migrated tree; diff the
   resolved-symbol set against the Phase-9 baseline — **must be identical** (the
   migration is meaning-preserving; only syntax changed).
4. Land ai-models + advance the ai-platform metadata pointer to the migrated commit in
   one coordinated step (Phase 8 already on the new artifact).

**DONE.** Migrated `ai-models` parses + resolves on the new grammar; resolved-symbol
set byte-identical to baseline; metadata service green on staging, then prod.

---

## Cutover ordering (summary)

```
modeler P1–P7  ──►  publish new MAJOR artifacts  ──►  ai-platform P8 (accept)
                                                            │
                                          ai-models P9 (migrate) ──► advance pointer
```

modeler is fully landed and the artifacts published before ai-platform bumps; the
content (ai-models) is the **last** thing to flip, gated behind a parse + resolved-
symbol-equivalence check so the runtime never sees an unparseable file.

## Rollback

Each repo is one revertable PR. If Phase 9 verification diffs, revert the ai-models
branch (pointer still on the old commit) — modeler/ai-platform on the new artifact can
parse old *and* new only insofar as the migrator output is the single source; since
it's a hard cut, rollback = revert the content PR and re-run the migrator after fixing
the codemod. No partial-migration state is ever deployed.
