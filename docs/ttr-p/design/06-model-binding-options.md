# D — Model Binding, World & Project Defaults: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the control-room decision log.
> Control surface: [`00-control-room.md`](./00-control-room.md). Canonical DSL (C3, converged): [`05-canonical-dsl-options.md`](./05-canonical-dsl-options.md). World/manifest groundwork: [`02-internal-model-options.md`](./02-internal-model-options.md) §T4/§T6.
> Opened 2026-07-03, immediately after C3.

**The question D must answer.** How do PL programs reference TTR model objects and world objects; where do schemas, the world document, and project defaults live; and how does a name in program text deterministically become a physical object on a concrete engine (P2 — the whole resolution chain must be explicit or default-derived).

## What's already banked (constraints, not options)

- The **world is a TTR-family document (`def world …`) and a compile target**; instances `extends` type manifests; runtime verifies compatibility (T6).
- **Schema-on-read is banned**; modeled objects get schemas from TTR models via the metadata component; ad-hoc files require declared schemas — "world doc or inline" (T7 sweep).
- **Movement is synthesized** from world relations + a **default staging area** that D must define (C3-d-iv).
- **Project defaults** must supply: world ref, staging, default display sink, bare-fragment container target + shell, T5-b split policy (C0/C3).
- Tatrman's `World` is idea-donor (Storage/Compute environments, per-program internal world, read/write/move relations); its `TableContainer` naming clashes with PL's `Container` node — the storage grouping needs a new name (T4).
- TTR already ships the **`binding` schema** (er2db, md2db def kinds; inline `binding:` sugar on entities/attributes/relations) — the logical→physical mapping *inside* the model. TTR v1.1 has **packages + imports** (`import x.y.*`) and qnames.

## The hero rendering, annotated with D debts

```pl
uses world "acme-prod"          // D-e: where is the default? what does the string resolve to?
container acc_prep target erp_pg """sql     // D-d: 'erp_pg' = engine instance in the world
    select … from erp.accounts               // D-a/b: model object ref inside a fragment
"""
container crunch(…) target polars {
    sales = load(files.sales_2026, schema: sales_csv)
    //           ^ D-b: storage-object ref   ^ D-c: named schema — declared where?
    …
}
acc_prep -> crunch.accounts     // D-f: synthesized movement needs a staging area
crunch.result -> display(…)     // D-e: bare programs need a *default* display sink
```

---

## D-a · What can a program reference — the object tiers

TTR models carry three data-object tiers: **db** (physical tables/columns), **er** (logical entities/attributes/relations, bound to db via `binding`), **md** (cubes/dimensions/measures, bound via md2db).

- **D-a-α · Physical only.** PL names db-schema objects (`erp.accounts` = a db table). er/md invisible to PL.
  - *Buys:* simplest resolution (model → world directly); SQL-fragment mental model is exact.
  - *Costs:* betrays A2's point (PL "references TTR models" shrinks to "references table lists"); analysts think in entities; renames/rebindings in the model don't shield programs.
- **D-a-β · Logical-first.** Programs name **er entities**; the compiler resolves entity → (binding) → db table → (world) → instance. Physical refs = escape hatch.
  - *Buys:* programs survive physical remapping; the analyst persona (A1) speaks entity language; joins could later exploit modeled `relation`s (join-condition inference — but careful: P2).
  - *Costs:* every program hop goes through binding indirection; a db table with no er entity needs the escape hatch anyway; "which db schema does this entity bind to *in this world*" adds a resolution axis.
- **D-a-γ · Both tiers, explicitly distinguished.** db objects AND er entities are referenceable; the ref kind is visible (syntax or resolution rule). md objects join later via the MD-sugar session (parked).
  - *Buys:* engineer persona gets tables, analyst persona gets entities; honest about mixed reality.
  - *Costs:* two ref kinds through the whole chain (checker, fragments, graphical); anti-P1 surface growth.
