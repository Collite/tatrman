# Phase 2 — Core tier (semantics, navigation, hover)

**Status:** v0 draft, ready for Bora review then handoff to Claude Code.
**Branch:** `feat/phase-02-core`
**Time budget:** 4–5 weeks (revised from the original 3–4-week estimate to absorb the project-model work, the diagnostic-taxonomy expansion, and three carryover items from Phase 1: semantic tokens, `parse-recovery-info` emission, and the VS Code smoke test).
**Dependencies:** Phase 1 merged; the **Acceptance criteria for Phase 1 as a whole** at the bottom of `tasks-phase-01-foundation.md` satisfied.
**Blocks:** Phase 3 (Designer needs a real `modeler/getModelGraph` payload, which depends on §A; the Designer's reference-following needs §D's resolver).

## How to read this document

This document is **detailed on purpose**. The reader is expected to be relatively new to language-tooling code: ANTLR walkers, LSP servers, symbol tables. Sections that look long will save you time later — they spell out which grammar rule produces which AST shape, which file to put each class in, and which pitfalls have already been hit by Phase 0/1.

Conventions used:
- File paths are always full, starting from the repo root: `packages/parser/src/ast.ts`, not `ast.ts`.
- Type and function names are in `code` font.
- Direct references to grammar rules use the grammar's own naming, e.g. `entityProperty` is rule `entityProperty` in `packages/grammar/src/TTR.g4`.
- "**Verify by running**" sections describe the exact command and expected outcome — run them before ticking the box.
- "**Common pitfalls**" sections list mistakes that have already been made in Phase 0 / Phase 1; treat them as warnings.

If a step is unclear, stop and ask before guessing. Phase 0 and Phase 1 had review cycles that surfaced silent skips and "best-effort" interpretations of the plan; Phase 2 is the first phase where users will *use* the tooling against real models, so quiet shortcuts will turn into bug reports.

## Goal

Phase 1 left us with a parser that produces stub AST nodes (kind + name + source location), an LSP that publishes parse-error diagnostics, a Designer that renders one node per definition, and zero understanding of what those definitions *mean*. Phase 2 closes that gap. After Phase 2:

