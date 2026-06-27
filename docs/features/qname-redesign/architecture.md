# Architecture — Qualified-name redesign

## 1. Problem

A TTR address mixes three orthogonal axes, and today the syntax conflates them:

1. **Model type / layer** — `db | er | md | binding | query | cnc`. Declared by
   `schema <code>` (the directive `schemaDirective : SCHEMA schemaCode (NAMESPACE id)?`
   in `packages/grammar/src/TTR.g4`).
2. **Namespace** — for `db` a physical `(database, schema)` coordinate; for `er`/`md`
   nothing meaningful; for `query` undefined.
3. **Package** — `shop.sales`, the hierarchical grouping of files/defs.

Two concrete defects follow:

- **Overloaded "schema".** Axis 1 is *called* "schema" while the real SQL schema
  (`dbo`) lives in axis 2 (`namespace`). `schema er namespace dbo` reads backwards.
- **Non-uniform slots.** The symbol-table keys are not the same shape across models:

  | Model | Example key | Slot after model holds |
  |---|---|---|
  | db | `db.dbo.QZBOZI_DF` | a **namespace** (`dbo`) |
  | er | `er.entity.artikl.id_artiklu` | the **kind** (`entity`) |

  `semantics/src/reference-index.ts:20` hides this with
  `namespace || defaultNamespaceForSchema(schema) || def.kind`; the comment in
  `semantics/src/default-schema.ts` admits "er already coincides with its entity
  namespace." So "namespace is meaningless for er/md" is really *the er
  namespace slot is echoing the kind*.

## 2. Keyword renames (D1–D3)

| Was | Becomes | Meaning |
|---|---|---|
| `def model erp_v1 { … }` | `def project erp_v1 { … }` | the whole artifact: identity, `version`, `description`, `tags`. One per repo. |
| `schema db` (directive) | `model db` | the model type / layer. |
| `namespace dbo` | `schema dbo` | the namespace = a binding to a physical database (db/binding only). |

`model` is now free to mean the type because the artifact header it used to collide
with is `project`. Each word names exactly one concept:

```
def project erp_v1 { version: "1.0.0" }   # the artifact
model db                                   # its type/layer  (usually omitted — see §5)
schema dbo                                 # its binding to a physical database
```

## 3. The key idea: canonical key vs surface reference

Stop using one string for two jobs.

- **Canonical key** — internal, uniform, fully-qualified. Every slot present. The
  resolver and symbol table speak this. Users never type it.
- **Surface reference** — what authors write. Short; almost every slot elided.

Canonical key, uniform and **package-first** (D4):

```
<package> . <model> . <schema?> . <kind> . <name>(.<sub>)*
```

| Surface | Canonical key |
|---|---|
| `shop.sales.Orders` (in a db context) | `shop.sales · db · dbo · table · Orders` |
| `shop.core.customer` (er) | `shop.core · er · — · entity · customer` |
| `shop.fin.time.month` (md) | `shop.fin · md · — · dimension · time · month` |

`schema` is a **db-only** slot (D6): a logical `er`/`md`/`cnc` model (and `binding`)
has no physical database to bind to, so the slot is simply absent — not
empty-string-papered. The db-vs-er asymmetry of §1 disappears because the *key* is
uniform while the *surface* is free to be short.

**Two consequences (D14, D15):**

- **Queries are db-schema objects.** There is no separate `query` model; a `def query`
  is a `db` object that lives in a schema (default `dbo`) just like a table, so the
  tables it references resolve there. `modelCode` set = `{db, er, md, binding, cnc}`.
  Address: `db.dbo.<query>` (kind `query` derived).
- **`cnc` is schema-less, with no namespace echo.** `cnc` joins `er`/`md`:
  `cnc.role.<X>` (model·kind·name), not the old `cnc.cnc.role.<X>`.

## 4. Slot model and vocabulary classification

A surface reference is a dotted run of segments. Each **leading** segment is
classified by which registered vocabulary it belongs to — not by position — which is
sound because of the no-collision rule (D9):

| Class | Source of truth |
|---|---|
| **model** | reserved set `{db, er, md, binding, query, cnc}` |
| **package** | registered in `modeler.toml` (+ path inference); longest dotted match |
| **schema** | registered in `modeler.toml` |
| **kind** | a kind keyword (`table`, `entity`, …) — rarely written |
| **name** | everything else (the object + sub-objects) |

Because schema names cannot equal a package name, a model code, or a kind keyword,
every leading segment has exactly one possible class. Input order therefore barely
matters for *parsing*; it matters only for the canonical key and readability, where
we standardise on package-first.

## 5. Resolution — slot filling

For a reference at a site with context `(expectedKind?)`, inside a file with
`(filePackage, headerSchema?)`:

1. **model** — consume `seg[0]` if it is a reserved code; else **derive from kind**
   (kind→model is single-valued, already encoded by `defaultSchemaForKind` in
   `semantics/src/default-schema.ts`); else leave for unique-match. *A file header
   never needs `model`* — every def's kind determines its model (D5).
2. **package** — consume the longest registered dotted prefix; else `filePackage`;
   else root (empty package — see [`default-package-name`](../default-package-name/INDEX.md)).
3. **schema** — consume a registered schema token if present; else the package's
   `default-schema` (D8); else `[defaults].schema`; else **scoped unique-match**
   (D10). ER/MD: slot absent, skip.
4. **kind** — from the grammar position (`target: { table: X }` ⇒ `table`); else a
   leading kind keyword in the reference; else inferred from the resolved target.
