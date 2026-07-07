# Multidimensional Model — Design

Status: **proposal** · Owner: editor-tooling · Companion to [`md model brief.md`](md%20model%20brief.md)

This document designs the **multidimensional (MD) model** for TTR: a logical, ROLAP-first
multidimensional layer and its **binding** down to the physical (`db`) and entity-relationship
(`er`) models. It is the outcome of the initial design brainstorm and is the input to a later
grammar/contracts/plan pass. No grammar has been written yet; this is the conceptual and
syntactic shape we agreed on.

The MD model is **editor tooling**, like the rest of this repo — the language is consumed at
runtime by `ai-platform`, which is out of scope here except where a **cross-repo contract** is
called out (§9).

---

## 1. Scope and the two "MD model" meanings

The phrase "multidimensional model" has two meanings we keep distinct:

1. **Logical / ROLAP** — an MD model backed by the relational schema. **This is v1.**
2. **Physical / MOLAP** — an MD model as materialised by BI engines (PowerBI, SSAS). **Later.**

Everything below is the logical model. The logical model is deliberately **binding-free** so that
the ROLAP binding (v1) and a future MOLAP binding both attach to the *same* logical objects as
separate definitions.

### 1.1 Two languages, one in scope

The feature is really **two languages**, and we design only the first now:

- **Layer A — the MD *model* (this doc).** Declarative structure: domains, dimensions, attributes,
  maps, hierarchies, measures, cubelets, and their bindings. Fits TTR's existing `def <kind> <id>
  { … }` paradigm directly.
- **Layer B — the operations DSL (deferred).** Drill/filter paths (`kaufland.sales.2025.january.net`)
  and the transformation algebra (filter/aggr/join/map + store). A separate sublanguage with a
  different lifecycle (runtime scripts, not designer-edited canonical text). Its paradigm is **not
  yet chosen**. The only slice that touches v1 is the *scalar-expression tier* it shares with calc
  maps — and even that is avoided in v1 via the built-in map catalog (§5.4, §9).

---

## 2. Goals and non-goals

**Goals**

- A complete logical MD vocabulary (§5) expressible in TTR's declarative style.
- ROLAP binding of cubelets to relational tables, supporting **read and writeback** (§6), the two
  v1 fact-table **shapes** (wide, long), and **journaling** (overwrite, invalidate, diff).
- Multi-source cubelets (several tables, possibly different shapes, same grain).
- A principled aggregation model covering additive and semi-additive measures (§7).
- Reuse of TTR's permissive-grammar / semantic-validation split: one keyword can serve two schemas
  with per-schema validation (e.g. `attribute`).

**Non-goals (v1)**

- The operations DSL (Layer B) and any general scalar-expression language.
- Query-backed cubelets (brief §"Mapping" v1.1) — table-backed only in v1.
- MOLAP bindings.
- Non-additive measure **recompute** formulas (`sum(num)/sum(den)`); v1 only *marks* non-additive
  measures (§7).
- Custom calc-map logic (e.g. "county from plate number"); v1 ships a **fixed** built-in catalog
  (§5.4).
- Writeback **inverse** strategies beyond declaring the binding; the inverse-N:1 "pick a winner"
  codegen is semantics/runtime, not grammar (§6.3).

---

## 3. Naming and migration

The brief's core nouns collide with TTR 2.3. Resolutions (all agreed):

| Concept | Decision | Migration impact |
|---|---|---|
| **`domain`** | Becomes the **new MD value-set** concept (§5.1). | The existing 2.3 `domain` / `.ttrd` (subject-area grouping) is **renamed to `area` / `.ttra`**. Low real-world usage; coordinate the breaking rename with ai-platform's agent registry. |
| **mapping → `binding`** | The cross-model mapping family is renamed **binding**. `schema map` → **`schema binding`**; `er2db_*` defs move there unchanged in meaning. | Frees the words "map"/"mapping" for the MD primitive. Breaking rename of `schema map`; coordinate with consumers. |
| **`attribute`** | **Kept in both `er` and `md`** (similar meaning). One permissive grammar body (ER-specific and MD-specific props both optional); **semantics** enforces validity per schema. | None — additive, idiomatic to the repo ("parser stays mechanical"). |
| **`map`** | New MD primitive (§5.4). `def map` (definition keyword) and the old `schema map` (schema code) did not clash grammatically; renaming the schema to `binding` removes the human-level clash too. | Covered by the binding rename. |

---

## 4. Object model at a glance

Seven **logical** objects (binding-free) plus a **binding** layer:

```
domain ──────┐                         (value space; calc | bound members)
             ├─ attribute ── dimension (attribute ranges over a domain; belongs to one dimension)
