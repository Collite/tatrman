# Built-in Calc-Map Catalog — v1 (Time)

**Status:** proposal v1, 2026-06-25 · Companion to [`design.md`](design.md) §5.4 and §9 · Cross-repo contract with `ai-platform`.

This document is the **initial catalog of built-in calc-backed maps** — the named,
parameterised, dialect-agnostic functions a `def map { … calc: <name> }` may reference in v1.
Per [`design.md`](design.md) §9 it is a **contract between two repos**:

- **modeler ships the signatures** (this doc → seeded into the new `@modeler/md-catalog`
  package, §4) so `@modeler/semantics` can validate every `calc:` reference and type-check the
  map's `from` / `to` against the entry's input/output types.
- **ai-platform ships the per-dialect lowerings** (Teradata, SQL Server, Postgres, …). The
  catalog itself contains **no SQL** — only abstract semantics.

v1 ships a **fixed** catalog. There is no general expression language and no project-level
custom function in v1; the declaration-only escape hatch is v1.1 (design §9, §11). Everything
here is the **Time** family — the one hierarchy domain that cannot be modelled extensionally and
therefore forces calc maps into v1.

---

## 1. Model: what a catalog entry is

A catalog entry is an **abstract function signature**, not an implementation:

```
entry := {
  name:        <identifier>            # referenced by `calc:` in a map def
  category:    truncation | extraction | rollup | fiscal
  params:      [ { name, type, default? } ]   # 0..n configuration parameters
  input:       <domain-type-shape>     # what the map's single `from` domain must satisfy
  output:      <domain-type-shape>     # what the map's `to` domain must satisfy
  cardinality: N:1                     # every time map is a function and coarsening ⇒ N:1
  semantics:   <prose>                 # the abstract meaning ai-platform lowers
}
```

`<domain-type-shape>` is expressed in the **existing TTR type system** plus an optional value
range, because the catalog type-checks against the *domain's* `type` / `restrict`, never against a
SQL column. The shapes used below:

| Shape | Meaning | Matches domains like |
|---|---|---|
| `instant` | a point in time at sub-day resolution | `type: timestamp` / `datetime` |
| `date` | a calendar day | `type: date` |
| `instant\|date` | either of the above | `Timestamp`, `Day` |
| `int{lo..hi}` | integer constrained to a range | `Month` (`1..12`), `Quarter` (`1..4`) |
| `int` | unbounded integer | `Year` |

> **Cardinality note.** Every entry is **N:1** in the forward (roll-up) direction — coarsening
> time is many-to-one. This satisfies the design's "a map is a function" invariant (§4). The
> catalog never produces a `1:1` entry; calendar co-leaves (e.g. `date` ↔ an integer day key) are
> modelled as table-backed or as separate same-grain attributes, not via a calc map.

---

## 2. The v1 Time catalog

Four families. **Truncation** keeps the value in the time domain but coarsens resolution.
**Extraction** pulls a calendar component out as a small bounded integer. **Roll-up** climbs one
calendar level to the next (the maps a `hierarchy` infers between consecutive levels). **Fiscal**
is the parameterised tail that real models always need.

### 2.1 Truncation — `instant|date → instant|date`

Maps a finer instant to the **start of its containing period**. Output domain is still an instant
(or date). These realise "round a timestamp down to the day/month/…".

| `name` | input | output | params | semantics |
|---|---|---|---|---|
| `truncToSecond`  | `instant` | `instant` | — | drop sub-second precision |
| `truncToMinute`  | `instant` | `instant` | — | zero the seconds |
| `truncToHour`    | `instant` | `instant` | — | zero minutes & below |
| `truncToDay`     | `instant\|date` | `date` | — | midnight of the same calendar day |
| `truncToWeek`    | `instant\|date` | `date` | `weekStart: mon\|sun = mon` | first day of the containing week |
| `truncToMonth`   | `instant\|date` | `date` | — | first day of the containing month |
| `truncToQuarter` | `instant\|date` | `date` | — | first day of the containing quarter |
| `truncToYear`    | `instant\|date` | `date` | — | first day of the containing year |

### 2.2 Extraction — `instant|date → int{lo..hi}`

Pulls a calendar component out as a bounded integer. Output domain is a small enum/range domain
(e.g. `Month = int{1..12}`). These are the `monthOfDate`, `yearOfDate` family named in the design.