5. **name path** — resolve against `(package, model, schema)`. More than one match →
   `AmbiguousReference` diagnostic; `[lint] require-qualified-refs` upgrades the
   convenience-resolution cases to warnings.

### Worked examples

```ttr
Orders                       # db file, pkg shop.sales, header `schema dbo`
                             #   kind=table (from `def`/context) → model db
                             #   → shop.sales.db.dbo.table.Orders
shop.sales.Orders            # pkg explicit; model+schema+kind filled
target: { table: Orders }    # kind=table from property ⇒ db; schema from default
shop.core.customer           # er: pkg explicit; model=er (kind/unique); NO schema slot
db.dbo.Orders                # fully explicit — the escape hatch for ambiguity
db.dbo.SalesByMonth          # a query: db kind=query, schema dbo (was db.query.*)
fin.cnc.role.dimension       # cnc: model·kind·name, NO schema (was cnc.cnc.role.*)
```

### Unique-match, scoped (D10)

Unique-match is *spooky action at a distance*: a bare `Orders` that resolves today
silently breaks when a second `Orders` appears in another schema, and the error
surfaces in a different file than the edit that caused it. Two guards:

- **Scope it.** Resolve within the current package/schema first; widen to
  project-global only if still unresolved. Narrow blast radius.
- **Make it lintable.** Resolve via unique-match, but `[lint] require-qualified-refs`
  flags bare cross-schema names so teams that want stability opt into explicit
  qualification. Resolution stays convenient; safety is a toggle.

## 6. The file header after the redesign

`model` in a header is redundant (every def's kind determines its model), so a header
reduces to an optional `schema`:

```ttr
package shop.sales
schema dbo            # default db schema for this file's db defs and unqualified refs
def table Orders { … }
```

ER/MD files carry no `schema` line at all (D6); they may carry only `package`. If a
file mixes models (Q2), the header `schema` applies to its db defs and er/md defs
ignore it — consistent with today's per-kind behaviour.

## 7. Components touched

| Component | Change |
|---|---|
| `@modeler/grammar` (`TTR.g4`) | rename tokens `SCHEMA`→`MODEL`?, `NAMESPACE`→`SCHEMA`; `def model`→`def project`; regenerate all three parsers. **Breaking** (Q1). |
| `@modeler/parser` | generated parser regen; AST node renames (`SchemaDirective`→`ModelDirective`, etc.). |
| `@modeler/semantics` | rewrite `qname.ts` (slot model), `default-schema.ts` (model-from-kind unchanged; namespace default → package/schema binding), `reference-index.ts` (drop `namespace || def.kind`), resolver slot-fill + scoped unique-match. |
| `@modeler/semantics` (manifest) | parse named schemas + package default-schema; merge embedded-SQL `namespace-map` into it. |
| `@modeler/lint` | `require-qualified-refs` rule; no-collision validation (schema vs package/model/kind). |
| `@modeler/migrate` | rewrite `schema`→`model`, `namespace`→`schema`, `def model`→`def project`. |
| Kotlin twin + conformance | mirror the rename + slot model; keep qname/diagnostic sets byte-identical (normative). |
| `@modeler/vscode-ext` | regenerate the TextMate grammar (keyword set changed). |

## 7.1 Where the slot-filling resolver + manifest defaults live (implementation note)

The slot-filling engine (`classifyReference` + `resolveReference`, §5) is the
**authoritative canonical resolver in the TS layer** (`@modeler/semantics`). The
production `Resolver` keeps its reachability steps (lexical, same-package, named/
wildcard imports, auto-imported cnc roles) — those are orthogonal to slot-filling —
and delegates the **canonical / qualified** resolution to `resolveReference`, fed by
a `Vocab` built from the manifest (registered `[schemas.*]` handles + `[packages.*]`
names, D9) and a `RefSite` carrying the manifest schema defaults. This is what makes
**D8** (`[packages.*].default-schema`) and **`[defaults].schema`** functional:

- `effectiveSchemaId(fileSchema, package, manifest)` fills a directive-less db
  file's schema slot from the package default → project default, so symbols are
  **keyed** under the right schema (the LSP and lint loaders call it before
  `upsertDocument`).
- the `Resolver` reads the same defaults (via a `RefSite`) for **scoped
  unique-match** (D10): a name ambiguous project-wide but unique within the file's
  package resolves; an otherwise-ambiguous name surfaces `AmbiguousReference`.

**The Kotlin and Python twins intentionally carry no manifest layer.** Their
resolvers take only the symbol table; `ai-platform` consumes the *published*
resolver and sources a schema from the file directives and the query request — it
never reads `modeler.toml`. The conformance harness is manifest-free in all three
languages, so the slot defaults change no cross-language output and there is nothing
to mirror: porting a manifest subsystem into the published artifacts would add code
no consumer exercises. The one cross-language invariant — the single kind→model map
(`modelForKind`, D14/D15) — **is** mirrored (`Kinds.kt`, `default_schema.py`), and
`defaultSchemaForKind` is now a deprecated alias of it in all three.

## 8. Migration

The rename changes the *meaning* of `schema`, so a silent alias is dangerous. Plan:
ship a `@modeler/migrate` codemod that rewrites the three keyword changes and lifts
`namespace = { db = "dbo", … }` from `[schemas]` into named `[schemas.*]` bindings.
Cut a new major grammar version (Q1). Decide on a deprecation window for the old
keywords vs a hard cut (Q3).