map ─────────┘   │                     (function over domains: table- or calc-backed; N:1 or 1:1)
                 ├─ hierarchy           (ordered path through the N:1 lattice; via: overrides)
measure ─────────┤                     (ranges over a scalar domain; additivity class + aggregation)
                 └─ cubelet             (grain = set of attributes + set of measures)

binding (schema binding):  md2db_cubelet · md2db_domain · md2db_map · md2er_*   (er2db_* also live here)
```

Guiding principle, agreed: **a map is a function** `A×…→X`, hence **N:1 or 1:1** in the forward
(roll-up) direction — "avoid M:N" simply means "be a function." Hierarchy roll-ups are therefore
always maps. The inverse (drill-down / writeback allocation) is *not* a function and needs a
strategy, deferred to §6.3.

---

## 5. Logical model

All examples are under `schema md`.

### 5.1 Domain

A **domain** is a named, typed value space with extensible restriction rules. It is the single
concept behind both dimensional value-sets and measure value-types ("domain is one thing").

```
def domain Money    { type: decimal(18,2) }                            # scalar — no membership
def domain Score    { type: int,    restrict: { range: 1..100 } }
def domain TxnType  { type: string, restrict: { members: {
                        "paper":    { cs: "Papír",    en: "Paper" },
                        "internet": { cs: "Internet", en: "Internet" } } } }
def domain Iban     { type: string, restrict: { pattern: "^[A-Z]{2}…", length: 15..34 } }
def domain Customer { type: string, kind: bound }                      # members from physical model
def domain Month    { type: int,    kind: calc, restrict: { range: 1..12 } }
```

- **`type`** reuses the existing TTR data-type system.
- **`restrict`** is an *extensible block* of named clauses (`range`, `members`, `pattern`,
  `length`, …) combined with AND. New rule kinds are additive grammar bumps. This is the "several
  ways and rules for what's inside a domain."
- **`members`** clauses carry labels — in MD, value labels live on the **domain's members** (ER
  attributes keep their own `valueLabels`).
- **`kind: calc | bound`** (optional; meaningful only for member-set domains, absent for scalars):
  - `bound` — members come from the physical model; the *source* is supplied at binding time
    (`md2db_domain`, §6.4). Absence of an inline member list is **intentional**, not an omission.
  - `calc` — members are **generated by rule** (`Month` = 1..12, a generated calendar).
  - When present, `kind` is authoritative: it enables diagnostics like "`kind: bound` but no source
    bound."
- Domains are **top-level and dimension-independent** — reusable across dimensions; two attributes
  may share one domain. Maps connect domains regardless of dimension.

Parallel worth noting: domain `calc`/`bound` = *how members exist*; map `calc`/`table` (§5.4) =
*how the function is given* — same spirit, different axis.

### 5.2 Dimension

A **dimension** is a conceptual axis. It **owns** its attributes and hierarchies (allowing short
inline attribute defs, like an ER entity) and declares **exactly one `key`** attribute = member
identity.

```
def dimension Customer {
  key: code
  attributes: [
    def attribute code   { domain: md.CustomerCode },   # leaf
    def attribute id     { domain: md.CustomerId   },   # co-leaf, 1:1 to code
    def attribute name   { domain: md.CustomerName },
    def attribute region { domain: md.Region       }
  ]
  hierarchies: [geography]
}
```

`nameAttribute` / `codeAttribute` from ER are **dropped** here; name/code display will be handled
differently later. `key` is the only identity declaration in v1.

### 5.3 Attribute

An **attribute** belongs to one dimension and **ranges over a domain** (instead of carrying a type
directly). Defined inline in a dimension or standalone with a `dimension:` back-reference.

- "Map attribute A to B" is **sugar** surfaced to authors; it resolves to the map between A's and
  B's underlying **domains**.
- **Leaves are emergent, not declared:** an attribute is a leaf iff **no N:1 map targets it**. The
  grain lattice is the partial order induced by N:1 maps.
- **1:1 maps are same-grain aliases** — they connect *co-leaves* (e.g. `code` and `id`) and do not
  demote leaf-ness. A dimension may have several leaves linked 1:1, but **exactly one `key`**.

### 5.4 Map

A **map** is a function over domains, `from: [domains] → to: [domains]`, with two backings:

```
# calc-backed: a built-in, abstract, dialect-lowered function
def map ts_to_day    { from: md.Timestamp, to: md.Day,     calc: truncToDay }
def map month_to_qtr { from: md.Month,     to: md.Quarter, calc: quarterOfMonth }