- **D-a-δ (weird) · World-only.** PL sees only world storage objects; TTR models are just schema *providers* behind the world. Model refs never appear in programs.
  - *Buys:* one namespace; PL fully decoupled from model evolution.
  - *Costs:* contradicts A2 outright; duplicates the model into the world.

*Lean: γ pragmatically, β aspirationally — but note v1 hero + fragments only need db-tier; a defensible v1 cut is α now, γ's syntax reserved (er refs added without breaking programs).*

## D-b · Reference syntax & namespaces

Program text meets at least four name kinds: **model objects** (`erp.accounts`), **storage objects** (`files.sales_2026`), **engine instances** (`erp_pg`), **schemas** (`sales_csv`). Options for keeping them apart:

- **D-b-α · One unified namespace.** Everything is a qname resolved against model ∪ world; kind inferred from what the name denotes; ambiguity = compile error (P2-legal).
  - *Buys:* no new syntax; reads clean.
  - *Costs:* "what does this name mean" needs global knowledge; collisions surface late (adding a world object can break a program).
- **D-b-β · Position-typed namespaces (context decides).** Each syntactic position knows its namespace: `target <x>` resolves x in *engine instances*; `load(<x>, …)`/`store(<x>)` resolve x in *storage objects*; `schema: <x>` in *schemas*; table refs in fragments/ops resolve in *model objects*. No sigils, no global namespace.
  - *Buys:* zero ambiguity by construction; matches how the hero already reads; each position's checker gives precise errors.
  - *Costs:* the same name means different things in different positions (probably fine — SQL does this); "load a *model* object into another engine" needs a rule (see D-b-iii below).
- **D-b-γ · Sigil/keyword-marked.** Storage refs marked (`@files/sales_2026` or `storage files.sales_2026`), model refs plain.
  - *Buys:* self-describing anywhere.
  - *Costs:* syntax noise; the graphical/fragment surfaces must carry it too.
- **D-b-ii · Imports/aliases:** TTR v1.1 already has `import x.y.*` — PL documents could import model packages / world namespaces to shorten qnames. Orthogonal to α/β/γ; adopt TTR's qname rules either way.
- **D-b-iii · Model refs in `load`:** `load(erp.accounts)` from a *polars* container — a modeled object pulled cross-engine (schema from model, location from world, movement synthesized). Position-typed reading: `load` accepts storage objects *and* model objects; a model object in load position = "the physical table this model object denotes in this world."

*Lean: β + ii (position-typed + TTR imports), with iii accepted.*

## D-c · Schema declarations for ad-hoc data

For non-modeled data (files, ad-hoc tables) — T7 banked "declared, never inferred". Where do declarations live?

- **D-c-α · In the world doc.** Storage objects carry schemas: `def file sales_2026 { schema { customer: string, amount: decimal(12,2), … } }`. `schema: sales_csv` references a named schema defined beside the storage.
  - *Buys:* schemas live with the data they describe; world = one reviewable environment truth; reusable across programs.
  - *Costs:* the world doc grows into a mini-model; ad-hoc-ness is lost (every CSV needs a world edit).
- **D-c-β · In the program, inline or named.** `load(files.sales_2026, schema: { customer: string, … })`; document-level `def schema sales_csv { … }` for reuse within the program.
  - *Buys:* self-contained programs; true ad-hoc workflow (analyst gets a CSV, writes a program, done).
  - *Costs:* same file loaded by five programs = five copies drifting.
- **D-c-γ · Model everything.** Ad-hoc files get modeled (a file-storage db-schema flavor in TTR); no second schema mechanism.
  - *Buys:* one mechanism, model tooling everywhere.
  - *Costs:* heavyweight for the "quick CSV" case; TTR db schema is table-shaped, files need envelope extras (format, header, encoding).
- **D-c-δ · Both α and β** (world for shared/stable, inline for ad-hoc), P2-ordered: explicit inline > named-in-program > world-declared; conflict = error.

*Lean: δ — T7's own wording ("world doc or inline") anticipated it.*

## D-d · The world document — grammar & model attachment

