# Contracts — Qualified-name redesign

Normative shapes. Where this disagrees with `architecture.md`, the algorithm there
wins for *behaviour*; this file pins the *types and the manifest schema*.

## 1. `modeler.toml`

Extends the existing manifest (`docs/v1/design/architecture.md` §5). All keys
optional; missing values fall back to convention.

```toml
[project]
name    = "df-erp-metadata"
version = "0.1.0"

# Named schemas = bindings to a physical database (db / binding models only).
# The TABLE KEY (`dbo`, `sales`, `core`) is the TTR-side handle written in refs.
# `db-schema` is the actual SQL schema inside the database — kept distinct from the
# handle so the word "schema" is never re-overloaded.
[schemas.dbo]
database  = "WH"
db-schema = "dbo"
dialect   = "tsql"

[schemas.sales]
database  = "WH"
db-schema = "sales"
dialect   = "tsql"

[schemas.core]
database  = "core"
db-schema = "public"
dialect   = "postgres"

# Packages. `default-schema` lets refs under the package skip the schema slot (D8).
# ER/MD packages simply omit it.
[packages."shop.sales"]
default-schema = "sales"

[packages."shop.catalog"]
default-schema = "dbo"

[defaults]
schema = "dbo"          # project-wide fallback when a package sets none
# NOTE: no `model` default — model is always derived from kind.

[lint]
require-qualified-refs = false   # if true, flag bare cross-schema names (D10)
```

### 1.1 Relationship to embedded-SQL config

This **subsumes** `[[sql.namespace-map]]` and `[sql.defaults.<dialect>]` from
`docs/features/embedded-sql/contracts.md` §5. The mapping:

| Old (embedded-sql) | New (here) |
|---|---|
| `namespace = "sales"` | the `[schemas.sales]` table key |
| `database = "WH"` | `[schemas.sales].database` |
| `schema = "dbo"` | `[schemas.sales].db-schema` |
| `[sql.defaults.tsql]` | per-schema `dialect` + `[defaults].schema` |

The embedded-SQL name-resolution algorithm (§5.1 there: split SQL name → reduce to
`(database, schema, table)` → map to TTR handle → resolve) now maps to a **schema
handle** instead of a `namespace`, but is otherwise unchanged.

## 2. Manifest DTOs (`@modeler/semantics`)

```ts
export interface SchemaBinding {
  name: string;            // TTR handle, e.g. 'sales'  (the [schemas.<name>] key)
  database: string;
  dbSchema: string;        // the physical SQL schema
  dialect: SqlDialect;     // 'tsql' | 'postgres' | 'duckdb' | 'mysql' | 'bigquery'
}

export interface PackageConfig {
  name: string;            // 'shop.sales'
  defaultSchema?: string;  // schema handle, must exist in `schemas`
}

export interface ManifestConfig {
  project: { name?: string; version?: string };
  schemas: Record<string, SchemaBinding>;
  packages: Record<string, PackageConfig>;
  defaults: { schema?: string };          // model is never defaulted
  lint: { requireQualifiedRefs: boolean };
}
```

## 3. Qname (`@modeler/semantics`, replaces `qname.ts`)

The canonical key is uniform; `schema` and `kind` may be absent.

```ts
export interface Qname {
  package: string;         // '' for the root package
  model: ModelCode;        // 'db' | 'er' | 'md' | 'binding' | 'cnc'
  schema?: string;         // db ONLY; undefined for er/md/cnc/binding
  kind: string;            // resolved object kind (always known after resolution)
  parts: string[];         // name + sub-objects, e.g. ['Orders', 'id']
}

// 'query' is NOT a model (D14) — a `def query` resolves to model 'db'. The QUERY
// grammar token survives for `def query`; only the model-code value is removed.
export type ModelCode = 'db' | 'er' | 'md' | 'binding' | 'cnc';

// Canonical, package-first, dropping absent slots.
//   shop.sales · db · dbo · table · Orders  →  "shop.sales.db.dbo.table.Orders"
//   shop.core  · er ·    · entity · customer →  "shop.core.er.entity.customer"
export function qnameToKey(q: Qname): string;
```