| `name` | input | output | params | semantics |
|---|---|---|---|---|
| `secondOfMinute` | `instant` | `int{0..59}` | — | seconds component |
| `minuteOfHour`   | `instant` | `int{0..59}` | — | minutes component |
| `hourOfDay`      | `instant` | `int{0..23}` | — | hour component (24h) |
| `dayOfMonth`     | `instant\|date` | `int{1..31}` | — | day-of-month |
| `dayOfWeek`      | `instant\|date` | `int{1..7}`  | `weekStart: mon\|sun = mon` | day-of-week, 1 = `weekStart` |
| `dayOfYear`      | `instant\|date` | `int{1..366}` | — | ordinal day in year |
| `weekOfYear`     | `instant\|date` | `int{1..53}` | `scheme: iso\|us = iso` | week number |
| `monthOfDate`    | `instant\|date` | `int{1..12}` | — | month number |
| `quarterOfDate`  | `instant\|date` | `int{1..4}`  | — | quarter number |
| `yearOfDate`     | `instant\|date` | `int`        | — | calendar year |

### 2.3 Roll-up — `int{lo..hi} → int{lo'..hi'}`

Climbs one calendar level to the coarser one. These are the maps a `def hierarchy` infers between
consecutive `levels` when both levels are calendar-integer domains (design §5.5). `quarterOfMonth`
is the design's worked example (`def map month_to_qtr { calc: quarterOfMonth }`).

| `name` | input | output | params | semantics |
|---|---|---|---|---|
| `quarterOfMonth` | `int{1..12}` | `int{1..4}` | — | `⌈m/3⌉` |
| `monthOfWeek`    | `int{1..53}` | `int{1..12}` | `scheme: iso\|us = iso` | month a week predominantly falls in |
| `quarterOfWeek`  | `int{1..53}` | `int{1..4}`  | `scheme: iso\|us = iso` | quarter for a week |
| `halfOfQuarter`  | `int{1..4}`  | `int{1..2}`  | — | `⌈q/2⌉` |

> Roll-ups operate on **component integers** within one calendar, not on instants. A hierarchy
> `[Day, Month, Quarter, Year]` mixes a date leaf with integer levels; the `Day→Month` step is an
> **extraction** (`monthOfDate`), and `Month→Quarter` is a **roll-up** (`quarterOfMonth`). The
> inference rule (which catalog entry connects two domains) is a **semantics** concern — see
> [`contracts.md`](contracts.md) §"hierarchy inference".

### 2.4 Fiscal — parameterised offset variants

Fiscal calendars are the most common reason a built-in alone is insufficient, so v1 ships the
parameterised forms rather than forcing custom calc. The `fiscalYearStartMonth` parameter (1–12,
where `4` = April-start) shifts the calendar.

| `name` | input | output | params | semantics |
|---|---|---|---|---|
| `fiscalYearOfDate`    | `instant\|date` | `int` | `fiscalYearStartMonth: int{1..12} = 1` | fiscal year label |
| `fiscalQuarterOfDate` | `instant\|date` | `int{1..4}` | `fiscalYearStartMonth: int{1..12} = 1` | fiscal quarter |
| `fiscalMonthOfDate`   | `instant\|date` | `int{1..12}` | `fiscalYearStartMonth: int{1..12} = 1` | fiscal month ordinal |
| `fiscalQuarterOfMonth`| `int{1..12}` | `int{1..4}` | `fiscalYearStartMonth: int{1..12} = 1` | fiscal quarter for a calendar month |

When `fiscalYearStartMonth = 1` each fiscal entry is **identical** to its plain counterpart;
semantics may warn (not error) that the plain form is preferred for clarity.

---

### 2.5 What ships in v1 — the floor (decided 2026-06-25, grounded in `ai-models`)

§2 is the **menu**; v1 ships a justified subset, because the catalog grows cheaply (adding an entry
= minor version bump) but a wrong signature is a **breaking** major bump. So ship only what's
certain or proven-used; defer the speculative tail.

The floor below is **pinned against the real `~/Dev/ai-models` content** (model-ttr + model-yaml).
What that grep found:

- Heavy `date`/`datetime` columns (`datum_*`); explicit `roční` (annual) and monthly aggregation
  (`tržba_12_měsíc` / `24_měsíc`) → day/month/year are core needs.