- The parser produces a fully populated AST for every grammar construct.
- The semantics layer builds a project-wide symbol table from all `.ttr` files in a project, plus the stock vocabulary (`fact`, `dimension`, etc.).
- Cross-references like `er.entity.artikl.id_artiklu` resolve to their definitions; unresolved ones produce diagnostics.
- A per-kind validator catches structural problems (missing required properties, primary-key columns that don't exist, etc.).
- The LSP exposes go-to-definition, find-references, hover, and workspace symbol search.
- Semantic tokens highlight dotted refs and definition names that TextMate alone can't disambiguate.
- The VS Code smoke test deferred from Phase 1 lands.

After Phase 2 a real user can author a TTR project in VS Code and feel like the editor understands what they're writing — the Phase-2 deliverable that justifies all the prior infrastructure work.

## Pre-flight

- [ ] Confirm Phase 1 is merged and its **Acceptance criteria for Phase 1 as a whole** are satisfied: `pnpm -r build && test && lint && typecheck` exits 0; `pnpm --filter @modeler/integration-tests test` exits 0; the demo path in Phase 1's spec works by hand.
- [ ] Create branch `feat/phase-02-core` from the merged Phase 1 PR.
- [ ] Create `docs/plan/progress-phase-02.md` mirroring the section headers in this document; tick boxes there as you complete tasks.
- [ ] Re-read `docs/design/architecture.md` §4.2 (parser), §4.3 (semantics), §4.5 (LSP), §5 (project model), §8.2 (localization), §8.3 (error handling).
- [ ] Re-read `packages/grammar/src/TTR.g4`, paying attention to: `objectDefinition` (top-level kinds), `*Property` rules (per-kind property surfaces), `value`/`literal`/`list`/`object_` (generic value forms), `localizedString`, `valueLabelsBody`, `searchBlock`, `dataType` (especially the structured form).
- [ ] Re-read `docs/plan/progress-phase-01.md` "Deferred to Later Phases" — those carryovers are §K, §L, §M of this plan.
- [ ] Open `samples/v1-metadata/er.ttr`, `samples/v1-metadata/db.ttr`, and `samples/v1-metadata/map.ttr` and skim them. The Phase 2 work has to handle every shape that appears in these files.

---

## Section A — AST completion

This is the largest section. Today `Definition` carries only `kind`, `name`, and `source`; everything inside the `{ … }` body is discarded. Phase 2 changes that: every property the grammar permits ends up in the AST, with source locations on every value node, ready for the semantics layer to consume in §C–§E.

The work is broken into 11 sub-sections (A.1 through A.11). Do them in order — later ones depend on earlier ones.

### A.1 — Common AST types

Add new types to `packages/parser/src/ast.ts` that every per-kind type below will use. Place these new types **above** the existing `SchemaDirective` declaration so per-kind interfaces below can reference them.

- [ ] **`PropertyValue` discriminated union.** Mirror the grammar's `value` rule. Every variant carries `source: SourceLocation`. Define:
  ```ts
  export type PropertyValue =
    | StringValue
    | TripleStringValue
    | NumberValue
    | BoolValue
    | NullValue
    | IdValue
    | ListValue
    | ObjectValue
    | FunctionCallValue;

  export interface StringValue { kind: 'string'; value: string; source: SourceLocation; }
  export interface TripleStringValue { kind: 'tripleString'; value: string; source: SourceLocation; }
  export interface NumberValue { kind: 'number'; value: number; source: SourceLocation; }
  export interface BoolValue { kind: 'bool'; value: boolean; source: SourceLocation; }
  export interface NullValue { kind: 'null'; source: SourceLocation; }
  export interface IdValue { kind: 'id'; path: string; parts: string[]; source: SourceLocation; }
  export interface ListValue { kind: 'list'; items: PropertyValue[]; source: SourceLocation; }
  export interface ObjectValue { kind: 'object'; entries: ObjectEntry[]; source: SourceLocation; }
  export interface ObjectEntry { key: string; value: PropertyValue; source: SourceLocation; }
  export interface FunctionCallValue { kind: 'functionCall'; name: string; args: PropertyValue[]; source: SourceLocation; }
  ```
- [ ] **`Reference`.** Cross-references (refs to other defs) are dotted ids that the parser doesn't resolve — see architecture §4.2. Distinct from `IdValue` so the resolver can find them quickly.
  ```ts
  export interface Reference { path: string; parts: string[]; source: SourceLocation; }
  ```
- [ ] **`LocalizedString` and `LocalizedStringList`.** Phase 2.2 of ai-platform's grammar.
  ```ts
  export interface LocalizedString { kind: 'localizedString'; entries: Record<string, string>; source: SourceLocation; }
  export interface LocalizedStringList { kind: 'localizedStringList'; entries: Record<string, string[]>; source: SourceLocation; }
  ```
  Note: `Record<string, string>` keys are BCP-47 language tags (`cs`, `en`, `de`, …). Empty objects `{}` are valid (the grammar allows it; emit a warning later in §E).
- [ ] **`DataType`.** Mirror the grammar's `dataType` rule (covers both shorthand and structured forms).
  ```ts
  export type DataType = SimpleDataType | StructuredDataType;
  export interface SimpleDataType { kind: 'simple'; name: string; source: SourceLocation; }
  export interface StructuredDataType {
    kind: 'structured';
    typeName: string;
    length?: number;
    precision?: number;
    source: SourceLocation;
  }
  ```
- [ ] **Enums for typed property values.** Strings, not enums, to match the grammar literally:
  ```ts
  export type IndexType = 'primary' | 'secondary' | 'ordered' | 'btree' | 'fulltext';
  export type ConstraintType = 'unique' | 'notNull';
  export type QueryLanguage = 'SQL' | 'TRANSFORMATION_DSL' | 'DATAFRAME_DSL' | 'REL_NODE';
  export type ParameterDirection = 'IN' | 'OUT' | 'INOUT';
  ```
- [ ] **`SearchBlock`.**
  ```ts
  export interface SearchBlock {
    kind: 'searchBlock';
    keywords?: LocalizedStringList;
    patterns?: string[];
    descriptions?: LocalizedStringList;
    examples?: string[];
    aliases?: string[];
    source: SourceLocation;
  }
  ```
- [ ] **`ValueLabels`.** A map from a string (e.g. `"1"`, `"2"`) to a `LocalizedString`. The grammar production is `valueLabelsBody`.
  ```ts
  export interface ValueLabels {
    kind: 'valueLabels';
    entries: Array<{ key: string; label: LocalizedString; source: SourceLocation }>;
    source: SourceLocation;
  }
  ```
- [ ] **`ParameterDef`.** Used by `procedureDef` and `queryDef`. Grammar: `parameterInline`.
  ```ts
  export interface ParameterDef {
    name: string;
    type?: DataType;
    label?: string;
    direction?: ParameterDirection;
    source: SourceLocation;
  }
  ```

**Verify by running**: `pnpm --filter @modeler/parser typecheck` exits 0 (no implementations yet, just types).

### A.2 — Per-kind property and definition types

Replace each of the 17 stub `*Def` interfaces in `packages/parser/src/ast.ts` with a fully-populated version. Every property field is **optional** (the grammar makes them so, except where noted in `*Property` rule comments).

For each kind, list every property the grammar's `*Property` rule allows. Use the type from §A.1 that matches the grammar's value form. The mapping is mechanical but tedious; double-check by grepping the grammar for the property name.

- [ ] **`ModelDef`** (grammar: `modelProperty`).
  ```ts
  export interface ModelDef {
    kind: 'model';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    version?: string;
  }
  ```
- [ ] **`TableDef`** (grammar: `tableProperty`).
  ```ts
  export interface TableDef {
    kind: 'table';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    primaryKey?: string[];
    columns?: ColumnDef[];          // produced by inline columns
    indices?: IndexDef[];
    constraints?: ConstraintDef[];
  }
  ```
- [ ] **`ViewDef`** (grammar: `viewProperty`).
  ```ts
  export interface ViewDef {
    kind: 'view';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    columns?: ColumnDef[];
    definitionSql?: StringValue | TripleStringValue;
  }
  ```
- [ ] **`ColumnDef`** (grammar: `columnProperty`). Used both at top level (`def column foo { … }`) and inline within `tableDef.columns: [ def column ... ]`.
  ```ts
  export interface ColumnDef {
    kind: 'column';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    type?: DataType;
    optional?: boolean;
    isKey?: boolean;
    searchable?: boolean;
    indexed?: boolean;
  }
  ```
- [ ] **`IndexDef`** (grammar: `indexProperty`).
  ```ts
  export interface IndexDef {
    kind: 'index';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    indexType?: IndexType;
    columns?: string[];
  }
  ```
  Note: `indexType` comes from `indexTypeProperty` (the grammar uses the `DATA_TYPE` token but with `indexTypeValue` on the right side — *don't* confuse this with `ColumnDef.type`).
- [ ] **`ConstraintDef`** (grammar: `constraintProperty`). Same dual `type` token issue as `IndexDef`.
  ```ts
  export interface ConstraintDef {
    kind: 'constraint';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    constraintType?: ConstraintType;
    columns?: string[];
  }
  ```
- [ ] **`FkDef`** (grammar: `fkProperty`). `from` and `to` are references (cross-schema).
  ```ts
  export interface FkDef {
    kind: 'fk';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    from?: PropertyValue;            // typically an IdValue; use Reference if id-shaped
    to?: PropertyValue;
  }
  ```
  Implementation note: `from` / `to` in the grammar are `value` (could be id, list, object, etc.). Keep them as `PropertyValue` so the parser stays mechanical; the resolver in §D extracts a `Reference` when the value is an id.
- [ ] **`ProcedureDef`** (grammar: `procedureProperty`).
  ```ts
  export interface ProcedureDef {
    kind: 'procedure';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    parameters?: ParameterDef[];
    resultColumns?: ColumnDef[];
  }
  ```
- [ ] **`EntityDef`** (grammar: `entityProperty`).
  ```ts
  export interface EntityDef {
    kind: 'entity';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    labelPlural?: string;
    nameAttribute?: Reference;
    codeAttribute?: Reference;
    aliases?: string[];
    attributes?: AttributeDef[];
    roles?: string[];                // listOfIds; bare role names that resolve via cnc.role.*
    displayLabel?: LocalizedString;
    search?: SearchBlock;
  }
  ```
- [ ] **`AttributeDef`** (grammar: `attributeProperty`).
  ```ts
  export interface AttributeDef {
    kind: 'attribute';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    type?: DataType;
    isKey?: boolean;
    optional?: boolean;
    searchable?: boolean;
    valueLabels?: ValueLabels;
    displayLabel?: LocalizedString;
    search?: SearchBlock;
  }
  ```
- [ ] **`RelationDef`** (grammar: `relationProperty`).
  ```ts
  export interface RelationDef {
    kind: 'relation';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    from?: PropertyValue;
    to?: PropertyValue;
    cardinality?: ObjectValue;       // grammar: `object_` — see Round 2 of er.ttr samples
    join?: ListValue;
  }
  ```
- [ ] **`Er2dbEntityDef`** (grammar: `er2dbEntityProperty`).
  ```ts
  export interface Er2dbEntityDef {
    kind: 'er2dbEntity';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    entity?: Reference;              // ER side
    target?: ObjectValue;            // DB side; structured object
    whereFilter?: ObjectValue;
  }
  ```
- [ ] **`Er2dbAttributeDef`** (grammar: `er2dbAttributeProperty`).
  ```ts
  export interface Er2dbAttributeDef {
    kind: 'er2dbAttribute';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    attribute?: Reference;
    target?: ObjectValue;
  }
  ```
- [ ] **`Er2dbRelationDef`** (grammar: `er2dbRelationProperty`).
  ```ts
  export interface Er2dbRelationDef {
    kind: 'er2dbRelation';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    relation?: Reference;
    fk?: Reference;
  }
  ```
- [ ] **`QueryDef`** (grammar: `queryProperty`).
  ```ts
  export interface QueryDef {
    kind: 'query';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    language?: QueryLanguage;
    parameters?: ParameterDef[];
    sourceText?: StringValue | TripleStringValue;
    search?: SearchBlock;
  }
  ```
- [ ] **`RoleDef`** (grammar: `roleProperty`).
  ```ts
  export interface RoleDef {
    kind: 'role';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    label?: LocalizedString;
    search?: SearchBlock;
  }
  ```
- [ ] **`Er2cncRoleDef`** (grammar: `er2cncRoleProperty`).
  ```ts
  export interface Er2cncRoleDef {
    kind: 'er2cncRole';
    name: string;
    source: SourceLocation;
    description?: StringValue | TripleStringValue;
    tags?: string[];
    entity?: Reference;
    role?: Reference;
  }
  ```

**Verify by running**: `pnpm --typecheck` from the parser package — expect type errors in `walker.ts` because nothing populates these new fields yet. Don't ship A.2 alone; A.3+ is what fills them in.

### A.3 — Walker for value forms

Add `walkValue(ctx)` in `packages/parser/src/walker.ts` that takes the grammar's `value` parser context and returns a `PropertyValue`. The grammar's `value` rule is:

```
value : literal | id | list | object_ | functionCall ;
```

- [ ] **`walkValue(ctx: ValueContext, file: string): PropertyValue`** — dispatch on which sub-rule produced a non-null context. For each case, call a more specific `walkLiteral`, `walkId`, `walkList`, `walkObject`, `walkFunctionCall`.
- [ ] **`walkLiteral(ctx: LiteralContext, file: string): PropertyValue`** — dispatch on `NUMBER_LITERAL` / `BOOLEAN_LITERAL` / `NULL_LITERAL` / `stringLiteralForm`. For numbers, parse via `Number(text)`; for booleans, compare text to `'true'`; for strings, see A.4.
- [ ] **`walkStringLiteralForm(ctx, file): StringValue | TripleStringValue`** — strip quotes; for `STRING_LITERAL`, also handle the standard escape sequences (`\n`, `\r`, `\t`, `\\`, `\"`); for `TRIPLE_STRING_LITERAL`, strip the outer `"""` then run the same dedent algorithm ai-platform's parser uses (Python's `textwrap.dedent`). The walker already imports a `Dedent` helper in ai-platform; mirror it. *Do not* skip dedent — sample files rely on it.
- [ ] **`walkId(ctx: IdContext, file: string): IdValue`** — `id` is one or more dotted parts. Parts are extracted from `idPart()` children. Both `path` (joined with `.`) and `parts` (the array) live on the result — different consumers want each shape.
- [ ] **`walkList(ctx: ListContext, file: string): ListValue`** — recurse `walkValue` on each child.
- [ ] **`walkObject(ctx: Object_Context, file: string): ObjectValue`** — for each `propertyEntry`, take the key (`key()` is just an `id` — call `getText()`) and recurse `walkValue` on the value child.
- [ ] **`walkFunctionCall(ctx, file): FunctionCallValue`** — name = the leading `id`, args = each `value` child mapped through `walkValue`.

**Common pitfall**: ANTLR's `*Context` accessors return `null` for absent rules. Always check before calling `getText()` or recursing. Use the existing `walker.ts` style: `if (ctx.foo()) { … }`.

**Verify by writing a quick scratch test** (don't commit):
```ts
parseString('def fk x { from: db.dbo.t.col }');
// inspect ast.definitions[0].from — should be IdValue { path: 'db.dbo.t.col', parts: ['db','dbo','t','col'] }
```

### A.4 — Walker for common properties

Many `*Property` rules accept `descriptionProperty`, `tagsProperty`, etc. Centralize so each per-kind walker can call one helper.

- [ ] **`extractCommonProperties(propertyContexts): { description?, tags?, version? }`** — given an array of property parser contexts (whose types vary per kind), iterate and pull out the standard ones. Use a switch-on-context-shape pattern; e.g.
  ```ts
  for (const p of propertyContexts) {
    if (p.descriptionProperty()) {
      out.description = walkStringLiteralForm(p.descriptionProperty().stringLiteralForm(), file);
    }
    if (p.tagsProperty()) {
      out.tags = walkListOfStrings(p.tagsProperty().listOfStrings());
    }
    // …
  }
  ```
- [ ] **`walkListOfStrings(ctx)`**: the grammar's `listOfStrings` rule. Returns `string[]`, stripping quotes (use `walkStringLiteralForm` and read `.value`).
- [ ] **`walkListOfIds(ctx)`**: the grammar's `listOfIds`. Returns `string[]` — bare ids, no dots.

### A.5 — Per-kind walker functions: db kinds

Replace the existing `walkDefinition` body in `walker.ts`. Today it returns stub `{ kind, name, source }`. Replace each branch with a call to a per-kind `walkXxxDef(ctx, file): XxxDef` function that:

1. Calls the existing common-properties helper.
2. Dispatches on each context type the grammar permits for that kind.
3. Returns the populated typed object.

Implement the db-side kinds first (table, view, column, index, constraint, fk, procedure):

- [ ] `walkModelDef(objDef, file)` — model has just description/tags/version
- [ ] `walkTableDef(objDef, file)` — extract `primaryKey` from `primaryKeyProperty`, `columns` from `columnsProperty` (which contains a `columnDefList` — see A.9), `indices` from `indicesProperty`, `constraints` from `constraintsProperty`
- [ ] `walkViewDef(objDef, file)` — extract `definitionSql` from `definitionSqlProperty.stringLiteralForm()`, `columns` like in `walkTableDef`
- [ ] `walkColumnDef(objDef, file)` — extract `type` (see A.10 for `dataType`), `optional` / `isKey` / `searchable` / `indexed` from their boolean property rules
- [ ] `walkIndexDef(objDef, file)` — extract `indexType` (one of the IndexType strings), `columns` from `columnNamesListProperty.listOfStrings()`
- [ ] `walkConstraintDef(objDef, file)` — extract `constraintType`, `columns` similarly
- [ ] `walkFkDef(objDef, file)` — extract `from` / `to` via `walkValue`
- [ ] `walkProcedureDef(objDef, file)` — extract `parameters` (see A.9), `resultColumns` (also a `columnDefList`)

**Wire these into `walkDefinition`** so each branch returns the typed result.

### A.6 — Per-kind walker functions: er kinds

- [ ] `walkEntityDef(objDef, file)` — extract every entity property; `nameAttribute` / `codeAttribute` are bare ids → `Reference`; `roles` is a `listOfIds`; `attributes` is an `attributeDefList`; `displayLabel` is a `localizedString`; `search` is a `searchBlock`
- [ ] `walkAttributeDef(objDef, file)` — extract `type`, the four boolean properties, `valueLabels` (the only kind that gets it), `displayLabel`, `search`
- [ ] `walkRelationDef(objDef, file)` — extract `from` / `to` (PropertyValue), `cardinality` (ObjectValue), `join` (ListValue)

### A.7 — Per-kind walker functions: map kinds

- [ ] `walkEr2dbEntityDef(objDef, file)` — `entity` is a `Reference`; `target` and `whereFilter` are `ObjectValue`s
- [ ] `walkEr2dbAttributeDef(objDef, file)` — `attribute` is a `Reference`; `target` is an `ObjectValue`
- [ ] `walkEr2dbRelationDef(objDef, file)` — `relation` and `fk` are both `Reference`s

### A.8 — Per-kind walker functions: query and cnc kinds

- [ ] `walkQueryDef(objDef, file)` — `language` is a `QueryLanguage`; `parameters` is a `parameterDefList` (see A.9); `sourceText` is a string literal form; `search` is a `searchBlock`
- [ ] `walkRoleDef(objDef, file)` — `label` is a `LocalizedString`
- [ ] `walkEr2cncRoleDef(objDef, file)` — `entity` and `role` are `Reference`s

### A.9 — Inline def lists

These are the trickiest part of the grammar because the same `def column …` syntax appears at the top level *and* inside a `columnsProperty: [ def column ..., def column ... ]`. The walker needs to recursively re-enter the column walker.

- [ ] **`walkColumnDefList(ctx: ColumnDefListContext, file): ColumnDef[]`** — for each `columnInline` child, build a `ColumnDef` by extracting properties from its inline `columnDef` body. Re-use `walkColumnDef`'s property extraction logic (factor it out so both top-level and inline call the same helper).
- [ ] **`walkIndexDefList`**, **`walkConstraintDefList`**, **`walkAttributeDefList`** — same pattern.
- [ ] **`walkParameterDefList(ctx, file): ParameterDef[]`** — for each `parameterInline` child, extract `name` (id), `type` (`dataType`), `label` (string), `direction` (id, then map to `ParameterDirection`).

**Common pitfall**: the inline form has its own `*Def` rule. Don't try to re-route it through the top-level `walkDefinition` — they're separate parser contexts. Factor out the body-extraction logic so it's reusable, and call it from both places.

### A.10 — `dataType` and reference detection

- [ ] **`walkDataType(ctx: DataTypeContext, file): DataType`** — the grammar:
  ```
  dataType
    : typeValue
    | LBRACE dataTypeProperty (COMMA? dataTypeProperty)* COMMA? RBRACE
    ;
  ```
  Shorthand form: a single `typeValue` → `SimpleDataType { name: <text>, source }`. Structured form: an object with `type`/`length`/`precision` keys → `StructuredDataType { typeName, length?, precision?, source }`. The `typeValue` rule itself accepts `text|int|float|bool|datetime|string|boolean|number|integer|double|object|list|char|varchar|decimal|date|timestamp|<id>` — all handled by `getText()`.
- [ ] **`extractReference(value: PropertyValue): Reference | null`** — helper used by per-kind walkers when a property's grammar slot is a `value` but the resolver should treat it as a ref. Return `Reference` only when the value is an `IdValue`; otherwise `null`. This is deferred to the resolver in §D, but implementing the helper here keeps the walker code clean.

### A.11 — Tests + golden fixtures + parseDirectory

- [ ] **Per-kind unit tests** in `packages/parser/src/__tests__/parser-properties.test.ts`:
  - Parse a minimal example for each of the 17 kinds with one of each allowed property; assert each property is populated with the correct type.
  - Example pattern:
    ```ts
    it('TableDef extracts primaryKey, columns, indices', () => {
      const r = parseString(`def table T { primaryKey: ["id"], columns: [ def column id { type: int, isKey: true } ] }`);
      expect(r.errors).toHaveLength(0);
      const t = r.ast?.definitions[0] as TableDef;
      expect(t.kind).toBe('table');
      expect(t.primaryKey).toEqual(['id']);
      expect(t.columns).toHaveLength(1);
      expect(t.columns![0].name).toBe('id');
      expect((t.columns![0].type as SimpleDataType).name).toBe('int');
      expect(t.columns![0].isKey).toBe(true);
    });
    ```
- [ ] **Golden-tree tests** in `packages/parser/src/__tests__/parser-samples.test.ts`:
  - For each `.ttr` under `samples/v1-metadata/`, parse it and assert: zero errors, every definition has its expected properties present, no `null`/`undefined` leak into required fields.
  - Use a small assertion helper to walk the AST and assert "no `undefined` where the type says non-optional."
- [ ] **`parseDirectory(rootPath, recursive = true)`** in `packages/parser/src/index.ts`:
  - Walk the directory finding all `.ttr` files (excluding `.modeler/` and `samples/broken/`).
  - Return `Promise<ParseResult[]>`.
  - Mirror ai-platform's `TtrLoader.parseDirectory`. Test: parses `samples/v1-metadata/` recursively → returns 4 ParseResults, all error-free.

**Acceptance for §A**: every sample under `samples/v1-metadata/` parses with zero errors AND every populated property has the expected type and value; `pnpm --filter @modeler/parser test` exits 0; the parser-samples test takes <2 seconds total.

---

## Section B — Project model and `modeler.toml`

Before the symbol table can be built (§C), the LSP needs to know what files belong to a project. The project root determines the scope of cross-reference resolution; the manifest, when present, configures schemas, namespaces, preferred language, lint rules, and stock-vocabulary loading. Architecture §5 has the manifest schema.

### B.1 — Manifest types and TOML parser

- [ ] Pick a TOML parser. Recommendation: **`smol-toml`** (modern, TypeScript-native, maintained). Add to `@modeler/semantics` dependencies: `pnpm --filter @modeler/semantics add smol-toml`.
- [ ] In a new file `packages/semantics/src/manifest.ts`:
  ```ts
  export interface ProjectManifest {
    project?: { name?: string; version?: string };
    language?: { preferred?: string };
    schemas?: { declared?: string[]; namespaces?: Record<string, string> };
    stock?: { load?: string[] };
    lint?: { strict?: boolean; requireDescriptions?: boolean };
  }

  export interface ResolvedManifest {
    name: string;                       // project.name || basename of root
    preferredLanguage: string;          // language.preferred || 'en'
    declaredSchemas: string[];          // schemas.declared || ['db','er','map','query','cnc']
    namespaces: Record<string, string>; // schemas.namespaces || {}
    stockVocabularies: string[];        // stock.load || ['cnc-roles']
    lint: { strict: boolean; requireDescriptions: boolean };
  }

  export function parseManifest(content: string): ProjectManifest;
  export function resolveManifest(m: ProjectManifest | undefined, projectRoot: string): ResolvedManifest;
  ```
- [ ] Implement both functions; `parseManifest` uses `smol-toml`'s `parse`. Document the defaults in JSDoc above `resolveManifest`.

### B.2 — Project root resolution

- [ ] In `packages/semantics/src/project.ts`:
  ```ts
  export interface Project {
    root: string;                        // absolute path of project root
    manifest: ResolvedManifest;
    ttrFiles: string[];                  // discovered .ttr files (Node mode)
  }

  export async function findProjectRoot(documentPath: string, workspaceFolder: string): Promise<string>;
  export async function loadProject(root: string): Promise<Project>;
  ```
- [ ] `findProjectRoot`: walk up from `documentPath`'s directory; the first directory containing `modeler.toml` is the root. If no manifest is found before crossing `workspaceFolder`, return `workspaceFolder` (convention default per architecture D4).
- [ ] `loadProject`: read `<root>/modeler.toml` if present, parse it, resolve. Walk the directory recursively for `.ttr` files (skip `.modeler/`, `node_modules/`, `.git/`). Return the populated `Project`.
- [ ] **Browser mode caveat**: `loadProject` uses `fs/promises` — it will fail in the browser bundle. Add a second exported entry point `loadProjectFromOpenDocuments(documents: TextDocument[], rootUri: string): Project` that builds the project from already-open documents and skips file-system discovery. Document this explicitly in `packages/semantics/README.md`.

### B.3 — `modeler/getProjectInfo` LSP method

- [ ] In `packages/lsp/src/server.ts`, add a custom request handler:
  ```ts
  connection.onRequest('modeler/getProjectInfo', async (params: { textDocument: { uri: string } }): Promise<ResolvedManifest & { root: string; ttrFileCount: number }> => { … });
  ```
- [ ] On the request, resolve the project root for the document's URI, load the project (or load-from-open-documents in browser mode), and return the resolved manifest plus the root path and discovered file count.
- [ ] Add a unit test that mounts a temporary directory with a `modeler.toml` and a `.ttr` file, opens the file in the LSP, and asserts the returned project info.

### B.4 — Sample manifest

- [ ] Add `samples/v1-metadata/modeler.toml` matching architecture §5's example:
  ```toml
  [project]
  name = "df-erp-metadata"

  [language]
  preferred = "cs"

  [schemas]
  declared = ["db", "er", "map"]
  namespaces = { db = "dbo", er = "entity", map = "er2db" }

  [stock]
  load = ["cnc-roles"]

  [lint]
  strict = false
  require-descriptions = false
  ```
- [ ] Confirm the manifest is consumed by the `loadProject` integration test (§C.6 below).

**Acceptance for §B**: opening `samples/v1-metadata/er.ttr` in VS Code with the manifest present causes `modeler/getProjectInfo` to return `name: 'df-erp-metadata'`, `preferredLanguage: 'cs'`, `ttrFileCount: 4` (or whatever the count is); without the manifest, the same call returns the convention defaults.

---

## Section C — Symbol table

The symbol table is the project-wide index `qname → symbol-info`. It powers go-to-definition, find-references, hover, workspace symbols, and the resolver's lookups.

### C.1 — Qname structure

A qname is `<schemaCode>.<namespace>.<localName>` for top-level defs (per architecture §4.5), with sub-qnames for nested defs (e.g. `er.entity.artikl.id_artiklu`). Define:

- [ ] In `packages/semantics/src/qname.ts`:
  ```ts
  export interface Qname {
    schemaCode: string;       // 'db' | 'er' | 'map' | 'query' | 'cnc'
    namespace: string;        // from schemaDirective; '' if none
    parts: string[];          // local name path; e.g. ['artikl', 'id_artiklu']
  }

  export function qnameToString(q: Qname): string;
  export function parseQname(text: string): Qname | null;       // dotted string → Qname
  export function buildQname(schemaCode: string, namespace: string, parts: string[]): Qname;
  ```
- [ ] Tests: round-trip a few realistic qnames; assert correct handling of empty namespaces and nested parts.

### C.2 — Symbol table (per-document)

- [ ] In `packages/semantics/src/symbol-table.ts`:
  ```ts
  export interface SymbolEntry {
    qname: string;            // canonical dotted form
    kind: Definition['kind']; // narrows via Phase 1's discriminated union
    name: string;             // local name (last segment)
    source: SourceLocation;   // pointing at the def (the `def` keyword's range works)
    documentUri: string;      // 'file://…/er.ttr'
    parent?: string;          // parent qname for nested defs (entity → attribute, table → column)
  }

  export class DocumentSymbolTable {
    constructor(documentUri: string, ast: Document, schemaCode: string, namespace: string);
    get(qname: string): SymbolEntry | undefined;
    all(): SymbolEntry[];
    inDocument(): SymbolEntry[];     // same as all() for DocumentSymbolTable; convenience for the project class
  }
  ```
- [ ] Build logic: walk every top-level `Definition`; emit a `SymbolEntry`. For each `EntityDef`, walk `attributes[]` and emit a nested entry per attribute (qname `er.<ns>.<entityName>.<attributeName>`). Same for `TableDef.columns[]`, `ProcedureDef.resultColumns[]`, etc.
- [ ] `schemaCode` and `namespace` come from the document's `SchemaDirective`; if absent, use the project's `[schemas].namespaces[<schemaCode>]` from the manifest, then `''` as last resort.
- [ ] **Common pitfall**: an `EntityDef` produces both a top-level `er.<ns>.entity-name` symbol and N child `er.<ns>.entity-name.attribute-name` symbols. Don't forget the children — find-references and resolver depend on them.
- [ ] Tests: parse `samples/v1-metadata/er.ttr`, build a `DocumentSymbolTable`, assert that `er.entity.artikl` and `er.entity.artikl.id_artiklu` both resolve.

### C.3 — Project symbol table

- [ ] In `packages/semantics/src/project-symbols.ts`:
  ```ts
  export class ProjectSymbolTable {
    constructor();
    upsertDocument(uri: string, ast: Document, schemaCode: string, namespace: string): void;
    removeDocument(uri: string): void;
    get(qname: string): SymbolEntry | undefined;
    all(): SymbolEntry[];
    findByName(name: string): SymbolEntry[];        // bare-name search (multi-result)
    duplicates(): Array<{ qname: string; entries: SymbolEntry[] }>;
  }
  ```
- [ ] Internal storage: `Map<documentUri, DocumentSymbolTable>` plus a denormalized `Map<qname, SymbolEntry[]>` (multi-value for duplicate detection).
- [ ] `upsertDocument`: replaces the document's prior entry (if any) and rebuilds the merged view.
- [ ] **Conflict / duplicate detection**: `duplicates()` returns every qname owned by more than one source location. The validator (§E) consumes this to emit `ttr/duplicate-definition` diagnostics.
- [ ] Tests: build a project from two synthetic documents that both define `er.entity.foo`; assert `duplicates()` lists exactly that qname with both entries.

### C.4 — Stock vocabulary

Architecture §4.3 puts stock vocabulary in `packages/semantics/src/stock/`. The default load is `cnc-roles`, which corresponds to ai-platform's six built-in CNC roles.

- [ ] Create `packages/semantics/src/stock/cnc-roles.ttr` containing six role defs:
  ```
  schema cnc namespace role

  def role fact { description: "Fact role — measurable events" }
  def role dimension { description: "Dimension role — descriptive context" }
  def role structural { description: "Structural role — defines hierarchy" }
  def role master { description: "Master role — reference data" }
  def role transaction { description: "Transaction role — operational events" }
  def role bridge { description: "Bridge role — many-to-many resolver" }
  ```
  (Cross-check ai-platform's built-in vocabulary if it has changed since the architecture doc was written; ai-platform's `BuiltinStockSource` is the source of truth — sync the file content via the same mechanism the grammar uses.)
- [ ] In `packages/semantics/src/stock-loader.ts`:
  ```ts
  export async function loadStockVocabularies(names: string[]): Promise<Document[]>;
  ```
- [ ] Use `parseString(content, fileLabel)` from `@modeler/parser` with `fileLabel = 'stock://cnc-roles.ttr'` so symbol entries from stock vocabularies are distinguishable from project-defined symbols.
- [ ] In the project loader (§B.2), call `loadStockVocabularies(manifest.stockVocabularies)` and merge their documents into the project symbol table **before** project files. The reconciler (§E.4) treats stock symbols as protected — user files cannot redefine them.

### C.5 — Incremental rebuild on document change

- [ ] In `packages/lsp/src/server.ts`, hold a single `ProjectSymbolTable` instance per project. On `documents.onDidChangeContent`, re-parse the document and call `projectSymbols.upsertDocument(uri, ast, …)`. On `onDidClose`, do **not** remove (the file may still be on disk and matter to other documents); only remove on `workspace/didChangeWatchedFiles` deletion events.
- [ ] Performance: for a 100-document project, a single document change should rebuild only that document's entries plus the merged-view delta. Add a perf-sanity test that measures the upsert wall time for a 100-document synthetic project (assert <50 ms per upsert).

### C.6 — Tests

- [ ] Unit tests in `packages/semantics/src/__tests__/symbol-table.test.ts` covering: empty document, document with one entity + 3 attributes (4 entries total), conflict detection, stock-vocab loading, manifest-driven namespace defaults.
- [ ] An integration test in `tests/integration/src/symbol-table.test.ts` that loads `samples/v1-metadata/` end-to-end and asserts a known set of qnames are present (e.g. `er.entity.artikl`, `er.entity.artikl.id_artiklu`, `db.dbo.QZBOZI_DF`, `db.dbo.QZBOZI_DF.IDZBOZI`).

**Acceptance for §C**: `pnpm --filter @modeler/semantics test && pnpm --filter @modeler/integration-tests test` exit 0; the `samples/v1-metadata/` integration test confirms 100+ symbols indexed with no duplicates.

---

## Section D — Reference resolver

The resolver turns a `Reference` (or any dotted id used as a reference) into a `SymbolEntry`. It runs against the project symbol table built in §C.

### D.1 — Resolver API

- [ ] In `packages/semantics/src/resolver.ts`:
  ```ts
  export type ResolutionResult =
    | { resolved: true; symbol: SymbolEntry }
    | { resolved: false; reason: 'not-found' | 'ambiguous'; tried: string[]; candidates?: SymbolEntry[] };

  export class Resolver {
    constructor(symbols: ProjectSymbolTable);
    resolveReference(ref: Reference, contextDocument: { schemaCode: string; namespace: string }): ResolutionResult;
    resolveBareId(name: string, scope: LexicalScope): ResolutionResult;
  }

  export interface LexicalScope {
    schemaCode: string;
    namespace: string;
    enclosing?: { kind: 'entity' | 'table' | 'view' | 'procedure'; qname: string };
  }
  ```

### D.2 — Dotted reference resolution

- [ ] If the ref's first part is a known schema code (`db`, `er`, `map`, `query`, `cnc`), treat the ref as fully qualified: schemaCode = first part, namespace = second part, local name = remaining parts. Look up directly in the symbol table.
- [ ] If the first part is **not** a schema code, the ref is project-qualified relative to the document's own schema and namespace: synthesize the full qname from `contextDocument.schemaCode + contextDocument.namespace + ref.parts` and look up.
- [ ] If lookup fails, return `{ resolved: false, reason: 'not-found', tried: [the qnames you attempted] }`. The `tried` list is what the diagnostic message will show to the user.

### D.3 — Bare-id resolution (lexical scope)

Used for `nameAttribute: id_artiklu` (looks up an attribute name within the enclosing entity), `roles: [fact, dimension]` (looks up bare roles in `cnc.role.*`), etc.

- [ ] If `scope.enclosing` is set, first try `<scope.enclosing.qname>.<name>` — this catches attribute-name lookups within an entity.
- [ ] Fall back to scoping by schema/namespace: try `<scope.schemaCode>.<scope.namespace>.<name>`.
- [ ] For roles specifically: also try `cnc.role.<name>` so `roles: [fact]` resolves against the stock vocabulary.

### D.4 — Tests

- [ ] Unit tests covering each of the three resolution paths.
- [ ] Integration test: load `samples/v1-metadata/`, walk every `Reference` in every AST, run the resolver, assert that every reference resolves (zero unresolved). If any sample reference is intentionally broken, list it explicitly in the test.

**Acceptance for §D**: every cross-reference in `samples/v1-metadata/` resolves; deliberately-broken refs (e.g. `er.entity.nonexistent`) produce `{ resolved: false, reason: 'not-found' }` with a meaningful `tried` list.

---

## Section E — Validator

Per-kind structural validation. The validator runs after parsing and resolution; it produces diagnostics for things the grammar can't catch.

### E.1 — Validator API

- [ ] In `packages/semantics/src/validator.ts`:
  ```ts
  export interface ValidationDiagnostic {
    code: string;                      // diagnostic code, e.g. 'ttr/required-property-missing'
    severity: 'error' | 'warning' | 'info';
    message: string;
    source: SourceLocation;
  }

  export class Validator {
    constructor(symbols: ProjectSymbolTable, resolver: Resolver, manifest: ResolvedManifest);
    validateDocument(uri: string, ast: Document): ValidationDiagnostic[];
    validateProject(): ValidationDiagnostic[];
  }
  ```
- [ ] `validateDocument` runs per-document checks (E.2, E.3, E.5). `validateProject` runs project-wide checks (E.4 duplicates).

### E.2 — Required-property checks

- [ ] For each kind, list the properties that are *de facto* required by the metadata service even though the grammar makes them optional. Start small: `EntityDef.attributes` should be non-empty; `TableDef.columns` should be non-empty; `ColumnDef.type` should be present; `AttributeDef.type` should be present. (Add more as they come up — keep a list in `docs/design/diagnostics.md`.)
- [ ] If `manifest.lint.requireDescriptions === true`, also require `description` on every top-level def.
- [ ] Emit code `ttr/required-property-missing` per missing property. Include the property name in the message.

### E.3 — Cross-reference checks

- [ ] For every `Reference` the parser surfaces (FK `from`/`to`, mappings' `entity`/`attribute`/`relation`/`fk`, `nameAttribute`, `codeAttribute`, `roles[]`), call the resolver. On unresolved, emit `ttr/unresolved-reference` with severity `error` (or `warning` if `manifest.lint.strict === false` for low-confidence cases like single-segment refs).
- [ ] For `TableDef.primaryKey` columns: ensure each named string actually exists as a column on the table. Emit `ttr/primary-key-column-not-found` if not.
- [ ] For `EntityDef.nameAttribute` / `codeAttribute`: ensure the referenced bare id resolves to an attribute on the same entity. Emit `ttr/entity-attribute-not-found`.

### E.4 — Duplicate-definition checks

- [ ] Iterate `projectSymbols.duplicates()`. For each duplicate qname, emit one `ttr/duplicate-definition` diagnostic per offending location, with the message naming the other location(s).

### E.5 — Empty-block warnings

- [ ] Emit `ttr/empty-localized-string` (severity: `warning`) when a `LocalizedString` has zero entries — the grammar permits it but ai-platform's loader warns about it (see `progress-phase-01-3-ttr-parser.md`).
- [ ] Emit `ttr/empty-search-block` (warning) when a `SearchBlock` has all sub-properties absent or empty.

### E.6 — Tests

- [ ] Unit tests in `packages/semantics/src/__tests__/validator.test.ts` covering each diagnostic code, both positive (valid → no diagnostic) and negative (invalid → expected diagnostic).
- [ ] Integration test: load `samples/v1-metadata/`, run the validator on every document, assert zero diagnostics across the bundle (the samples are known-good).

**Acceptance for §E**: every diagnostic code in this section has a positive and a negative test; `samples/v1-metadata/` produces zero validator diagnostics.

---

## Section F — Diagnostic-code expansion

Phase 1 introduced the `DiagnosticCode` enum with `ttr/parse-error` and `ttr/unknown-property`. Phase 2 expands it with the semantic-layer codes. Architecture §8.3 covers the error-handling shape.

- [ ] Update `packages/parser/src/diagnostics.ts` (or move to `packages/semantics/src/diagnostics.ts` if you prefer the codes to live next to their producers — pick one home and document it):
  ```ts
  export enum DiagnosticCode {
    // Parser-level (Phase 1)
    ParseError = 'ttr/parse-error',
    UnknownProperty = 'ttr/unknown-property',
    ParseRecoveryInfo = 'ttr/parse-recovery-info',
    // Semantic-layer (Phase 2)
    UnresolvedReference = 'ttr/unresolved-reference',
    DuplicateDefinition = 'ttr/duplicate-definition',
    RequiredPropertyMissing = 'ttr/required-property-missing',
    PrimaryKeyColumnNotFound = 'ttr/primary-key-column-not-found',
    EntityAttributeNotFound = 'ttr/entity-attribute-not-found',
    EmptyLocalizedString = 'ttr/empty-localized-string',
    EmptySearchBlock = 'ttr/empty-search-block',
  }
  ```
- [ ] Update the LSP diagnostic-mapping table to set severity per code; the default is `Error`, but `EmptyLocalizedString` and `EmptySearchBlock` are `Warning`, and `ParseRecoveryInfo` is `Information`.
- [ ] Update `docs/design/diagnostics.md` with one section per new code: trigger, severity, before/after example, fix.

**Acceptance for §F**: `docs/design/diagnostics.md` lists every code in `DiagnosticCode`; lint passes; LSP diagnostics carry the correct code, source `'modeler'`, and severity.

---

## Section G — go-to-definition (`textDocument/definition`)

- [ ] In `packages/lsp/src/server.ts`, declare server capability:
  ```ts
  capabilities: { …, definitionProvider: true }
  ```
- [ ] Implement `connection.onDefinition(async (params) => { … })`:
  - Find the document; from `params.position`, resolve which AST node (or token) the cursor sits on.
  - If on an `IdValue` or a `Reference`, run the resolver. If resolved, return `{ uri: symbol.documentUri, range: <symbol.source converted to LSP range> }`.
  - If on a definition's `name` (the symbol *is* its own definition), return its own location.
  - If on whitespace / comment / unresolvable, return `null`.
- [ ] **Helper**: `findNodeAtPosition(ast: Document, position: { line: number; character: number }): { node: AnyNode; isReference: boolean }` — walks the AST returning the most-specific node whose `source` range contains the position. Add unit tests for this helper specifically (it's the foundation for §H, §I as well).
- [ ] Tests: open `samples/v1-metadata/map.ttr`, position cursor on a `er.entity.foo` ref, call `textDocument/definition`, assert the returned location is in `er.ttr` at the entity's def line.

---

## Section H — find-references (`textDocument/references`)

The reverse direction: given a definition (or a reference), list every place in the project that refers to it.

### H.1 — Reference index

- [ ] In `packages/semantics/src/reference-index.ts`:
  ```ts
  export class ReferenceIndex {
    constructor();
    upsertDocument(uri: string, references: Array<{ targetQname: string; source: SourceLocation }>): void;
    removeDocument(uri: string): void;
    findReferences(qname: string): Array<{ uri: string; source: SourceLocation }>;
  }
  ```
- [ ] Building it: walk every AST after parsing; for each `Reference` (and id-shaped `PropertyValue` in slots that are semantically refs), call the resolver; record `{ targetQname: resolvedSymbol.qname, source: ref.source }`. Unresolved refs are skipped (find-references can't list them).
- [ ] Hold a `ReferenceIndex` instance alongside `ProjectSymbolTable` in the LSP; update both on every document change.

### H.2 — LSP method

- [ ] Capability: `referencesProvider: true`.
- [ ] `connection.onReferences(async (params) => { … })`:
  - Resolve the symbol at `params.position` (using §G's `findNodeAtPosition`).
  - Call `referenceIndex.findReferences(symbol.qname)`.
  - Convert each result to an LSP `Location[]`. If `params.context.includeDeclaration === true`, prepend the symbol's own definition location.
- [ ] Tests: open `er.ttr`, position on `def entity artikl`, call references, assert that all map-side `entity: er.entity.artikl` refs in `map.ttr` come back.

---

## Section I — hover (`textDocument/hover`)

### I.1 — Hover content formatter

- [ ] In `packages/semantics/src/hover.ts`:
  ```ts
  export function formatHover(symbol: SymbolEntry, def: Definition, manifest: ResolvedManifest): { value: string; kind: 'markdown' };
  ```
- [ ] Content shape (markdown):
  ```
  **`<qname>`** *(<kind>)*

  <description, picked per manifest.preferredLanguage if it's a localized string;
   otherwise the description value as-is>

  - **Type:** <type if applicable>
  - **Defined at:** `<basename>:<line>`
  ```
  Skip sections that don't apply.
- [ ] For localized labels (entities, attributes, roles), pick the preferred-language entry from `displayLabel`; fall back to `en`; fall back to bare name. Use the same logic ai-platform's `Localisation.kt` uses.

### I.2 — LSP method

- [ ] Capability: `hoverProvider: true`.
- [ ] `connection.onHover(async (params) => { … })`:
  - `findNodeAtPosition`; if on a definition or a resolvable reference, look up the symbol, fetch its def, format and return.
  - Return `null` if not on a meaningful node.

### I.3 — Tests

- [ ] Unit tests for `formatHover`: with a Czech-preferred manifest, an entity with `displayLabel: { cs: "Artikl", en: "Item" }` produces a hover including `**Artikl**`. Switch to `en` preferred and the same entity hovers as `**Item**`.
- [ ] Integration test: open `samples/v1-metadata/er.ttr`, hover over `def entity artikl`, assert the markdown body contains the description text.

**Acceptance for §I**: hovering any def or resolved ref in any sample file shows a useful tooltip with description, kind, and source location.

---

## Section J — workspace symbols (`workspace/symbol`)

Cmd/Ctrl-T in VS Code; project-wide fuzzy search.

- [ ] Add a fuzzy matcher. Recommendation: **`fuzzysort`** (pnpm: `pnpm --filter @modeler/lsp add fuzzysort`).
- [ ] Capability: `workspaceSymbolProvider: true`.
- [ ] `connection.onWorkspaceSymbol(async (params) => { … })`:
  - Take `params.query` (may be empty — return up to 100 symbols by qname order).
  - Build the candidates list: `projectSymbols.all()` flattened.
  - Run fuzzysort against `candidate.qname` and `candidate.name` separately; keep the best match per candidate.
  - Map each result to an LSP `SymbolInformation` with the appropriate `kind` (use `SymbolKind.Class` for entities/tables, `SymbolKind.Field` for attributes/columns, `SymbolKind.Method` for procedures/queries, `SymbolKind.Namespace` for the schema/namespace level — pick a stable mapping).
- [ ] Tests: open `samples/v1-metadata/`, query `'art'`, assert `er.entity.artikl` is in the results.

---

## Section K — Semantic tokens (carryover from Phase 1.G)

Phase 1 deferred this. Now that Phase 2's AST is complete, the implementation is straightforward.

- [ ] Capability:
  ```ts
  semanticTokensProvider: {
    legend: {
      tokenTypes: ['namespace', 'type', 'class', 'property', 'string', 'number', 'comment', 'keyword', 'variable'],
      tokenModifiers: ['declaration', 'readonly', 'deprecated'],
    },
    full: true,
  }
  ```
- [ ] `connection.onRequest('textDocument/semanticTokens/full', async (params) => { … })`:
  - Parse the document; walk the AST.
  - For each `Definition`, emit a `class` token with `declaration` modifier at the def's `name` position.
  - For each `Reference` and id-shaped `PropertyValue` in semantic-ref slots: emit `namespace` for each segment except the last, `variable` for the last.
  - Encode tokens per LSP semantic-tokens encoding (delta-encoded line/char/length/typeIndex/modifierBitmask). Use `SemanticTokensBuilder` from `vscode-languageserver` to handle the encoding.
- [ ] Tests: synthetic AST with one entity + one ref → assert encoded tokens contain the expected types and ranges.

---

## Section L — `parse-recovery-info` emission (carryover from Phase 1.F)

Phase 1 added the diagnostic code but never emits it. Add a `DefaultErrorStrategy` subclass that emits the info diagnostic at recovery boundaries.

- [ ] In `packages/parser/src/walker.ts` (or a new `packages/parser/src/recovery.ts`):
  - Subclass antlr4ng's `DefaultErrorStrategy`. Override `recover` and `recoverInline` to push a `ttr/parse-recovery-info` `ParseError` (severity `info`) at the recovery point.
  - Wire the subclass into the parser construction in `parseString`.
- [ ] Update the recovery-fixtures tests in `packages/parser/src/__tests__/recovery-fixtures.ts`: assert that each fixture that *does* produce a parse-error also produces ≥1 `parse-recovery-info` diagnostic.
- [ ] Update `progress-phase-01.md`'s F line — change `[ ]` to `[x]` with a "completed in Phase 2.L" annotation; update `docs/design/diagnostics.md` to remove the "reserved" note from `parse-recovery-info`.

---

## Section M — VS Code smoke test (carryover from Phase 1.J)

Phase 1's task list (Section J) had this; the developer rolled it back. Now that the LSP has more surface area, the smoke test can assert more useful things.

- [ ] In `packages/vscode-ext/src/__tests__/extension.smoke.test.ts`:
  - Use `@vscode/test-electron`'s `runTests` to boot a real VS Code instance.
  - Inside the test runner: open `samples/v1-metadata/er.ttr`; assert `languageId === 'ttr'`; wait for diagnostics to settle; assert zero error-severity diagnostics; modify the document to introduce `def entity {` (missing name); wait for diagnostics; assert ≥1 with code `ttr/parse-error`; revert the change; assert diagnostics clear.
- [ ] Add a `test:smoke` script to `packages/vscode-ext/package.json`.
- [ ] CI: add a smoke-test job using xvfb on Linux. Skip on Windows for now. Document the local-run command in the package's README.
- [ ] **Common pitfall**: the test electron host needs the LSP bundle to be on disk *before* the test runs. The extension's `build` script (review-001 Task 2) copies it; ensure the test runner's pre-step calls `pnpm --filter @modeler/vscode-ext build` first, or invokes Vite/esbuild build commands as part of `test:smoke`.

**Acceptance for §M**: `pnpm --filter @modeler/vscode-ext test:smoke` passes locally; the CI smoke job is green.

---

## Section N — Documentation + progress

- [ ] Throughout Phase 2, update `docs/plan/progress-phase-02.md` as work lands; one entry per section above with subsections.
- [ ] `docs/design/diagnostics.md` covers every code in §F; one section per code: trigger, severity, example, fix.
- [ ] `docs/design/architecture.md` §10 (Open questions): close Question 6 if §C.4's stock-vocab loading materially answers it; otherwise leave with a Phase-3 reference.
- [ ] `packages/semantics/README.md` — new doc covering the public API: `ProjectSymbolTable`, `Resolver`, `Validator`, `loadProject`. Include a small worked example.
- [ ] `packages/lsp/README.md` updated with the v2 LSP surface (definition/references/hover/workspace-symbols/semantic-tokens).

---

## Acceptance criteria for Phase 2 as a whole

- [ ] All sections A–N complete
- [ ] `pnpm -r build && test && lint && typecheck` green
- [ ] `pnpm --filter @modeler/integration-tests test` green
- [ ] `pnpm --filter @modeler/vscode-ext test:smoke` green
- [ ] All `samples/v1-metadata/` files produce zero parser errors and zero validator diagnostics
- [ ] `samples/broken/` fixtures still produce their expected codes (now including `ttr/parse-recovery-info` for the recoverable cases)
- [ ] Hand-verified demo path: open `samples/v1-metadata/er.ttr` in EDH; Cmd-click on a reference like `id_artiklu` → jumps to the attribute's def in `er.ttr`; right-click `def entity artikl` → "Find All References" → results include the map-side `er2db_entity` mapping; hover any def shows description + kind + source link, in Czech because `samples/v1-metadata/modeler.toml` declares `preferred = "cs"`; Cmd-T then `'artikl'` lists the entity in workspace symbols; introduce a typo in a qname → red squiggly with code `ttr/unresolved-reference` and a useful "tried: …" message; semantic tokens visibly improve highlighting on dotted refs vs Phase 1
- [ ] PR reviewed and merged

## Risks and mitigations

- **§A scope underestimate.** The walker has 17 kinds × ~5 properties each × multiple value shapes. If §A takes 1.5× the budget, that's expected. Mitigation: write the per-kind tests (A.11) early and run them continuously; avoid getting stuck on `er2dbRelation`-style edge cases by leaving them as `// TODO Phase 2.x` and proceeding.
- **Browser-mode project loading (§B.2).** `loadProject` uses `fs/promises`, which fails in the Web Worker bundle. The `loadProjectFromOpenDocuments` alternative needs the LSP server to know about all .ttr files the Designer has opened. If the Designer hasn't opened sibling files, cross-file refs won't resolve in browser mode. Document this as a v1 limitation; the Designer can be told to pre-load all known files via a custom LSP request in Phase 3.
- **TOML parser bundle size in browser.** `smol-toml` is ~5 KB minified — fine. Verify after `esbuild --bundle` that `dist/server-browser.js` doesn't bloat unexpectedly.
- **Performance on large projects.** The `samples/v1-metadata/db.ttr` file alone is ~80 KB and parses to hundreds of definitions. Symbol table builds on the full sample bundle should stay <500 ms. Add a perf assertion in the integration test.
- **Reference resolution ambiguity.** Bare-id resolution (`roles: [fact]`) can match multiple stock vocabularies in principle. The resolver returns `ResolutionResult.reason: 'ambiguous'` if so. Phase 2 emits a warning; Phase 3 may need a deterministic precedence rule.
- **Carryover regression risk (§L, §M).** The Phase 1 review surfaced silent skips of these items. Treat them as first-class scope, not afterthoughts. CI must turn red if §L's `parse-recovery-info` stops being emitted, or if §M's smoke test stops running.

## Out of scope for Phase 2 (explicitly Phase 3 or later)

- Designer schema/detail toggles, edge rendering, detail panel content (Phase 3)
- Designer LSP integration beyond what Phase 0 (post-review-Task-4) shipped (Phase 3)
- IntelliJ plugin (Phase 4)
- Productivity-tier completion (property names, schema kinds, references in completion lists) — v1.1
- Polish-tier rename, format, code actions, code lens — v1.2+
- Designer edit mode and the `WorkspaceEdit` synthesizer — v1.1
- Localized diagnostic messages (v1.x)
- Live-database integration (v1.5+)
