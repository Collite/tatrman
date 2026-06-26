# TTR Grammar — MD Model Changes (sketch)

**Status:** sketch v1, 2026-06-25 · Companion to [`design.md`](design.md), [`contracts.md`](contracts.md),
and [`map-catalog.md`](map-catalog.md). Coordination input for the ai-platform Kotlin parser maintainer.

This is the **grammar sketch** the design (§12.2) called for. It is deliberately a sketch, not
final ANTLR: it fixes the tokens, the `objectDefinition` additions, and the *shape* of each new
body, and it states explicitly which checks the grammar does **not** make (they belong to
`@modeler/semantics`, per the "parser stays mechanical" invariant). The authoritative property
tables are in [`contracts.md`](contracts.md); where this doc and contracts disagree, contracts wins.

It assumes **Phase 0 is merged** (grammar 3.0): `schema binding`, `def area`, `.ttrm`, and the
freed `domain` / `map` keywords already exist. Token names below that are marked *(exists)* are
already in `TTR.g4`.

---

## 1. Version bump

- **From:** `3.0` (Phase 0)
- **To:** `3.1` — **additive**.
- **Why additive:** no construct is removed and no existing file changes meaning. The change adds a
  new schema code (`md`), new `def` kinds, and new binding `def` kinds. Every new keyword is also
  added to `idPart` (§8), so any file using these words as cross-reference fragments keeps parsing
  — the same treatment 2.2 (`role`) and 2.3 (`domain`) received.
- Update both version sites in lockstep: `packages/grammar/package.json` and the
  `@grammar-version:` marker at the top of `TTR.g4`. Add a `Changes in 3.1` block to the header
  comment.

> The MD layer is large, but it is purely **new optional surface**. Reserve a major bump for a
> change that breaks existing 3.0 files; this doesn't.

---

## 2. New lexer tokens

Declared **before `IDENT`** (longest-match-then-first-listed), grouped with their kin. `MD` joins
the schema codes. **`MAP` already exists** (`TTR.g4` line ~541, retained in `idPart` since 3.0).
**`DOMAIN` must be re-introduced** — 3.0 *deleted* the token (with the old `.ttrd` block), so 3.1
adds it back as `DOMAIN : 'domain'` and promotes it into `objectDefinition` (§4) as the MD
value-set keyword.

```antlr
// Schema code
MD            : 'md' ;          // new logical schema

// Logical def kinds
DOMAIN        : 'domain' ;      // RE-ADDED in 3.1 (was deleted in 3.0); MD value-set
DIMENSION     : 'dimension' ;
HIERARCHY     : 'hierarchy' ;
MEASURE       : 'measure' ;
CUBELET       : 'cubelet' ;
// MAP already tokenised (line ~541) — promoted into objectDefinition (§4)

// Binding def kinds
MD2DB_CUBELET : 'md2db_cubelet' ;
MD2DB_DOMAIN  : 'md2db_domain' ;
MD2DB_MAP     : 'md2db_map' ;
MD2ER_CUBELET : 'md2er_cubelet' ;

// Logical body property keywords
RESTRICT      : 'restrict' ;
MEMBERS       : 'members' ;
KIND          : 'kind' ;
CALC          : 'calc' ;
KEY           : 'key' ;
HIERARCHIES   : 'hierarchies' ;
LEVELS        : 'levels' ;
VIA           : 'via' ;
CLASS         : 'class' ;
AGGREGATION   : 'aggregation' ;
VALID_BY      : 'validBy' ;
GRAIN         : 'grain' ;
MEASURES      : 'measures' ;
DOMAIN_PROP   : 'domain' ;      // = DOMAIN token reused as the `domain:` property keyword

// Binding body property keywords
CUBELET_PROP  : 'cubelet' ;     // = CUBELET token reused as the `cubelet:` property keyword
SHAPE         : 'shape' ;
JOURNALING    : 'journaling' ;
SOURCE        : 'source' ;

// Range punctuation
DOTDOT        : '..' ;          // placed BEFORE DOT so `1..12` lexes NUM DOTDOT NUM
```

Notes:

- **`restrict`, `members`, `range`, `pattern`, `length`, `wide`, `long`, `overwrite`, `invalidate`,
  `diff`, `additive`, `semiAdditive`, `nonAdditive`, `sum`, `latestValid`, `bound`** etc. are
  **NOT** minted as tokens. They appear only as **object keys or bare-id values** inside generic
  `object_` / `value` bodies and are validated in semantics (§9). Minting a token per enum value
  would explode the lexer for no parser benefit — the repo already does this (`indexTypeValue` is a
  closed token set, but `journaling: overwrite` is an open `id`).
- `DOTDOT` is the only genuinely-new punctuation. `1..12` currently lexes as `NUMBER_LITERAL DOT
  DOT NUMBER_LITERAL`; introducing `DOTDOT` makes the range form unambiguous and readable. Place it
  with `DOT`/`STAR` and **before** `DOT` in the lexer.

---

## 3. Schema code

```antlr
schemaCode
  : DB | ER | BINDING | QUERY | CNC | MD      // MD new
  ;
```

`schema md` opens a logical-model file; `md2*` binding defs live under `schema binding` (§6),
exactly as `er2db_*` do.

---

## 4. `objectDefinition` additions

```antlr
objectDefinition
  : // … all existing alternatives unchanged …
  | DOMAIN        id  mdDomainDef        // 3.1 — re-promoted (was deleted in 3.0)
  | DIMENSION     id  dimensionDef       // 3.1
  | MAP           id  mdMapDef           // 3.1 — `def map` now a real kind
  | HIERARCHY     id  hierarchyDef       // 3.1
  | MEASURE       id  measureDef         // 3.1
  | CUBELET       id  cubeletDef         // 3.1
  | MD2DB_CUBELET id  md2dbCubeletDef    // 3.1 (binding)
  | MD2DB_DOMAIN  id  md2dbDomainDef     // 3.1 (binding)
  | MD2DB_MAP     id  md2dbMapDef        // 3.1 (binding)
  | MD2ER_CUBELET id  md2erCubeletDef    // 3.1 (binding, structural-only)
  ;
```

Per the repo invariant, file-kind/schema validity (an `md` def appearing under `schema er`, a
binding def under `schema md`) is a **semantic** check, not grammatical — mirroring how `graph`
and `area` are policed today.

---

## 5. Logical def bodies

All follow the established `LBRACE (prop (COMMA? prop)* COMMA?)? RBRACE` pattern.

### 5.1 Domain

```antlr
mdDomainDef      : LBRACE (mdDomainProperty (COMMA? mdDomainProperty)* COMMA?)? RBRACE ;
mdDomainProperty : descriptionProperty | tagsProperty
                 | typeProperty                       // reuse (exists) — `type:`
                 | kindProperty
                 | restrictProperty ;

kindProperty     : KIND     propSep? id ;             // `calc` | `bound` — value validated in semantics
restrictProperty : RESTRICT propSep? restrictBlock ;

restrictBlock    : LBRACE (restrictClause (COMMA? restrictClause)* COMMA?)? RBRACE ;
restrictClause   : key propSep? restrictValue ;       // key ∈ {range, members, pattern, length, …} (open)
restrictValue    : rangeLiteral | membersBlock | value ;

rangeLiteral     : NUMBER_LITERAL DOTDOT NUMBER_LITERAL ;
membersBlock     : LBRACE (memberEntry (COMMA? memberEntry)* COMMA?)? RBRACE ;
memberEntry      : stringLiteralForm propSep? localizedString ;  // reuses localizedString (exists)
```

- `restrictClause` is **open** (`key` is any `id`): new restriction kinds (`pattern`, `length`, …)
  are additive without grammar edits. Semantics validates the known clause set and the
  value shape per clause (`md/unknown-restrict-clause`, `md/bad-restrict-value`).
- `members` value labels reuse the `localizedString` block already used by `valueLabels`.

### 5.2 Dimension (with inline attributes)