- model-yaml carries literal SQL filter expressions `DATEPART(quarter, …)` (`čtvrtletí`) and
  `DATEPART(week, …)` (`tento_týden` / `poslední týden`) → these are exactly `quarterOfDate` and
  `weekOfYear` lowered to SQL Server. **Quarter and week are real.**
- The **accounting / fiscal period** (`účetní období`, formatted `RRRR.MM`; `fiskální období`) is a
  **table-keyed entity** (QUCTOBD), *not* a calc-derived value. In MD that is a **`kind: bound`
  domain + `md2db_domain`**, not a calc map. ⇒ **the fiscal `calc` family is NOT needed in v1.**

| Tier | Entries | Basis |
|---|---|---|
| **Ship in v1 (~11)** | `truncToDay`, `truncToMonth`, `truncToQuarter`, `truncToYear`, `monthOfDate`, `quarterOfDate`, `yearOfDate`, `quarterOfMonth`, `dayOfMonth`, `weekOfYear`, `truncToWeek` | day/month/quarter/year + week all evidenced in `ai-models` |
| **Deferred — add when a model needs it** | sub-day (`truncToSecond/Minute/Hour`, `secondOfMinute`, `minuteOfHour`, `hourOfDay`), `dayOfWeek`, `halfOfQuarter`, `monthOfWeek`, `quarterOfWeek` | no usage found; additive later (minor bump) |
| **Deferred — fiscal is a *bound dimension*, not calc** | `fiscalYearOfDate`, `fiscalQuarterOfDate`, `fiscalMonthOfDate`, `fiscalQuarterOfMonth` | real fiscal/accounting periods come from a table → `kind: bound` + `md2db_domain`; revisit only if a model needs a *calc-derived* fiscal offset |

**Week scheme default:** ISO-8601 (Monday-start) — the EU/Czech convention. Note for the
ai-platform lowering: SQL Server `DATEPART(week, …)` is `DATEFIRST`/locale-dependent, so the
`weekOfYear`/`truncToWeek` lowering must pin ISO (`DATEPART(ISO_WEEK, …)` / explicit `SET
DATEFIRST 1`) to match the abstract semantics — the in-repo yaml expressions use the bare
`DATEPART(week, …)` form, which is **not** ISO by default.

## 3. Authoring shape (how the catalog is referenced)

```
schema md

def domain Timestamp { type: timestamp }
def domain Day       { type: date }
def domain Month     { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Quarter   { type: int, kind: calc, restrict: { range: 1..4 } }
def domain Year      { type: int, kind: calc }

# truncation — no params
def map ts_to_day    { from: md.Timestamp, to: md.Day,     calc: truncToDay }

# extraction
def map day_to_month { from: md.Day,       to: md.Month,   calc: monthOfDate }

# roll-up
def map month_to_qtr { from: md.Month,     to: md.Quarter, calc: quarterOfMonth }

# parameterised
def map day_to_fy    { from: md.Day, to: md.FiscalYear, calc: fiscalYearOfDate(fiscalYearStartMonth: 4) }
```

Parameters use **named arguments** (`calc: <name>(<param>: <value>, …)`) via the dedicated
`calcRef` rule (grammar sketch §5.4) — decided 2026-06-25, so optional/defaulted params are
order-independent. A map with **no** `calc:` and no inline cases is table-backed (its case-table
arrives via `md2db_map`).

---

## 4. Package & validation contract (`@modeler/md-catalog`)

The catalog lives in a **new workspace package `@modeler/md-catalog`** (decision 2026-06-25),
mirroring `@modeler/grammar`: a small package with no runtime logic beyond the data + a typed
accessor, vendored to `ai-platform` for lowering. Shape:

```ts
// @modeler/md-catalog
export type TimeShape = 'instant' | 'date' | 'instant|date';
export type IntShape  = { kind: 'int'; lo?: number; hi?: number };
export type CatalogShape = TimeShape | IntShape;

export interface CatalogParam { name: string; type: IntShape | 'enum'; values?: string[]; default?: string | number; }
export interface CatalogEntry {
  name: string;
  category: 'truncation' | 'extraction' | 'rollup' | 'fiscal';
  params: CatalogParam[];
  input: CatalogShape;
  output: CatalogShape;
  cardinality: 'N:1';
  semantics: string;
}
export const MD_CALC_CATALOG: ReadonlyMap<string, CatalogEntry>;
export const MD_CATALOG_VERSION: string;   // semver; cross-repo sync key
```

