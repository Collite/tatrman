# Embedded SQL — contracts

**Status:** Contracts v1, 2026-06-11. Normative. The value-extraction algorithm,
grammar rules, tag registry, and conformance cases are specified in
[`../embedded-language-blocks.md`](../embedded-language-blocks.md) (**DESIGN**);
this file is the consolidated, build-facing API/DTO/schema surface. Where this
file and DESIGN agree, DESIGN is the source of the *algorithm*; this file pins
the *shapes*. If a task disagrees with either, the docs win.

## 1. Grammar surface (`packages/grammar/src/TTR.g4`)

Lexer (tagged declared **before** the untagged form — DESIGN §2.1):

```antlr
TAGGED_BLOCK_LITERAL
  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""' ;
TRIPLE_STRING_LITERAL
  : '"""' .*? '"""' ;
```

Parser (DESIGN §2.2):

```antlr
embeddedBlock
  : TAGGED_BLOCK_LITERAL
  | TRIPLE_STRING_LITERAL
  | STRING_LITERAL ;

sourceTextProperty    : SOURCE_TEXT    propSep? embeddedBlock ;
definitionSqlProperty : DEFINITION_SQL propSep? embeddedBlock ;
```

`stringLiteralForm` is unchanged. A `TAGGED_BLOCK_LITERAL` outside
`embeddedBlock` is a parse error (intended).

## 2. `TaggedBlockValue` (AST node)

### 2.1 TypeScript (`packages/parser/src/ast.ts`)

```ts
export interface TaggedBlockValue {
  kind: 'taggedBlock';
  tag: string;                 // raw tag text, e.g. 'sql' | 'ms-sql' | 'postgres'
  language: LanguageKind;      // resolved enum (SQL | TRANSFORMATION_DSL | …)
  dialect: SqlDialect | null;  // 'tsql' | 'postgres' | 'duckdb' | … ; null for non-SQL
  value: string;               // SQL only — tag/fence stripped, dedented, trailing \n removed
  tagSource: SourceLocation;   // span of the tag token text
  valueSource: SourceLocation; // span of `value` within the file (post-dedent)
  indentWidth: number;         // common indent removed by dedent (uniform; for the source map)
  source: SourceLocation;      // whole literal
}
```

`PropertyValue` (the `sourceText`/`definitionSql` value union) gains
`TaggedBlockValue`. **This is a breaking change** (DESIGN §1/§10).

### 2.2 Kotlin (`packages/kotlin/ttr-parser`, package `org.tatrman.ttr.parser`)

New sealed `PropertyValue` variant — mirrors the TS shape; carries the value
contract only, **no SQL analysis**:

```kotlin
data class TaggedBlockValue(
    val tag: String,
    val language: LanguageKind,
    val dialect: String?,        // dialect id string; null for non-SQL
    val value: String,
    val tagSource: SourceLocation,
    val valueSource: SourceLocation,
    val indentWidth: Int,
    override val source: SourceLocation,
) : PropertyValue()
```

### 2.3 Value extraction (normative — DESIGN §4)

`tag-peel` (strip `"""`, consume `tag`, consume `[ \t]*\r?\n`) → existing
`applyTextwrapDedent` (contracts.md §2.9 of the grammar-master root) → strip
**exactly one** trailing `\r?\n`. The tag never reaches `value`. TS and Kotlin
must agree byte-for-byte (conformance §6 below).

## 3. Tag registry (`@modeler/grammar`, mirrored in Kotlin)

```ts
export type SqlDialect = 'tsql' | 'postgres' | 'duckdb' | 'mysql' | 'bigquery';

export interface TagEntry {
  language: LanguageKind;
  dialect: SqlDialect | null;     // null for non-SQL languages
  grammar: 'tsql' | 'postgresql' | null;  // generated grammar that backs it
}

export const TAG_REGISTRY: Record<string, TagEntry>;
// 'sql' → { SQL, null (→ modeler.toml default), grammar by resolved default }
// 'ms-sql'|'tsql'|'mssql' → { SQL, 'tsql', 'tsql' }
// 'postgres'|'postgresql'|'pg' → { SQL, 'postgres', 'postgresql' }
// 'duckdb' → { SQL, 'duckdb', 'postgresql' }   // postgres grammar + patches
// 'transform' → { TRANSFORMATION_DSL, null, null }  (etc.)
```

Unknown tag → diagnostic on `tagSource`; block stored as raw text (DESIGN §5).
`language:` property is inferred from the tag and soft-deprecated (DESIGN §6).

## 4. `SqlRefModel` (dialect-agnostic extraction — DESIGN §12.4)

Per-dialect adapters walk their own ANTLR parse tree and emit this; the resolver
consumes **only** this.