```antlr
dimensionDef      : LBRACE (dimensionProperty (COMMA? dimensionProperty)* COMMA?)? RBRACE ;
dimensionProperty : descriptionProperty | tagsProperty
                  | keyProperty
                  | attributesProperty        // reuse (exists) — inline `def attribute` list
                  | hierarchiesProperty ;

keyProperty         : KEY         propSep? id ;
hierarchiesProperty : HIERARCHIES propSep? listOfIds ;   // reuse listOfIds (exists)
```

`attributesProperty` already accepts a bracketed list of `def attribute id attributeDef` inline
defs (used by ER entities). MD dimensions reuse it verbatim — the shared `attribute` body is §5.3.

### 5.3 Attribute (shared ER + MD body)

`attributeProperty` (exists) is **extended** with the MD-only `domain:` property; the ER-only
props (`type`, `valueLabels`, …) and the MD-only `domain:` are **all optional** in the grammar,
and semantics enforces "exactly the right ones for this schema":

```antlr
attributeProperty
  : descriptionProperty | tagsProperty
  | typeProperty | isKeyProperty | optionalProperty
  | valueLabelsProperty | displayLabelProperty | searchBlockProperty | bindingProperty
  | domainRefProperty                                  // 3.1 — MD attribute ranges over a domain
  | aggregationProperty                                // 3.1 — attribute roll-up agg in a hierarchy
  ;

domainRefProperty : DOMAIN_PROP propSep? id ;          // `domain: md.CustomerCode`
```

Semantics (`md/attr-needs-domain` if an `md` attribute lacks `domain:`; `er/attr-domain-in-er`
if an ER attribute carries `domain:` — see [`contracts.md`](contracts.md) §7 for the canonical
code set). This is the design's "one permissive body, per-schema
validation" (design §3, §10).

### 5.4 Map

```antlr
mdMapDef      : LBRACE (mdMapProperty (COMMA? mdMapProperty)* COMMA?)? RBRACE ;
mdMapProperty : descriptionProperty | tagsProperty
              | fromProperty | toProperty          // reuse (exists) — value is `id` or a `list` of ids
              | cardinalityProperty                // reuse (exists)
              | calcProperty ;

calcProperty  : CALC propSep? calcRef ;

// DECIDED (2026-06-25): a dedicated rule with NAMED parens args — NOT the existing
// `functionCall` (which is positional-only and can't express `fiscalYearStartMonth: 4`).
calcRef       : id ( LPAREN ( calcArg (COMMA calcArg)* )? RPAREN )? ;   // `truncToDay` | `fiscalYearOfDate(fiscalYearStartMonth: 4)`
calcArg       : id propSep? value ;
```

- `from`/`to` already accept a `value` (an `id` or a `list`), covering both single- and
  multi-domain maps (`from: [md.Account, md.CostCenter]`).
- `calc:` is a catalog reference; **absence of `calc:`** ⇒ table-backed map (case-table supplied by
  `md2db_map`, §6). Semantics validates the reference + args against [`map-catalog.md`](map-catalog.md)
  (`md/unknown-calc-map`, `md/bad-calc-args`, `md/calc-type-mismatch`).
- Calc params use **named args** (`calcArg : id propSep? value`), so optional/defaulted params
  (the fiscal/week entries) are order-independent and adding a param later is non-breaking. This
  reuses TTR's `key: value` idiom rather than positional `functionCall`.

### 5.5 Hierarchy

```antlr
hierarchyDef      : LBRACE (hierarchyProperty (COMMA? hierarchyProperty)* COMMA?)? RBRACE ;
hierarchyProperty : descriptionProperty | tagsProperty
                  | dimensionRefProperty
                  | levelsProperty ;

dimensionRefProperty : DIMENSION propSep? id ;       // `dimension: md.Time`
levelsProperty       : LEVELS    propSep? levelList ;

levelList  : LBRACK ( levelEntry (COMMA levelEntry)* )? COMMA? RBRACK ;
levelEntry : id (VIA id)? ;                          // `Quarter` | `Quarter via md.month_to_qtr`
```

Levels are written **leaf→root** (design §5.5). The connecting map between consecutive levels is
**inferred** in semantics; `via:` pins it; ambiguity without `via:` ⇒ `md/ambiguous-hierarchy-step`.

### 5.6 Measure

