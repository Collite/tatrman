# TTR grammar changelog

Versioning follows `X.Y`:

- **X** — breaking change. New required syntax, removed/renamed constructs, or
  changes that cause previously-valid `.ttr` files to fail to parse.
- **Y** — additive change. New optional constructs, syntactic sugar, parser
  bug fixes, or any change that keeps previously-valid files parsing.

The canonical version lives in the `// @grammar-version:` marker at the top of
`src/TTR.g4`. The prebuild script extracts it into
`src/generated/version.ts`, re-exported from `@tatrman/grammar` as
`TTR_GRAMMAR_VERSION`.

## 4.4 — 2026-07-13

**Additive (TTR-M lexicon surface — RG-P4, RS-9..11).** The canonical vocabulary
form. No previously-valid 4.3 `.ttr`/`.ttrm`/`.ttrg` file changes meaning — every
new keyword is also added to `idPart`, so nothing common is newly reserved as
anything but an id fragment.

1. **New model code** `lexicon` (`model lexicon`) — `modelCode` gains `| LEXICON`.
2. **Three new def kinds** `term` / `pattern` / `example`, sharing one permissive
   `lexiconEntryDef` body with optional `for` (target ref er/db/md) + `forms` (term
   surface forms) | `match` (pattern regex) | `text` (example text). Per-kind
   required-field validity is SEMANTIC (`@tatrman/semantics`), not grammatical —
   the "parser stays mechanical" invariant.
3. **Unit-level locale header** `model lexicon locale <id>` — `modelDirective`
   gains an optional `( LOCALE id )?` (the `db … schema` precedent slot).
   Locale-only-on-lexicon is enforced in semantics.
4. **Inline `lexicon { … }` sugar** (free-form `object_` body, the semantics-block
   precedent) attachable to data-bearing carriers: er/db `table`/`column`/`entity`/
   `attribute` AND md `measure`/`dimension`/`cubelet` (RS-10 makes md kinds legal
   carriers). Desugars to canonical `term` entries in semantics.
5. **New lexer tokens** `LEXICON`, `TERM`, `PATTERN` (distinct from `PATTERNS`),
   `EXAMPLE` (distinct from `EXAMPLES`), `FOR`, `FORMS`, `MATCH`, `LOCALE` — all
   added to `idPart`. `text:` reuses `TEXT`; the inline `terms:` key stays an
   un-minted bare object key (validated in semantics). See
   `docs/features/resolution/plan/contracts.md` §7.

## 4.3 — 2026-07-07

**Additive (`.ttrl` view-state sidecar — TTR-P Phase 5.2, C1-c-iii).** The
family-wide `.ttrl` sidecar body is now hosted by the TTR-M grammar (not a fresh
`.g4`, not in `TTRP.g4`), as a **separate entry rule** the Kotlin `ttr-parser`
dispatches to on the `.ttrl` extension. No previously-valid 4.2 `.ttr`/`.ttrm`/
`.ttrg` file changes meaning — the new rules are unreachable from `document`.

1. **Two new lexer tokens** `TTRL` (`'ttrl'`), `CANVAS` (`'canvas'`), both added
   to `idPart` so they stay usable as cross-ref / identifier fragments (the
   `WORLD`/`GRAPH` precedent — no common word is newly reserved).
2. **New parser rules** `ttrlDocument : TTRL NUMBER_LITERAL ttrlCanvas* EOF`;
   `ttrlCanvas : CANVAS id LBRACE ttrlProperty* RBRACE`; `ttrlProperty` keys stay
   generic `id` (skin/mode/nodes/collapsed validated in the parser wrapper, not the
   grammar). `nodes` uses a string-keyed `ttrlNodeMap` of `{ x, y }` coords;
   `collapsed` reuses `listOfStrings`. The `edges:` bendPoints slot is intentionally
   NOT in the grammar (reserved, not v1 — C1-c-iii).
3. Promotes the v1.1 in-file `layout` block concept to a standalone document (the
   TTR-M `.ttrl` migration off the v1.1 layout block is a separate post-v1 arc).

## 4.2 — 2026-07-06

**Additive (`semantics { … }` block — grounding Phase 1).** New free-form
`semantics` block property enabling deterministic grounding (time / geography /
money semantic roles) in ai-platform. No previously-valid 4.1 file changes
meaning. See `docs/features/semantics-block/README.md`.

1. **New lexer token `SEMANTICS`** (`'semantics'`), added beside `SEARCH`.
2. **New parser rule `semanticsBlockProperty : SEMANTICS propSep? object_ ;`** —
   attachable on exactly four kinds: `table`, `column`, `entity`, `attribute`.
   NOT on view/relation/query/role. The body is a free-form `object_`
   (`attributesMapProperty` precedent); ALL shape/vocabulary checking lives in
   semantics ("parser stays mechanical"), so new roles need no future grammar
   bump.