## 4. Slot-filling parser/resolver

The parser no longer assumes positions; it classifies leading segments by vocabulary
and returns *partial* slots, which the resolver fills.

```ts
export interface RefSite {
  expectedKind?: string;   // from the grammar position, e.g. 'table' for `table: …`
  filePackage: string;
  headerSchema?: string;   // the file header `schema <id>`, if any
}

export interface Vocab {
  models: ReadonlySet<ModelCode>;     // fixed reserved set
  packages: ReadonlySet<string>;      // registered + path-inferred
  schemas: ReadonlySet<string>;       // registered
  kinds: ReadonlySet<string>;         // kind keywords
}

export interface PartialQname {
  model?: ModelCode;
  package?: string;
  schema?: string;
  kind?: string;
  parts: string[];         // remaining name segments (never empty)
}

// Step 1 — classify, no resolution. Pure, total: any segment run yields a PartialQname.
export function classifyReference(text: string, vocab: Vocab): PartialQname;

// Step 2 — fill the gaps per architecture.md §5. Returns the resolved symbol or a
// diagnostic (UnresolvedReference | AmbiguousReference).
export function resolveReference(
  partial: PartialQname,
  site: RefSite,
  vocab: Vocab,
  symbols: ProjectSymbolTable,
): ResolvedSymbol | ReferenceDiagnostic;
```

Fill order (normative — mirrors §5): `kind` (context → leading keyword → target),
`model` (written → kind→model → unique), `package` (written → file → root),
`schema` (written → package default → `[defaults].schema` → scoped-unique; absent for
er/md), then resolve `parts` against `(package, model, schema)`.

`modelForKind(kind)` is the single-valued map already implied by
`defaultSchemaForKind` (`semantics/src/default-schema.ts`); extract it so model and
default-schema derive from one table. Per D14, **`query` and `drillMap` now map to
`db`** (not the retired `query` model), and a db-model kind gets the schema slot
(default `dbo`). Per D15, `role`/`er2cncRole` map to `cnc`, which is schema-less.

## 5. Validation rules (`@modeler/lint` + manifest load)

| Rule | Severity | Check |
|---|---|---|
| `schema-name-collision` | error | A `[schemas.<name>]` key equals a package name, a model code, or a kind keyword (D9). |
| `unknown-package-schema` | error | A `[packages.*].default-schema` names a schema absent from `[schemas]`. |
| `schema-on-logical-model` | warning | A `schema` directive (or schema-qualified ref) targets an `er`/`md`/`cnc`/`binding` model, which has no schema slot (D6). |
| `ambiguous-reference` | error | A slot-filled reference matches more than one symbol after scoped search. |
| `require-qualified-refs` | off→warning | When enabled, a reference resolved only via cross-schema unique-match (D10). |

## 6. Conformance & parity

Normative, as in [`pkg-schema-defaults`](../pkg-schema-defaults/INDEX.md): TS and the
Kotlin twin must produce **identical** canonical-key sets and diagnostic-code sets for
the same input. Add fixtures covering: full elision (`shop.sales.Orders`), context
kind (`target: { table: X }`), er/md no-schema, explicit escape hatch
(`db.dbo.Orders`), and each collision rule. The conformance harness
(`tests/conformance/`) compares resolved-qname + symbol-qname + diagnostic-code sets.

## 7. Open contract questions

- **C1 — token name for the type directive.** `MODEL` reads best (`model db`) but
  verify no residual `MODEL` token use after `def model`→`def project`. The grammar
  currently has `MODEL : 'model'` (TTR.g4:670) used by both `objectDefinition` and
  `idPart`; the def-kind use moves to `PROJECT`, freeing the directive use.
- **C2 — backward-compat aliases.** Whether `schema`/`namespace`/`def model` survive
  as deprecated aliases for one release (Q3). Recommendation: migrator + hard cut,
  because `schema`'s meaning *changes*.