```antlr
measureDef      : LBRACE (measureProperty (COMMA? measureProperty)* COMMA?)? RBRACE ;
measureProperty : descriptionProperty | tagsProperty
                | domainRefProperty          // reuse §5.3 `domain:`
                | classProperty
                | aggregationProperty
                | validByProperty ;

classProperty       : CLASS       propSep? id ;            // additive | semiAdditive | nonAdditive
aggregationProperty : AGGREGATION propSep? aggregationValue ;
validByProperty     : VALID_BY    propSep? id ;

aggregationValue
  : id                                                     // `sum` (simple)
  | object_ ;                                              // `{ default: sum, time: latestValid }` (per-dim overrides)
```

`class` / agg-function / `validBy` consistency is semantic (default `additive`; `validBy` required
iff a `latestValid`-style override is present; `nonAdditive` ⇒ marked-only in v1). See contracts.

### 5.7 Cubelet

```antlr
cubeletDef      : LBRACE (cubeletProperty (COMMA? cubeletProperty)* COMMA?)? RBRACE ;
cubeletProperty : descriptionProperty | tagsProperty
                | grainProperty
                | measuresProperty ;

grainProperty    : GRAIN    propSep? listOfIds ;     // `[Customer.code, Product.code, Time.day]` (dotted ids ok)
measuresProperty : MEASURES propSep? measuresValue ;

measuresValue
  : listOfIds                                        // references to standalone measures
  | measureInlineList ;                              // inline `def measure` list
measureInlineList : LBRACK ( DEF MEASURE id measureDef COMMA? )* RBRACK ;
```

`grain` items are dotted `Dimension.attribute` ids (already legal `id`s). Inline measures mirror
the inline-attribute pattern.

---

## 6. Binding def bodies (`schema binding`)

The `md2db_*` family mirrors `er2db_*`: brace bodies, mostly generic `object_` maps validated in
semantics. Shapes only here; property tables in [`contracts.md`](contracts.md) §6.

```antlr
md2dbCubeletDef      : LBRACE (md2dbCubeletProperty (COMMA? md2dbCubeletProperty)* COMMA?)? RBRACE ;
md2dbCubeletProperty : descriptionProperty | tagsProperty
                     | cubeletRefProperty            // `cubelet: md.sales`
                     | targetProperty                // reuse (exists) — `table: db.dbo.SALES_FACT`
                     | shapeProperty
                     | attributesMapProperty         // `attributes: { Customer.code: CUST_CODE, … }`
                     | measuresMapProperty           // `measures: { net: NET_AMT, … }`
                     | journalingProperty ;

cubeletRefProperty : CUBELET_PROP propSep? id ;
shapeProperty      : SHAPE        propSep? shapeValue ;
journalingProperty : JOURNALING   propSep? journalingValue ;
attributesMapProperty : ATTRIBUTES propSep? object_ ;   // reuse ATTRIBUTES token; body is a generic map
measuresMapProperty   : MEASURES   propSep? object_ ;

shapeValue
  : id                                                 // `wide`
  | object_ ;                                          // `{ long: { codeColumn: …, valueColumn: … } }`

journalingValue
  : id                                                 // `overwrite` | `diff`
  | object_ ;                                          // `{ invalidate: { validColumn: … } }`

md2dbDomainDef       : LBRACE (md2dbDomainProperty (COMMA? md2dbDomainProperty)* COMMA?)? RBRACE ;
md2dbDomainProperty  : descriptionProperty | tagsProperty
                     | domainRefProperty               // reuse §5.3 — which domain
                     | sourceProperty ;                // `source: { table: …, column: … }`
sourceProperty       : SOURCE propSep? object_ ;

md2dbMapDef          : LBRACE (md2dbMapProperty (COMMA? md2dbMapProperty)* COMMA?)? RBRACE ;
md2dbMapProperty     : descriptionProperty | tagsProperty
                     | mapRefProperty                  // which `def map`
                     | targetProperty                  // case-table
                     | object_                          // from/to column keying (generic, semantic-checked)
                     ;
mapRefProperty       : MAP propSep? id ;                // reuse MAP token as `map:` property

md2erCubeletDef      : LBRACE (md2erCubeletProperty (COMMA? md2erCubeletProperty)* COMMA?)? RBRACE ;
md2erCubeletProperty : descriptionProperty | tagsProperty
                     | cubeletRefProperty
                     | targetProperty                  // ER entity
                     | attributesMapProperty ;         // structural only — NO shape/journaling/measures (design §6.5)
```