**Shape.** T6 says `def world …` is TTR-family. Sub-options:
- **D-d-α · New TTR schema kind** (`schema world` beside `db`/`er`/`md`/`binding`/`cnc`): `def storage`, `def engine`, `def executor` kinds inside; instance `extends` a type manifest; same parser/tooling, world files are model files (extension → H).
- **D-d-β · Standalone PL-side grammar** (world parsed by the PL toolchain, not TTR's).
  - α keeps one toolchain and makes the world Designer-viewable for free; β decouples release cadence. *Lean: α.*

**Content sketch (for concreteness, not decision):**
```ttr
schema world
def world acme_prod {
    def engine erp_pg     { type: postgres, version: 16, extensions: [pg_trgm] }
    def engine polars     { type: polars }
    def executor sh       { type: bash }
    def storage erp_db    { type: postgres, via: erp_pg, namespaces: [erp] }
    def storage files     { type: local_dir, path: "…", staging: false }
    def storage stage     { type: local_dir, path: "…", staging: true }
}
```
(Naming: Tatrman's `TableContainer` becomes **`namespace`** here — placeholder, → H.)

**Model-to-world attachment — the load-bearing sub-fork.** `erp.accounts` (model) must land on `erp_db` (world storage) in *this* world. Who says so?
- **D-d-i · The world hosts models:** storage declares `hosts: [erp]` (model package ↔ storage namespace mapping lives in the world doc). *Buys:* world = the one environment truth; same model attaches differently in dev/prod worlds (exactly what worlds are for). *Costs:* world doc references model internals.
- **D-d-ii · Project defaults map models → storages.** *Buys:* worlds stay model-agnostic. *Costs:* splits environment truth across two files; a world is no longer self-describing.
- **D-d-iii · The TTR binding schema grows a `db2world` kind.** *Buys:* all binding tiers in one schema code. *Costs:* bindings become world-specific (they're currently world-independent logical↔physical maps); model files would name deployment environments — layering violation.

*Lean: i — attachment is environment truth, and dev/prod variance is the world's raison d'être.*

## D-e · Project defaults — format & precedence

Keys owed so far: world ref · default staging · default display sink · bare-fragment target + shell · T5-b split policy (split-with-warning vs refuse).

- **D-e-α · Extend the project manifest** (`modeler.toml`-equivalent, a `[pl]` table). *Buys:* one project file, tooling already resolves it (TTR walks up to `modeler.toml`); config-shaped things in a config file. *Costs:* defaults invisible to the TTR/PL parser (toml, not family text).
- **D-e-β · A TTR-family defaults document** (`def project` / `def defaults`). *Buys:* text-canonical consistency; reviewable/diffable like everything else. *Costs:* second project-level file kind; grammar for what is honestly key-value config.
- **D-e-γ · Inside the world doc.** *Costs:* wrong layer — "which world" can't live inside a world; display sink/split policy are project posture, not environment fact. Listed for completeness.
- **Precedence (any format):** document pin (`uses world`) > project defaults; **nothing below** (P2 — no user-level dotfiles in the resolution chain, or they must be explicitly out-of-scope v1).

*Lean: α — it's config; the world stays the TTR-family artifact.*

## D-f · The staging area (movement synthesis's missing input)

Synthesized Store+Transfer+Load needs "where does the intermediate live". Options:
- **D-f-α · One declared default** — the world marks one storage `staging: true`; every synthesized crossing uses it; more than one marked = compile error unless the pair narrows it (P2).
- **D-f-β · Per-pair resolution (Tatrman's `getEnvForTransfer`)** — feasibility from read/write/move relations: staging = a storage both sides reach. Deterministic only with a tie-break rule; "first found" is a miracle. Needs α as the tie-break anyway.
- **D-f-γ · Per-container/edge override** — author says `via stage2` on a wiring statement or container; synthesis respects it.

*Lean: α as default + β as feasibility check (declared staging must be reachable by both sides, else error) + γ as the explicit escape hatch. Composes with C3-d-iv cleanly.*

## D-g · Resolution machinery (Q6 lands here)

- **The metadata component** (modeler-owned, Kotlin/JVM, wrapped by Ariadne per Q6): PL compiler embeds it; resolves model refs + binding chains **offline from the model repo** (compiler must work with no service, per T6's compile-target stance). Kantheon workspace metadata = a *population source* for world/schema data (T4), never the compile-time truth.
- **`ttr-metadata` vs `md-catalog`:** md-catalog = the MD calc-map catalogue (data-only leaf; `MD_CATALOG_VERSION` sync key) — it feeds the **function catalogue** side (T5-c "two catalogues behind one interface"); ttr-metadata (the component Q6 names) feeds **object/schema resolution**. Distinct axes; the MD-sugar session decides how md-catalog's calc maps surface in PL expressions.
- Open: does the PL compiler read the model repo directly (paths in project defaults) or through a `pl/getWorld`-style Designer-server seam only in editor contexts (compiler CLI reads directly either way)?

## D-h · Which ops apply to which model kind (frame only — parked)

er entity refs behave as tables (via binding) for the whole T10 node set. **md objects** (cubes/measures/hierarchies) are where op-applicability gets interesting (Aggregate/Pivot sugar over cubelets, calc-map functions) — **that is the parked MD-sugar session**, unchanged. D only reserves: the ref syntax chosen in D-a/D-b must not preclude md refs later.

---

## RESOLVED (2026-07-03) — D converged (core)

All threads decided in-session (full text in the control-room decision log):

- **D-a = γ**: all tiers referenceable by design; **v1 = db + er**, md reserved for the MD-sugar session · ref kind **package-derived** (full TTR qnames unambiguous; short forms via `import`) · er depth = **names + relation-joins** (`on: relation <name>`).
- **D-b**: world names ride the **same qname + import mechanism**, plus **position-typing** (each syntactic position checks its kind).
- **D-c = δ**: schemas in **both homes**, P2-ordered (inline > named-in-program > world-declared; same-level conflict = error).
- **D-d**: world = **new TTR schema kind** (`schema world`; `def engine`/`def executor`/`def storage`; instance `extends` type manifest) · attachment = **world hosts models** (`hosts: [erp]` on storage).
- **D-e = α**: project defaults in the **project manifest** `[pl]` table; precedence document pin > project defaults > nothing.
- **D-f**: staging = **declared default + feasibility check + `via` override**.
- **D-g confirmed**: compiler **embeds the metadata component**, reads model repo + world directly via project-default paths, offline; Kantheon metadata = world-population source only; Ariadne stays the runtime wrapper.

### The er-flavored hero variant (D-a sub-2 in the flesh)

```pl
import erp.er.*                              // short entity names (package-derived kinds)

container crunch(in accounts, out result, err rejects) target polars {

    c = load(customer)                       // er entity: schema = attributes, logical names
    c = filter(c, customerType = 'retail')   // attribute name — er2db binding maps it at emit

    j = join(left: c, right: sales_txn,
             on: relation customer_sales)    // the modeled relation IS the join condition

    result  = j -> aggregate { group by region; total = sum(amount) }
    rejects = j.rejects
}
```

## Open questions (D-local)

- World file naming/extension; `namespace` vs other name for storage grouping (→ H).
- Does a world doc `extends`/import another world (dev extends prod-shape)? (Packages/imports exist in TTR — probably free.)
- Schema type vocabulary for D-c declarations: reuse TTR db-schema attribute types verbatim?
- Auth/credentials in the world doc: named-connection indirection only (Charon precedent — names, never secrets)?
- Multiple worlds in one project (dev/test/prod) — selection = project default + `uses world` pin (already banked); do world *documents* live in the model repo or beside programs?

## Cross-links

D → C3 (position-typed namespaces must cover fragments too — TTR-SQL table refs are model refs) · D → T6/T4 (world doc = instance overlays; per-program internal world) · D → T7 (declared schemas) · D → E (id-normalization/`languageDetails` per engine at emit) · D → F (executor instances in the world) · D → Q6/G (metadata component; Ariadne wrapper) · D → MD-sugar session (D-h) · D → H (names: world extension, namespace, defaults file).
