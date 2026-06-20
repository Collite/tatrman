# TTR grammar changelog

Versioning follows `X.Y`:

- **X** — breaking change. New required syntax, removed/renamed constructs, or
  changes that cause previously-valid `.ttr` files to fail to parse.
- **Y** — additive change. New optional constructs, syntactic sugar, parser
  bug fixes, or any change that keeps previously-valid files parsing.

The canonical version lives in the `// @grammar-version:` marker at the top of
`src/TTR.g4`. The prebuild script extracts it into
`src/generated/version.ts`, re-exported from `@modeler/grammar` as
`TTR_GRAMMAR_VERSION`.

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