# table-backed: from/to/cardinality only; the case-table is a binding concern (md2db_map)
def map cc_to_activity { from: [md.Account, md.CostCenter], to: md.Activity, cardinality: N:1 }
```

- **Table-backed (extensional)** — the function is a list of cases stored in a relation. The
  case-table is supplied at binding time (§6.4).
- **Calc-backed (intensional)** — the function is a **named built-in** from a fixed v1 catalog
  (`truncToDay`, `monthOfDate`, `quarterOfMonth`, `yearOfDate`, …, parameterised where needed).
  No general expression language in v1. Built-ins are **abstract / dialect-agnostic**; the runtime
  translator lowers them per SQL dialect.
- `cardinality` is `1:1` or `N:1` (maps are functions). Time and similar hierarchies are realised
  through calc-backed maps; this is why calc maps cannot be fully deferred.
- Custom calc logic (non-catalog functions, e.g. "county from plate") is **out of v1** — it returns
  with the scalar-expression tier (§9).

### 5.5 Hierarchy

A **hierarchy** is a named, ordered **path through the N:1 lattice** of one dimension. Levels are
written **leaf → root**.

```
def hierarchy calendar { dimension: md.Time, levels: [Day, Month, Quarter, Year] }
def hierarchy isoWeeks { dimension: md.Time, levels: [Day, Week] }     # shares the Day leaf
```

- The connecting map between consecutive levels is **inferred by default** (the N:1 map between
  their domains).
- **`via:`** overrides the map for a step.
- **Ambiguity without `via:`** (two N:1 maps connect the same domains) is an **error**.

### 5.6 Measure

A **measure** ranges over a scalar domain and carries an **additivity class** + aggregation (§7).
Standalone (reusable) or inline in a cubelet.

```
def measure net { domain: md.Money, class: additive, aggregation: sum }
```

### 5.7 Cubelet

A **cubelet** is a **grain** (a set of attributes drawn across dimensions) plus a set of measures.
Several cubelets share dimensions at different grains.

```
def cubelet sales {
  grain:    [Customer.code, Product.code, Time.day]
  measures: [net, qty]
}
```

---

## 6. Binding layer (`schema binding`)

Bindings hook the binding-free logical model to the physical model. They mirror the existing
`er2db_*` family (which now also lives in `schema binding`). All `md2db_*` examples below could
equally have an `md2er_*` form, with the caveat in §6.5.

### 6.1 Cubelet → table (wide and long)

```
schema binding