3. **`idPart`** gains `SEMANTICS` (4.1 `WORLD` precedent) — keeps files using
   `semantics` as an identifier (e.g. `def attribute semantics { … }`) parsing;
   4.2 stays honestly additive.
4. **Fixtures:** `tests/conformance/fixtures/59-semantics.ttrm` (golden roster —
   entity + attribute + table + column attachments, every role once);
   parser-reject roster under `semantics-negative/`.
5. **Downstream surface:** the validated result reaches consumers via
   `ttr-metadata`'s typed model (`Entity`/`DbTable` `.semanticsKind`,
   `Attribute`/`DbColumn` `.semantics`) and the five `MetadataQuery` grounding
   accessors — see `docs/ttr-metadata/architecture/contracts.md` v1.5.

## 4.1 — 2026-07-05

**Additive (world model — ttr-metadata M0, D-d-α).** New deployment-world model
code; no previously-valid 4.0 file changes meaning.

1. **New model code `world`** (`model world`). `modelCode` gains `| WORLD`.
2. **New top-level def kind `def world <id> { … }`** — the ONLY world def kind.
   `engine`/`executor`/`storage`/world-`schema` exist ONLY nested inside a world
   (grammar-enforced nesting; meaningless outside a world). Per-model validity of
   `def world` itself (world defs only in `model world` files) is semantic.
3. **New lexer tokens:** `WORLD`, `ENGINE`, `EXECUTOR`, `STORAGE`, `EXTENDS`,
   `HOSTS`, `STAGING`. `via:` reuses the 3.1 `VIA` token; `type:`/`version:` reuse
   `DATA_TYPE`/`VERSION`.
4. **New parser rules:** `worldDef`, `worldMember`, `worldProperty`, `engineDef`,
   `executorDef`, `enginePartProperty`, `storageDef`, `storageProperty`,
   `extendsProperty`, `hostsProperty`, `stagingProperty`, `viaProperty`,
   `worldSchemaDef`, `worldSchemaField`. engine/executor/storage bodies list typed
   props first, then a free-form `propertyEntry` fallback (T6 β manifest data —
   transported opaque, interpreted by TTR-P Stage 2.2 only; MD5).
5. **`idPart`** gains `WORLD`, `ENGINE`, `EXECUTOR`, `STORAGE`, `VERSION`.
   `EXTENDS`/`HOSTS`/`STAGING` are deliberately excluded so a malformed
   `staging: "x"` / `hosts: ["x"]` / `extends: "x"` is a hard parse error rather
   than falling through to `propertyEntry` (negative fixtures
   `world-negative/neg-02,03,05`).
6. **Fixtures:** `tests/conformance/fixtures/57-world.ttrm` (golden roster),
   `58-world-extends.ttrm` (overlay input); parser-reject roster under
   `packages/kotlin/ttr-parser/src/test/resources/world-negative/`.

## 4.0 — 2026-06-27

**BREAKING (qname redesign).** Renames the three keywords that name a TTR address
so each word means exactly one concept. Previously valid 3.x files must be
migrated (`@tatrman/migrate`). See `docs/features/qname-redesign/`.

1. **`def model <id>` → `def project <id>`.** New `PROJECT` lexer token; the
   whole-artifact header (identity + `version` + `description` + `tags`) is the
   *project* (one per repo). Rules `modelDef`/`modelProperty` → `projectDef`/
   `projectProperty`; `objectDefinition` alt `MODEL id …` → `PROJECT id …`.
2. **Type directive `schema <code> [namespace <id>]` → `model <code> [schema <id>]`.**
   The freed `MODEL` token now names the model type; the `SCHEMA` token now
   carries the namespace/binding id; the `NAMESPACE` token is **deleted**. Rule
   `schemaDirective` → `modelDirective`, `schemaCode` → `modelCode`.
3. **Graph header property `schema <code>` → `model <code>`** (`graphSchemaProperty`
   → `graphModelProperty`).
4. The freed keywords `schema`, `model`, `project` stay in `idPart` so they remain
   usable as cross-reference / identifier fragments.

`modelCode` keeps all alternatives (`DB | ER | BINDING | QUERY | CNC | MD`) — the
parser stays mechanical. The semantic `ModelCode` set drops `query` (a `def query`
folds into the `db` model, schema-bound) and `cnc` loses its namespace echo; that
folding lives in `@tatrman/semantics`, not the grammar.

## 3.0 — 2026-06-24

**BREAKING (MD Phase 0 legacy renames).** Frees the `map` / `mapping` / `domain`
vocabulary for the upcoming MD model and disambiguates model files. Previously
valid 2.x files must be migrated (the `modeler phase0` CLI automates the steps).

1. **`schema map` → `schema binding`.** New `BINDING` lexer token; `schemaCode`
   now alternates `DB | ER | BINDING | QUERY | CNC`. `MAP` is removed from
   `schemaCode` (so `schema map` no longer parses) but the `MAP` token is
   retained in `idPart` and reserved for the future MD `def map` value-set
   keyword. The `er2db_*` defs are unchanged — only the schema code they live
   under is renamed (qnames move `…map.er2db…` → `…binding.er2db…`).