```ts
export interface Span { offset: number; length: number; line: number; column: number; }

export interface SqlTableRef {
  name: string[];                 // raw name parts as written, e.g. ['dbo','Orders']
  alias?: string;
  span: Span;
  origin: 'base' | 'cte' | 'derived';
}
export interface SqlColumnRef {
  name: string;
  qualifier?: string;             // table or alias qualifier as written
  span: Span;
}
export interface SqlCte { name: string; span: Span; columns?: string[]; }
export interface SqlParamRef { name: string; span: Span; }  // @p / $1 / :name / ?

export interface SqlScope {        // for resolving bare columns
  tables: SqlTableRef[];           // tables/aliases visible in this scope
  parent?: SqlScope;
}

export interface SqlRefModel {
  tables: SqlTableRef[];
  columns: SqlColumnRef[];
  ctes: SqlCte[];
  params: SqlParamRef[];
  rootScope: SqlScope;
  parseErrors: { message: string; span: Span }[];  // tolerated, not fatal
}
```

Adapter contract: `extract(tree: ParseTree, dialect: SqlDialect): SqlRefModel`.
Adapters must never throw on `error` subtrees — record into `parseErrors` and
continue (DESIGN §12.3).

## 5. `modeler.toml` SQL config (`@modeler/semantics`)

Project-wide. Closes DESIGN §12.8 multi-part name mapping.

```toml
[sql]
default-dialect = "tsql"          # resolves bare `sql` tag

# Bijective (database, schema) ⇄ TTR namespace map (project-wide).
[[sql.namespace-map]]
namespace = "sales"               # TTR db.<namespace>
database  = "WH"
schema    = "dbo"

[[sql.namespace-map]]
namespace = "public_core"
database  = "core"
schema    = "public"

# Per-dialect defaults for under-qualified names.
[sql.defaults.tsql]
database = "WH"
schema   = "dbo"
[sql.defaults.postgres]
database = "core"
schema   = "public"               # search_path head
[sql.defaults.duckdb]
database = "core"
schema   = "main"
```

Config DTO:

```ts
export interface SqlConfig {
  defaultDialect: SqlDialect;
  namespaceMap: { namespace: string; database: string; schema: string }[]; // bijective
  defaults: Record<SqlDialect, { database: string; schema: string }>;
}
```

### 5.1 Name resolution algorithm (normative — DESIGN §12.8)

1. Split a SQL name by dialect part-count: T-SQL `server.database.schema.object`
   (≤4), Postgres/DuckDB `[catalog|database].schema.table` (≤3).
2. Reduce to `(database, schema, table)`, filling missing leading parts from
   `sql.defaults.<dialect>`.
3. Map `(database, schema)` → TTR `namespace` via `namespace-map`.
4. Resolve `db.<namespace>.table.<table>[.<column>]` in the TTR symbol table.
   Apply dialect identifier folding before comparison (§6.2).

## 6. Conformance & identifier rules

### 6.1 Tagged-block value conformance (TS ⇄ Kotlin)

Golden cases C1–C11 in DESIGN §9 are the fixture set. Both walkers must produce
identical `tag` / `language` / `dialect` / `value`. Lives in the existing
`@modeler/conformance` harness (TS dump vs Kotlin dump).

### 6.2 Dialect identifier folding (resolver — DESIGN §12.8)

| Dialect | Unquoted | Quoting |
|---|---|---|
| tsql | case-insensitive | `[brackets]` (and `"…"` when `QUOTED_IDENTIFIER`) |
| postgres | fold to lower-case | `"double quotes"` preserve case |
| duckdb | case-insensitive, case-preserving | `"double quotes"` |

The resolver normalises both the SQL identifier and the TTR symbol name by the
block's dialect rule before equality.

## 7. LSP additions (`@modeler/lsp`)

- **Semantic tokens.** Extend the server legend with SQL token types
  (`keyword`, `string`, `number`, `operator`, `comment`, `variable` (`@p`/`#temp`),
  `parameter`, `class`→table, `property`→column). Embedded SQL tokens are merged
  into the document's semantic-token response, positioned via the §8 source map.
- **Custom methods reuse.** No new `modeler/*` method is required for v1 (SQL
  features ride existing diagnostics/hover/definition/semantic-token responses);
  resolution targets are TTR `db` symbols, so go-to-definition returns the
  existing symbol locations.

## 8. Versioning

The tagged-block change ships in a new `org.tatrman:*` version (additive AST node
+ sealed variant → behaves as a breaking minor while < 1.0.0; DESIGN §10). SQL
analysis (lexer/parser/semantics) is TS-only and does **not** affect the Kotlin
artifacts' version.