`@modeler/semantics` consumes it exactly as it pre-loads the stock CNC vocab:

1. **Resolution** — a `calc: <name>` that isn't in the catalog ⇒ `md/unknown-calc-map`.
2. **Arity / params** — unknown param name, missing required param, or out-of-range value ⇒
   `md/bad-calc-args`.
3. **Type check** — the `from` domain's type must satisfy `entry.input`; the `to` domain's type
   must satisfy `entry.output` (range included where the entry constrains it) ⇒
   `md/calc-type-mismatch`.
4. **Cardinality** — a `calc:` map is implicitly `N:1`; an explicit conflicting `cardinality:` ⇒
   `md/calc-cardinality-conflict`.

`MD_CATALOG_VERSION` is the **sync key** between repos: ai-platform pins the catalog version it has
lowerings for; a model referencing a newer entry than the runtime supports is a deploy-time
mismatch ai-platform reports (not a modeler concern). Versioning/sync mechanics mirror the
grammar-version story and are detailed in [`contracts.md`](contracts.md) §"catalog package".

---

## 4bis. Lowering caveats (ai-platform contract)

The catalog ships **abstract** semantics; ai-platform owns the per-dialect SQL. These are the
known places where a naive lowering diverges from the abstract meaning — flagged here so they're
not lost when the lowerings are written. ai-platform should encode them per `(name, dialect)`.

| Entry | Caveat | Correct lowering intent |
|---|---|---|
| `weekOfYear`, `truncToWeek` | The abstract semantics is **ISO-8601** (Mon-start, ISO week). SQL Server's bare `DATEPART(week, …)` is **`DATEFIRST`/locale-dependent** and is **not** ISO. The in-repo `ai-models` yaml filters (`tento_týden`, `čtvrtletí`) use the bare `DATEPART(week, …)` form, so a literal copy would silently disagree at year boundaries. | SQL Server: `DATEPART(ISO_WEEK, …)` (and `SET DATEFIRST 1` where a week-start is implied). Postgres: `EXTRACT(WEEK …)` is already ISO. Teradata: use the ISO week function. |
| `yearOfDate` paired with `weekOfYear` | ISO **week-year** ≠ calendar year near Jan 1 / Dec 31 (an ISO week can belong to the adjacent year). | When labelling weeks, pair `weekOfYear` with the **ISO week-year**, not `yearOfDate`, in the lowering — or document that the model must use a week-year domain. |
| `truncTo*` | Abstract truncation is **wall-clock on the stored instant** (no timezone shift, design/​§5 below). | Lower without an implicit `AT TIME ZONE`; a `tz:` param is a future additive entry, not v1. |

## 5. Explicitly out of v1

- **Non-time calc maps** (e.g. `plate → county`, `zip → region`) — need custom logic ⇒ scalar
  expression tier (design §11). v1 time maps are the only built-ins.
- **Custom / project-declared functions** — the declaration-only escape hatch is v1.1 (design §9).
- **Timezone-aware truncation** — v1 truncation is wall-clock on the stored instant; a `tz:`
  parameter is a candidate additive entry-param for a later catalog version.
- **Inverse (drill-down) calc** — coarsening is N:1; the inverse is not a function (design §6.3).

---

## 6. Open / decided questions for the catalog

1. ~~**Param surface**~~ — **DECIDED (2026-06-25):** named args via the dedicated `calcRef` rule
   (`calc: truncToWeek(weekStart: mon)`), grammar sketch §5.4.
2. ~~**`weekStart` / `scheme` defaults**~~ — **DECIDED:** ISO-8601 (Mon-start), the EU/Czech
   convention (§2.5). Lowering caveat recorded there (SQL Server `DATEPART(week)` is not ISO by
   default).
3. ~~**Catalog v1 floor**~~ — **DECIDED (2026-06-25):** ship the **certain core** now; finalize the
   week/fiscal tier against real `ai-models` usage. See §2.5 (the floor); the full set in §2 is the
   *menu*, not the v1 ship list.
4. ~~**Half-year levels** (`halfOfQuarter`)~~ — **DECIDED:** deferred (in the §2.5 deferred tier).