2. **Inline `mapping:` → `binding:`.** The v2.1 inline mapping property on
   `def entity` / `def attribute` / `def relation` is renamed, reusing the
   `BINDING` token; the `MAPPING` token is removed. The diagnostic code is
   renamed `ttr/duplicate-mapping` → `ttr/duplicate-binding`.
3. **`domain` block / `.ttrd` file kind removed → `def area`.** The top-level
   `domain <id> { … }` block and the `.ttrd` file kind are deleted. Subject
   areas are now a plain `def area <id> { description?, tags?, packages: [...],
   entities: [...] }` definition that lives in ordinary model files, coexists
   with other defs, and registers a resolvable symbol. New `AREA` token; the
   `DOMAIN` token and `domainBlock`/`domainProperty` productions are removed
   (`PACKAGES` / `ENTITIES` tokens retained for the area body). `domain` is
   freed for the future MD value-set keyword. The grouping is renamed end-to-end:
   lint codes `ttr/domain-*` → `ttr/area-*` (`area-empty`, `area-member-not-found`,
   `duplicate-area`, `area-redundant-member`) and the resolved-packages artifact
   key `domains` → `areas`.
4. **Model file extension `.ttr` → `.ttrm`** ("Tatrman Model"). Grammar-external
   (file detection only); `.ttrg` (graph) is unchanged.

Migration: `modeler phase0 <project-root>` renames `*.ttr` → `*.ttrm`, rewrites
`schema map` → `schema binding` and inline `mapping:` → `binding:`, and converts
`.ttrd` `domain { … }` blocks to `def area { … }` in `.ttrm`.

## 2.3 — 2026-06-20

Additive: the `.ttrd` **domain** file kind (Packages & Domains PD2). Editor-only
— ai-platform's loader does NOT load `.ttrd` (consumed by the agent registry +
resolved-packages artifact). Backward compatible — every 2.2 file parses unchanged.

- Added `DOMAIN`, `PACKAGES`, `ENTITIES` lexer tokens; extended `idPart` to keep
  them usable as identifier components.
- Added rules: `domainBlock`, `domainProperty`, `domainPackagesProperty`,
  `domainEntitiesProperty`; `document` now accepts a `domainBlock` alongside
  `graphBlock`.
- File-kind (`.ttrd` ⇔ exactly one domain block) is enforced by semantics, not
  grammar (mirrors `.ttrg`).

## 2.2 — 2026-06 (additive)

Additive: `def drill_map` ("click this row → run that pattern"). Backward
compatible — every 2.0/2.1 file parses unchanged.

- Added `DRILL_MAP`, `ARGS`, `DISPLAY`, `OVERRIDE` lexer tokens; extended `idPart`.
- Added rules: `drillMapDef`, `drillMapProperty`, `argsProperty`,
  `displayProperty`, `overrideProperty`, `drillArgEntry`.

## 2.1 — 2026-05-27

Additive: inline mapping shorthand for er2db_* on entity/attribute/relation.
Backward compatible — every 2.0 file parses unchanged.

- Added `mapping:` property on `def entity`, `def attribute`, and `def relation`.
- Added `MAPPING` lexer token; extended `idPart` to keep it usable as an identifier component.
- Added rules: `mappingProperty`, `mappingValue`, `mappingBlock`,
  `mappingBlockProperty`, `mappingColumnsProperty`, `mappingColumnMap`,
  `mappingColumnEntry`, `mappingColumnValue`.
- Relaxed `targetProperty` to accept a bare id (`target: <ref>`) in addition to
  the existing object form (`target: { column: <ref> }`).
- Reuses existing `COLUMNS` and `FK` tokens; no new keywords beyond `mapping`.

## 2.0 — 2026-05-27

Major version bump promoting the v1.1 "packages" work to a stable grammar
version. Breaking because the document rule now accepts new top-level
constructs and several new reserved keywords were added.

- Added `package <qualifiedName>` top-level declaration.
- Added `import <qualifiedName>[.*]` top-level declaration.
- Added `graph <id> { ... }` block with `schema`, `objects`, and `layout`
  properties (replaces the v1 `.modeler/layout.ttrl` sidecar).
- New lexer tokens: `PACKAGE`, `IMPORT`, `GRAPH`, `OBJECTS`, `LAYOUT`, `STAR`.
- New parser rules: `packageDecl`, `importDecl`, `graphBlock`,
  `graphProperty`, `graphSchemaProperty`, `graphObjectsProperty`,
  `graphLayoutProperty`, `qualifiedName`.
- `idPart` extended so the new keywords remain usable inside cross-reference
  components.

## 1.x

Pre-versioning baseline. See git history under `packages/grammar/src/TTR.g4`.
