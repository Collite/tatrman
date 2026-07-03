# MD Model — Contracts

**Status:** v1, 2026-06-25. Companion to [`design.md`](design.md),
[`grammar-md-changes.md`](grammar-md-changes.md), [`map-catalog.md`](map-catalog.md), and the
phased plan under [`plan/implementation-plan.md`](plan/implementation-plan.md).

**Audience:** the implementer (junior dev or coding agent) executing the MD feature. The contracts
here are the single source of truth — every property, every AST field, every diagnostic code,
every catalog signature. If a task-list snippet conflicts with this document, **this document
wins**; amend by PR against this file.

**Scope (this pass):** the full v1 — the seven logical objects (Layer A) **and** the binding layer
(`md2db_*`, `md2er_cubelet`). The operations DSL (Layer B), MOLAP, query-backed cubelets, custom
calc, and non-additive recompute are out (design §2, §11).

> **Implementer note — verifying library APIs.** Where a task references an external library, run
> the `context7` MCP server (`mcp__context7__resolve-library-id` → `mcp__context7__query-docs`)
> before trusting any snippet. Our contracts are fixed; upstream APIs drift.

## Index

1. [Grammar surface](#1-grammar-surface)
2. [AST additions](#2-ast-additions)
3. [Logical object property tables](#3-logical-object-property-tables)
4. [Binding object property tables](#4-binding-object-property-tables)
5. [Symbol table & namespaces](#5-symbol-table--namespaces)
6. [Semantic algorithms](#6-semantic-algorithms)
7. [Diagnostic codes](#7-diagnostic-codes)
8. [`@modeler/md-catalog` package](#8-modelermd-catalog-package)
9. [LSP impact](#9-lsp-impact)
10. [Changelog](#10-changelog)

---

## 1. Grammar surface

Owned by `packages/grammar/src/TTR.g4`. Version `3.0 → 3.1` (**additive**). The full token list,
`objectDefinition` additions, and body productions are in
[`grammar-md-changes.md`](grammar-md-changes.md). Summary of what the parser now accepts:

- New schema code `md` (`schema md`).
- New logical `def` kinds: `domain`, `dimension`, `map`, `hierarchy`, `measure`, `cubelet`.
- New binding `def` kinds (under `schema binding`): `md2db_cubelet`, `md2db_domain`, `md2db_map`,
  `md2er_cubelet`.
- One new punctuation token `DOTDOT` (`..`) for range literals.
- All new keywords added to `idPart` (cross-references stay valid).

The parser stays **mechanical**: it builds the permissive superset and never rejects on
schema/property *validity* — those are §6/§7.

---

## 2. AST additions

Owned by `packages/parser/src/ast.ts`. Every node carries `source: SourceLocation` (ANTLR-style,
per the v1 invariant). Cross-references are **opaque strings** at the parser layer (resolved in
semantics). Names mirror the existing `*Def` node conventions.

```ts
// ---- Logical ----
export interface MdDomainDef {
  kind: 'mdDomain';
  name: string;
  type?: DataType;                       // reuses existing DataType
  domainKind?: 'calc' | 'bound';         // grammar accepts any id; validated in semantics
  restrict?: RestrictClause[];
  description?: string; tags?: string[];
  source: SourceLocation;
}
export interface RestrictClause {
  clause: string;                        // 'range' | 'members' | 'pattern' | 'length' | <open>
  value: RangeLiteral | DomainMember[] | LiteralValue;
  source: SourceLocation;
}
export interface RangeLiteral { lo: number; hi: number; source: SourceLocation; }
export interface DomainMember { key: string; labels: LocalizedString; source: SourceLocation; }

export interface DimensionDef {
  kind: 'dimension';
  name: string;
  key?: string;                          // attribute name; required-in-semantics
  attributes: AttributeDef[];            // inline defs (shared AttributeDef node)
  hierarchies?: string[];                // references to HierarchyDef names
  description?: string; tags?: string[];
  source: SourceLocation;
}

// AttributeDef is the EXISTING node, extended (optional fields; per-schema validation):
export interface AttributeDef {
  kind: 'attribute';
  name: string;
  type?: DataType;                       // ER
  domainRef?: string;                    // MD — `domain:`  (opaque ref)
  aggregation?: AggregationSpec;         // MD — attribute roll-up agg in a hierarchy
  isKey?: boolean; optional?: boolean;
  valueLabels?: ValueLabels; /* … existing ER fields … */
  source: SourceLocation;
}

export interface MdMapDef {
  kind: 'mdMap';
  name: string;
  from: string[];                        // 1..n opaque domain refs
  to: string[];                          // 1..n opaque domain refs (M usually 1)
  cardinality?: '1:1' | 'N:1';           // grammar object_; normalised in walker
  calc?: CalcRef;                        // absent ⇒ table-backed
  description?: string; tags?: string[];
  source: SourceLocation;
}
export interface CalcRef { name: string; args: CalcArg[]; source: SourceLocation; }
export interface CalcArg { name: string; value: LiteralValue; source: SourceLocation; }

export interface HierarchyDef {
  kind: 'hierarchy';
  name: string;
  dimensionRef?: string;                 // opaque
  levels: HierarchyLevel[];              // leaf→root order PRESERVED
  description?: string; tags?: string[];
  source: SourceLocation;
}
export interface HierarchyLevel { attribute: string; via?: string; source: SourceLocation; }

export interface MeasureDef {
  kind: 'measure';
  name: string;
  domainRef?: string;                    // opaque
  measureClass?: 'additive' | 'semiAdditive' | 'nonAdditive';
  aggregation?: AggregationSpec;
  validBy?: string;                      // attribute name
  description?: string; tags?: string[];
  source: SourceLocation;
}
export interface AggregationSpec {
  default?: string;                      // 'sum' | 'max' | 'latestValid' | …
  perDimension?: Record<string, string>;// { time: 'latestValid' }
  source: SourceLocation;
}

export interface CubeletDef {
  kind: 'cubelet';
  name: string;
  grain: string[];                       // dotted `Dimension.attribute` opaque refs
  measures: (string | MeasureDef)[];     // refs or inline defs
  description?: string; tags?: string[];
  source: SourceLocation;
}

// ---- Binding (schema binding) ----
export interface Md2DbCubeletDef {
  kind: 'md2dbCubelet';
  name: string;
  cubeletRef: string;
  table: string;                         // target table ref
  shape: ShapeSpec;
  attributes: Record<string, AttrColumnBinding>;
  measures: Record<string, MeasureColumnBinding>;
  journaling?: JournalingSpec;
  source: SourceLocation;
}
export type ShapeSpec =
  | { shape: 'wide' }
  | { shape: 'long'; codeColumn: string; valueColumn: string };
export type AttrColumnBinding =
  | { column: string }
  | { via: string; from: { table: string; column: string } };   // map-mediated (design §6.1)
export type MeasureColumnBinding = { column: string } | { code: string };  // wide | long
export type JournalingSpec =
  | { mode: 'overwrite' } | { mode: 'diff' }
  | { mode: 'invalidate'; validColumn: string };

export interface Md2DbDomainDef {
  kind: 'md2dbDomain'; name: string;
  domainRef: string; source_: { table: string; column: string };
  source: SourceLocation;
}
export interface Md2DbMapDef {
  kind: 'md2dbMap'; name: string;
  mapRef: string; table: string;
  columns: Record<string, string>;       // from/to domain → case-table column
  source: SourceLocation;
}
export interface Md2ErCubeletDef {
  kind: 'md2erCubelet'; name: string;
  cubeletRef: string; entity: string;    // target ER entity
  attributes: Record<string, string>;    // attribute → ER attribute (structural only)
  source: SourceLocation;
}
```

> `DataType`, `LocalizedString`, `ValueLabels`, `LiteralValue`, `SourceLocation` are the existing
> types — reused, not redefined.

---

## 3. Logical object property tables

Property = grammar property; **Req** = required (semantics); **Schema** = the only schema it's
legal in. "opaque ref" = a cross-reference resolved in semantics, not the parser.

### 3.1 `def domain` (schema md)

| Property | Type | Req | Notes |
|---|---|---|---|
| `type` | DataType | yes | reuses TTR type system |
| `kind` | `calc` \| `bound` | no | member-set domains only; absent for scalars; authoritative when present |
| `restrict` | restrict-block | no | AND of clauses; `range` (`lo..hi`), `members`, `pattern`, `length`, … (open set) |
| `description`, `tags` | … | no | standard |

Rules: `kind: bound` ⇒ an `md2db_domain` source must exist (`md/bound-domain-no-source`). `members`
clause only on a domain whose `type` is discrete. Scalar domains (no member-set) must not carry
`kind` (`md/kind-on-scalar`).

### 3.2 `def dimension` (schema md)

| Property | Type | Req | Notes |
|---|---|---|---|
| `key` | attribute name | yes | exactly one; member identity |
| `attributes` | inline `def attribute` list | yes | ≥1; shared body §3.3 |
| `hierarchies` | list of hierarchy names | no | each must reference a `def hierarchy` whose `dimension:` is this one |

Rules: `key` must name an attribute in this dimension (`md/dim-key-unknown`); exactly one `key`
(grammar allows one property; duplicate ⇒ `md/dim-multiple-keys` if surfaced via aliasing).

### 3.3 `def attribute` (shared er + md)

| Property | Type | Req | Schema | Notes |
|---|---|---|---|---|
| `domain` | opaque domain ref | yes (md) | md | attribute ranges over this domain |
| `aggregation` | agg-spec | no | md | roll-up agg in a hierarchy (e.g. latest-valid address) |
| `type` | DataType | yes (er) | er | ER attributes keep type |
| `valueLabels`, `displayLabel`, `binding`, … | … | no | er | existing ER props |
| `isKey`, `optional` | bool | no | both | |

Rules: md attribute **must** have `domain:` and **must not** have `type:`
(`md/attr-needs-domain`, `md/attr-type-in-md`); er attribute the reverse
(`er/attr-domain-in-er`). Leaf-ness is **emergent** — never declared (§6.1).

### 3.4 `def map` (schema md)

| Property | Type | Req | Notes |
|---|---|---|---|
| `from` | domain ref \| list of refs | yes | input domain(s) |
| `to` | domain ref \| list of refs | yes | output domain(s); usually one |
| `calc` | catalog ref (+args) | no | absent ⇒ table-backed (needs `md2db_map`) |
| `cardinality` | `1:1` \| `N:1` | no | default `N:1`; a function in the forward direction |

Rules: `calc:` must resolve in the catalog with arg/type checks (§6.4); a `calc:` map is implicitly
`N:1` (explicit `1:1` ⇒ `md/calc-cardinality-conflict`). A table-backed map with no `md2db_map`
binding ⇒ `md/table-map-no-binding` (warning in model-only files; error once a binding context
exists).

### 3.5 `def hierarchy` (schema md)

| Property | Type | Req | Notes |
|---|---|---|---|
| `dimension` | dimension ref | yes | the axis this hierarchy belongs to |
| `levels` | ordered attribute list | yes | **leaf→root**; each entry may add `via <mapRef>` |

Rules: each level attribute must belong to `dimension` (`md/level-not-in-dim`); consecutive levels
must be connected by exactly one N:1 map (inferred) or a `via:` (§6.3); ambiguity ⇒
`md/ambiguous-hierarchy-step`; no connecting map ⇒ `md/no-hierarchy-step`.

### 3.6 `def measure` (schema md, or inline in cubelet)

| Property | Type | Req | Notes |
|---|---|---|---|
| `domain` | scalar domain ref | yes | the measure's value type |
| `class` | `additive`\|`semiAdditive`\|`nonAdditive` | no | default `additive` |
| `aggregation` | `sum` \| agg-spec object | no | required-shape depends on `class` (§6.5) |
| `validBy` | attribute name | cond. | required when a `latestValid`-style per-dim override is used |

### 3.7 `def cubelet` (schema md)

| Property | Type | Req | Notes |
|---|---|---|---|
| `grain` | list of `Dimension.attribute` | yes | the grain; each ref must resolve |
| `measures` | list of measure refs \| inline defs | yes | ≥1 |

Rules: every `grain` ref resolves to a real attribute (`md/grain-ref-unknown`); grain attributes
should be leaves or explicitly coarser (advisory `md/grain-not-leaf`).

---

## 4. Binding object property tables (schema binding)

### 4.1 `def md2db_cubelet`

| Property | Type | Req | Notes |
|---|---|---|---|
| `cubelet` | cubelet ref | yes | the logical cubelet bound |
| `table` | table ref | yes | target fact table |
| `shape` | `wide` \| `{ long: { codeColumn, valueColumn } }` | yes | fact-table shape |
| `attributes` | map `Dim.attr → column \| {via,from}` | yes | grain columns; map-mediated form allowed |
| `measures` | map `measure → column` (wide) \| `{ code }` (long) | yes | measure columns/codes |
| `journaling` | `overwrite` \| `diff` \| `{ invalidate: { validColumn } }` | no | writeback policy; default read-only |

Rules: the bound `attributes` keys must cover the cubelet's `grain`; **multi-source** = several
defs same `cubelet:` whose attribute-bindings union must agree on grain
(`md/multisource-grain-mismatch`). `measures` value form must match `shape`
(`md/shape-measure-mismatch`).

### 4.2 `def md2db_domain`

| Property | Type | Req | Notes |
|---|---|---|---|
| `domain` | domain ref (`kind: bound`) | yes | which bound domain |
| `source` | `{ table, column }` | yes | where members come from |

Rule: `domain` must be `kind: bound` (`md/source-on-unbound-domain`).

### 4.3 `def md2db_map`

| Property | Type | Req | Notes |
|---|---|---|---|
| `map` | map ref (table-backed) | yes | which `def map` |
| `table` | case-table ref | yes | the relation holding the cases |
| `columns` | map `domain → column` | yes | one column per from/to domain, keyed by cardinality |

Rule: `map` must be table-backed, i.e. have no `calc:` (`md/binding-on-calc-map`); `columns` must
cover every `from`/`to` domain (`md/map-columns-incomplete`).

### 4.4 `def md2er_cubelet` (structural-only)

| Property | Type | Req | Notes |
|---|---|---|---|
| `cubelet` | cubelet ref | yes | |
| `entity` | ER entity ref | yes | dimension/grain → entity |
| `attributes` | map `Dim.attr → ER attribute` | yes | structural mapping only |

Rule: **no** `shape` / `journaling` / `measures` here (design §6.5) — a pure md→er cubelet is
read-oriented; the ER→DB chain completes the physical picture. Presence of those props ⇒
`md/md2er-physical-prop`.

---

## 5. Symbol table & namespaces

Owned by `@modeler/semantics`. MD adds new symbol namespaces parallel to the existing ones. Each
`def` registers a resolvable symbol keyed by `(schema, kind, qualifiedName)`:

| Namespace | Populated by | Referenced by |
|---|---|---|
| `md.domain` | `def domain` | attribute `domain:`, measure `domain:`, map `from`/`to`, `md2db_domain` |
| `md.dimension` | `def dimension` | hierarchy `dimension:`, cubelet grain prefix |
| `md.attribute` | inline `def attribute` in a dimension | hierarchy `levels`, cubelet `grain` (dotted `Dim.attr`) |
| `md.map` | `def map` | hierarchy `via:`, `md2db_map` |
| `md.hierarchy` | `def hierarchy` | dimension `hierarchies:` |
| `md.measure` | `def measure` (+inline) | cubelet `measures` |
| `md.cubelet` | `def cubelet` | `md2db_cubelet`, `md2er_cubelet` |
| `binding.md2db_*`, `binding.md2er_*` | binding defs | — (terminal) |

Attributes are **dimension-qualified** (`Customer.code`); the resolver accepts both the dotted form
in cubelet grain/hierarchy levels and the bare form within the owning dimension. The stock calc
catalog (§8) is pre-loaded as a read-only symbol source for `calc:` resolution, exactly as the
stock CNC vocab is pre-loaded today.

---

## 6. Semantic algorithms

The non-trivial computations the validator performs. All are **semantic**, none in the parser.

### 6.1 Leaf & grain lattice

- Build the directed graph of **N:1 maps** over attributes (an attribute map is the map between the
  attributes' domains; "map A to B" sugar is resolved here).
- An attribute is a **leaf** iff **no N:1 map targets it**. (1:1 maps do **not** demote leaf-ness —
  they connect co-leaves.)
- The grain partial order is the transitive closure of N:1 edges. A cubelet's grain must be an
  antichain-ish set of resolvable attributes; flag a grain attribute that is strictly coarser than
  another in the same grain (advisory).

### 6.2 1:1 co-leaf classes

- 1:1 maps partition leaves into **co-leaf classes** (e.g. `code` ↔ `id`). Exactly one attribute
  per dimension is the `key`; others in its co-leaf class are aliases. Two attributes may share a
  domain without a 1:1 map (independent), so co-leaf-ness comes from maps, not shared domains.

### 6.3 Hierarchy step inference

For each consecutive `(lower, upper)` level pair (leaf→root order):

1. If the level carries `via: <mapRef>`, use it (must be an N:1 map `lower.domain → upper.domain`).
2. Else find the **unique** N:1 map connecting `lower.domain → upper.domain` (directly, or a
   catalog calc map). Unique ⇒ use it. Zero ⇒ `md/no-hierarchy-step`. More than one ⇒
   `md/ambiguous-hierarchy-step` (author must add `via:`).

### 6.4 Calc-map validation (catalog)

For a map with `calc: <name>(args)`:

1. `name` ∈ catalog else `md/unknown-calc-map`.
2. Each arg name ∈ entry params, required params present, values in range/enum else
   `md/bad-calc-args`.
3. `from` (single domain) `type` satisfies `entry.input`; `to` `type` (+range if entry constrains)
   satisfies `entry.output` else `md/calc-type-mismatch`.
4. Implicit `N:1`; explicit `1:1` ⇒ `md/calc-cardinality-conflict`.

### 6.5 Additivity consistency

- `class` default `additive`. `additive` ⇒ `aggregation` is a single function (default `sum`).
- `semiAdditive` ⇒ `aggregation` is an object `{ default, <dim>: <fn> }`; a `latestValid`-style
  override **requires** `validBy` (`md/semiadditive-no-validby`).
- `nonAdditive` ⇒ v1 **marks only**; any blind-sum request downstream is refused by tooling. A
  recompute formula is rejected here (`md/nonadditive-recompute-unsupported`) — that's v1.1.

### 6.6 Binding completeness

- A cubelet bound for **writeback** (any `journaling`) must bind every measure to a writable
  column and every grain attribute to a column (directly or via map).
- **Multi-source**: union of all `md2db_cubelet` defs for one cubelet must cover the grain and not
  conflict on a measure binding.
- `md→er` is structural-only; writeback requires the physical shape downstream.

---

## 7. Diagnostic codes

All MD codes are namespaced `md/*` (binding codes too, since they're MD-binding-specific). Severity
is the v1 default; the linter may downgrade. This list is the **canonical set** — add by PR.

| Code | Severity | Meaning |
|---|---|---|
| `md/unknown-schema-def` | error | an `md` def appears outside `schema md` (or binding def outside `schema binding`) |
| `md/attr-needs-domain` | error | md attribute lacks `domain:` |
| `md/attr-type-in-md` | error | md attribute carries ER-only `type:` |
| `er/attr-domain-in-er` | error | er attribute carries MD-only `domain:` |
| `md/unknown-ref` | error | a `domain`/`map`/`measure`/… cross-reference doesn't resolve |
| `md/dim-key-unknown` | error | dimension `key` names no attribute in the dimension |
| `md/dim-multiple-keys` | error | more than one `key` declared |
| `md/kind-on-scalar` | error | `kind:` on a scalar (no member-set) domain |
| `md/bad-restrict-value` | error | restrict clause value has the wrong shape |
| `md/unknown-restrict-clause` | warning | unrecognised restrict clause name |
| `md/bound-domain-no-source` | error | `kind: bound` domain with no `md2db_domain` source |
| `md/unknown-calc-map` | error | `calc:` references a name not in the catalog |
| `md/bad-calc-args` | error | calc arg unknown / missing / out of range |
| `md/calc-type-mismatch` | error | map `from`/`to` types don't satisfy the catalog signature |
| `md/calc-cardinality-conflict` | error | explicit `1:1` on a calc (N:1) map |
| `md/table-map-no-binding` | warning/error | table-backed map with no `md2db_map` |
| `md/no-hierarchy-step` | error | no map connects two consecutive levels |
| `md/ambiguous-hierarchy-step` | error | >1 map connects two levels and no `via:` |
| `md/level-not-in-dim` | error | a hierarchy level attribute is not in its dimension |
| `md/semiadditive-no-validby` | error | semi-additive latest-valid agg without `validBy` |
| `md/nonadditive-recompute-unsupported` | error | recompute formula on a non-additive measure (v1.1) |
| `md/grain-ref-unknown` | error | a cubelet grain ref doesn't resolve |
| `md/grain-not-leaf` | warning | a grain attribute is strictly coarser than another in the grain |
| `md/shape-measure-mismatch` | error | measure binding form doesn't match `shape` |
| `md/multisource-grain-mismatch` | error | multi-source cubelet bindings disagree on grain |
| `md/source-on-unbound-domain` | error | `md2db_domain` targets a non-`bound` domain |
| `md/binding-on-calc-map` | error | `md2db_map` targets a calc (non-table) map |
| `md/map-columns-incomplete` | error | `md2db_map` columns don't cover all from/to domains |
| `md/md2er-physical-prop` | error | `md2er_cubelet` carries shape/journaling/measures |
| `md/incomplete-journaling` | error | an `invalidate` journaling with no `validColumn`, or a writeback (journaling-bearing) cubelet binding leaving a measure unbound |

---

## 8. `@modeler/md-catalog` package

**Decision (2026-06-25):** a **new workspace package** `@modeler/md-catalog`, mirroring
`@modeler/grammar` — small, data-only, vendored to ai-platform. It is the physical home of the
calc-map catalog (the signatures in [`map-catalog.md`](map-catalog.md)).

### 8.1 Package layout

```
packages/md-catalog/
  package.json          # name @modeler/md-catalog, version = catalog semver
  src/
    catalog.ts          # MD_CALC_CATALOG (the Time entries), typed
    types.ts            # CatalogEntry / CatalogParam / CatalogShape
    index.ts            # re-exports + MD_CATALOG_VERSION
  src/__tests__/catalog.test.ts
```

### 8.2 Exported types & data

(see [`map-catalog.md`](map-catalog.md) §4 for the `CatalogEntry` shape — that file and this section
are kept in lockstep). Key invariants:

- `MD_CALC_CATALOG: ReadonlyMap<string, CatalogEntry>` — keyed by `entry.name`.
- `MD_CATALOG_VERSION: string` — semver; **bump rules**: adding an entry or an optional param =
  minor; changing an entry's signature/semantics = major; doc/typo = patch.
- The catalog contains **no SQL** — abstract semantics only. ai-platform owns lowerings keyed by
  `(name, dialect)` and pins the catalog version it supports.

### 8.3 Consumption

- `@modeler/semantics` depends on `@modeler/md-catalog` (`workspace:*`) and pre-loads
  `MD_CALC_CATALOG` into the symbol source for `calc:` resolution + type-check (§6.4). This mirrors
  how the stock CNC vocab is pre-loaded.
- **Cross-repo sync**: ai-platform vendors the package (like `@modeler/grammar`); the version is the
  sync key. A model referencing an entry newer than the runtime's pinned catalog version is a
  deploy-time mismatch ai-platform reports — not a modeler diagnostic.
- Dependency-graph placement: `md-catalog` sits beside `grammar` as a leaf with no runtime deps;
  `semantics → md-catalog`. Update the graph in `CLAUDE.md` when the package lands.

---

## 9. LSP impact

v1 MD is a **language** increment: parser + semantics + diagnostics flow through the existing LSP
methods (`textDocument/publishDiagnostics`, hover, definition, completion) with no new custom
method. Specifically:

- **Diagnostics** — the `md/*` codes (§7) publish through the standard channel.
- **Hover / go-to-definition / completion** — work once the new symbols (§5) are indexed; calc-map
  completion lists catalog entries; domain/measure/attribute refs resolve via the symbol table.
- **`modeler/getModelGraph`** — the Designer's read-only graph method is **not** extended for MD in
  v1 (Designer MD rendering is a later phase, mirroring how `db`/`er` rendering preceded edit mode).
  No `applyGraphEdit` work here.
- **TextMate grammar** — regenerate (`packages/vscode-ext/scripts/generate-tm-grammar.ts`) so the
  new keywords highlight.

No per-host language logic (the "one LSP across hosts" invariant). All MD understanding lands in
`parser` / `semantics` / `md-catalog`.

---

## 10. Changelog

- **v1 (2026-06-25)** — initial MD contracts: 7 logical objects + binding layer, AST shapes,
  `md/*` diagnostic set, `@modeler/md-catalog` package contract. Pairs with grammar 3.1 and the
  Time calc-map catalog v1.