- **Attribute-via-map** in a cubelet binding (the building→cost-center case, design §6.1) is a
  nested `object_` value (`{ via: md.…, from: { table: …, column: … } }`) — generic in grammar,
  shape-checked in semantics.
- **Multi-source** is "several `md2db_cubelet` defs same `cubelet:`" — no grammar support needed;
  the grain-union agreement is a semantic check (design §6.5).

---

## 7. Shared / reused value forms

Reused unchanged: `propSep`, `value`, `literal`, `list`, `listOfStrings`, `listOfIds`, `object_`,
`functionCall`, `localizedString`, `dataType`, `id`/`idPart`. New: `rangeLiteral`, `restrictBlock`,
`membersBlock`, `levelList`, `aggregationValue`, `shapeValue`, `journalingValue`, `measureInlineList`.

---

## 8. `idPart` extension

Append the new keywords so they remain usable as cross-reference fragments (e.g. a column literally
named `measure`), keeping the bump additive:

```antlr
idPart
  : // … existing …
  | MD                                                          // 3.1 schema code
  | DOMAIN | DIMENSION | HIERARCHY | MEASURE | CUBELET          // 3.1 def kinds (DOMAIN re-added; MAP already present)
  | MD2DB_CUBELET | MD2DB_DOMAIN | MD2DB_MAP | MD2ER_CUBELET    // 3.1 binding kinds
  | RESTRICT | MEMBERS | KIND | CALC | KEY | HIERARCHIES
  | LEVELS | VIA | CLASS | AGGREGATION | VALID_BY | GRAIN
  | MEASURES | SHAPE | JOURNALING | SOURCE                      // 3.1 body keywords
  ;
```

(`DOMAIN`/`MAP`/`ATTRIBUTES`/`DIMENSION` as a property keyword are already covered or added above.)

---

## 9. What stays semantic (NOT grammar)

Per "parser stays mechanical," the grammar accepts the permissive superset; these are diagnosed in
`@modeler/semantics` (codes in [`contracts.md`](contracts.md) §7):

- `md` defs only under `schema md`; `md2*` only under `schema binding`.
- `attribute` body validity per schema (`domain:` required in md, forbidden in er; `type:` the
  reverse).
- `kind: calc|bound` value set; `kind: bound` with no `md2db_domain` source ⇒ error.
- `restrict` clause names + per-clause value shapes.
- `calc:` reference resolution, arg validation, and `from`/`to` type-check against the catalog.
- Leaf/grain computation from N:1 maps; hierarchy step inference + ambiguity.
- Measure additivity consistency (`class`, `aggregation`, `validBy`).
- Cubelet grain references resolve to real `Dimension.attribute`s; multi-source grain agreement.
- Binding completeness (`shape`/`journaling` present where writeback is implied; md→er structural-only).

---

## 10. Open grammar questions

1. ~~**Calc param surface**~~ — **DECIDED (2026-06-25):** a dedicated `calcRef` rule with named
   parens args (§5.4). Rejected: positional `functionCall` (order-fragile for optional params) and
   the `calc` + `calcArgs: {…}` two-property split.
2. **Range literal reach** — introduce `DOTDOT` (this sketch) vs. keep `NUM DOT DOT NUM` and parse
   it in the walker. `DOTDOT` is cleaner and low-risk; confirm.
3. **`md2er_*` breadth** — v1 ships `md2er_cubelet` (structural). Do we also want `md2er_domain` /
   `md2er_map` now, or add additively when a model needs them? (design §6.5 leans thin.)
4. **`grain`/`measures` as dotted vs. nested** — `listOfIds` of dotted ids is proposed; an
   alternative `{ Customer: [code], … }` nesting was considered and rejected as less uniform.