# WIDE — each attribute & measure is its own column
def md2db_cubelet sales_wide {
  cubelet: md.sales
  table:   db.dbo.SALES_FACT
  shape:   wide
  attributes: { Customer.code: CUST_CODE, Product.code: PROD_CODE, Time.day: SALE_DATE }
  measures:   { net: NET_AMT, qty: QUANTITY }
  journaling: overwrite
}

# LONG — attributes are columns; one code column + one value column
def md2db_cubelet drivers_long {
  cubelet: md.costCenterDrivers
  table:   db.dbo.CC_DRIVERS
  shape:   { long: { codeColumn: DRIVER_CODE, valueColumn: DRIVER_VALUE } }
  attributes: { CostCenter.code: CC_CODE }
  measures:   { numberOfFTEs: { code: "FTE" }, numberOfm2: { code: "m2" } }
}
```

An attribute reached **through a map** (the building→cost-center case):

```
attributes: {
  CostCenter.code: { via: md.costCenter_Building2CC, from: { table: db.dbo.BUILDING, column: BUILDING_CODE } }
}
```

### 6.2 Journaling

Per-binding (it governs writeback to *that* table):

- **`overwrite`** — replace the original value.
- **`invalidate: { validColumn: <col> }`** — mark the old row invalid, append a new valid row.
- **`diff`** — append a delta row (new − original).

### 6.3 Read/write derivation

`load` (read) and `store`/`append` (write) SQL are **generated** from the binding declaration; the
brief's RAE `priklady DS RO` notes are the reference for the generated select/join/aggr (load) and
the inverse (store). The hard inverse case — an N:1 attribute map must "pick a winner" on store
(`row_number() … qualify = 1`) — is **codegen in semantics/runtime, not grammar**. The grammar only
declares enough for both directions to be generated.

### 6.4 Bound domains and table-backed maps

First-class bindings (same schema, same pattern), **not** folded into the cubelet binding:

- **`md2db_domain`** — where a `kind: bound` domain's members come from (table + column).
- **`md2db_map`** — the case-table backing a table-backed map (table + the from/to columns, keyed
  per the map's cardinality).

### 6.5 Multi-source, and the thin `md→er` binding

- **Multi-source = multiple defs.** A cubelet fed by several tables (possibly different shapes) is
  modelled as **several `md2db_cubelet` defs** all targeting the same `cubelet:`, each binding a
  subset of measures with its own shape/journaling. Validation: the union of bindings must agree on
  the cubelet's grain.
- **`md→er` is deliberately thinner.** Shape, journaling, and measure-columns are inherently
  *physical*, so an `md2er_*` binding carries only the **structural** part (attribute → ER
  attribute, dimension → entity); the existing ER→DB binding completes the chain. Consequence: a
  pure `md→er` cubelet is **read-oriented** — writeback needs the physical shape to live somewhere
  down the chain. v1 ships `md→db` fully; `md→er` is structural-only.

---

## 7. Aggregation and additivity

Every measure carries an **additivity class** (default `additive`):

- **`additive`** — summable across all dimensions; a single `aggregation` (e.g. `sum`).
- **`semiAdditive`** — summable across some dimensions but not others (typically not time). Modelled
  as a **default agg + per-dimension overrides + a validity marker**:

  ```
  def measure headcount {
    domain: md.Count, class: semiAdditive,
    aggregation: { default: sum, time: latestValid },
    validBy: validFrom            # which attribute marks the current row
  }
  ```

  This covers the brief's `kaufland.zip` "latest valid" case and the classic balance/stock measure.
- **`nonAdditive`** — ratios/rates that cannot be summed across any dimension. In v1 a non-additive
  measure is only **marked** (tooling refuses to blind-sum it and shows "not aggregatable here").
  The `sum(num)/sum(den)` **recompute** formula needs the scalar-expression tier and lands in **v1.1**.

Aggregation defaults also apply to **attributes** in a hierarchy (e.g. a slowly-changing address
rolling up as "latest valid"), using the same vocabulary.

---

## 8. Schemas and file kinds

- New schema code **`md`** for the logical model.
- Cross-model bindings live in **`binding`** (renamed from `map`), housing `er2db_*` and the new
  `md2db_*` / `md2er_*`.
- The 2.3 subject-area grouping moves to **`area`** with file kind **`.ttra`** (was `domain` /
  `.ttrd`).
- File-kind enforcement stays **semantic, not grammatical**, consistent with `graph`/`domain` today.

---

## 9. Cross-repo contract: the built-in map catalog

The calc-map catalog (§5.4) is a **contract between modeler and ai-platform**, like `TTR.g4` and
the stock CNC vocab:

- **Modeler ships the signatures** (input domain type → output domain type, parameters) and
  pre-loads them in `@modeler/semantics` to validate `calc:` references and type-check `from`/`to`
  — mirroring how the stock CNC vocab is pre-loaded today.
- **ai-platform ships the per-dialect lowerings** (Teradata, SQL Server, Postgres, …).

Open: where the catalog physically lives (a new `@modeler/<x>` package vendored to ai-platform,
mirroring `@modeler/grammar`) and its versioning/sync story. **v1 catalog is fixed**; a
**declaration-only escape hatch** (a project declares an extra abstract function — signature here,
lowering supplied downstream) is targeted for **v1.1**, before the full expression tier exists.

---

## 10. Relationship to existing invariants

- **Text is canonical.** The MD model and its bindings are ordinary `.ttr` definitions; the
  Designer (when MD editing lands) issues structured edits, the LSP synthesises `WorkspaceEdit`s,
  the host applies, the LSP re-parses. No independent MD state in the Designer.
- **One LSP across hosts.** All MD understanding lives in `parser` / `semantics` / `lsp`; hosts stay
  thin. The built-in catalog load sits in `semantics`.
- **Parser stays mechanical.** Per-schema validity of the shared `attribute` keyword, additivity
  rules, leaf/grain computation, hierarchy map inference/ambiguity, and `kind` consistency are all
  **semantic** checks — not grammar.
- **Source locations on every node.** Required as ever for the edit synthesizer.

---

## 11. Deferred / open

- **Operations DSL (Layer B)** — paradigm undecided (fluent / algebraic / SQL-like); explore against
  the planning-agent use case. Includes drill/filter dot-notation paths and the
  filter/aggr/join/map/store algebra. The dot-path sugar has a brainstorm-stage design note:
  [`dot-path-sugar.md`](dot-path-sugar.md) (2026-07-07).
- **Scalar-expression tier** — the shared bottom of Layer B; pulled forward only when custom calc
  maps, non-additive recompute, or calculated measures are needed (v1.1).
- **Query-backed cubelets** (v1.1) — read-only or with explicit store scripts.
- **MOLAP bindings** — a second binding family on the same logical objects.
- **Writeback inverse strategies** — allocation/winner-selection beyond the default `top(1)`.
- **Catalog home + sync** and the **escape-hatch** declaration form (§9).

---

## 12. Suggested next steps

1. Decide the built-in map catalog's **home package** and seed the **Time** entries
   (`truncToDay`, `monthOfDate`, `quarterOfMonth`, `yearOfDate`, `weekOfDate`, …).
2. Write a **contracts** doc (object property tables, JSON/AST shapes) and a **grammar** sketch
   (`md` schema, `binding` rename, shared `attribute` body).
3. Plan the **`area` / `.ttra`** and **`schema map → binding`** renames as a coordinated grammar
   bump with ai-platform.
4. Build a handful of **end-to-end fixtures** from the RAE examples (`costCenterTransactions`,
   `otherDrivers` long-shape, `costCenterM2` map-mediated store) as conformance targets.
