# Feature: `semantics { }` block — grammar 4.2

TTR-M language-surface change enabling **deterministic grounding** in ai-platform
(time / geography / money semantic roles on model elements). Approved in the
grounding master-plan session, 2026-07-06 (see ai-platform repo root:
`feature-grounding-{architecture,contracts,plan}.md`; this feature is its Phase 1).
This README plus the six task lists below are the whole tatrman-side plan.

> **Branching:** work in a **separate worktree off `master`** (TTR-P development
> is in flight on `feature/ttr-p-v1-phase2`). Claim grammar **4.2** in the
> CHANGELOG in the first commit. Hard deadline: merged + published before TTR-P
> Phase 5 Stage 5.2 (`.ttrl` grammar lands in ttr-parser there).

## Goal

1. Add a `semantics { … }` block property, attachable to `entity`, `attribute`,
   `table`, `column` (narrower than `search` — widening later is additive).
2. Body is **free-form `object_`** — one new lexer token (`SEMANTICS`), one rule;
   ALL shape/vocabulary checking lives in semantics ("parser stays mechanical").
   New roles therefore need **no future grammar bump**.
3. Surface the validated result on `@tatrman/semantics`, Kotlin `ttr-semantics`,
   and `ttr-metadata`'s typed model; publish; mirror the new catalog functions
   in TTR-P's `BuiltinCatalog`.

### New surface (illustration)

```ttr
def entity AccountingPeriod {
  semantics { kind: period_table },
  attributes: [
    def attribute start_date { type: date,       semantics { role: period_start } },
    def attribute end_date   { type: date,       semantics { role: period_end } },
    def attribute period     { type: varchar(6), semantics { role: period_code, code_format: "yyyyMM" } }
  ]
}

def entity Transaction {
  attributes: [
    def attribute date       { type: date,    semantics { role: event_date, period: AccountingPeriod } },
    def attribute doc_date   { type: date,    semantics { role: document_date } },
    def attribute due        { type: date,    semantics { role: due_date } },
    def attribute amount     { type: decimal, semantics { role: amount, currency: currency_code } },
    def attribute amount_dom { type: decimal, semantics { role: amount_domestic } },
    def attribute currency_code { type: varchar(3), semantics { role: currency_code } }
  ]
}

def entity Poi {
  semantics { kind: poi },
  attributes: [
    def attribute lat { type: decimal, semantics { role: geo_lat } },
    def attribute lon { type: decimal, semantics { role: geo_lon } }
  ]
}
```

## Decisions (from the 2026-07-06 planning session)

| Decision | Choice |
|---|---|
| Body style | Free-form `object_` (world-model 4.1 / `attributesMapProperty` precedent); NOT search-style typed sub-properties. Vocabulary evolves without grammar bumps. |
| Primitive | **Attribute-level `role` is the primitive**; entity/table level carries only `kind` (+ future kind params). No entity-level attribute wiring (single source of truth). |
| Attachment set | `entity`, `attribute`, `table`, `column` only (db-only period/POI tables supported). `relation`/`query`/`role`/project: NOT attachable in 4.2. |
| Project-level defaults | **Deferred.** Fiscal alignment is derivable: package declares a `period_table` ⇒ table-backed; otherwise calendar-aligned (formats from GroundingContext). |
| Keyword hygiene | `SEMANTICS` added to `idPart` (4.1 `WORLD` precedent) — 4.2 stays honestly additive. |
| Unknown keys / bad values | **Error** (closed vocabulary; evolution goes through ttr-semantics + proto releases anyway). Duplicate keys: error via the `duplicateProperties` walker pattern (search-block precedent). |
| MD coexistence | `semantics` is orthogonal to v3.1 `domain_ref`/`aggregation`; both may appear on one attribute; `semantics` NEVER implies aggregation. |

## Vocabulary (4.2 / ttr-semantics v1)

**Entity/table kinds** (`kind:`): `period_table`, `calendar`, `poi`, `fx_rate`.
(`calendar` = pre-materialized date dimension; not present at DF but supported.)

**Attribute/column roles** (`role:`):

| Role | Extra keys | Type constraint | Notes |
|---|---|---|---|
| `period_start` / `period_end` | — | date/datetime | on `period_table` kinds |
| `period_code` | `code_format` (string, default `"yyyyMM"`) | text | |
| `event_date` | `period:` → entity ref (kind `period_table`) | date/datetime | **≤ 1 per entity** — THE default query date |
| `document_date` / `posting_date` / `due_date` | optional `period:` | date/datetime | secondary dates, NL-targetable ("posted in May", "due in May") |
| `valid_from` / `valid_to` | — | date/datetime | generic validity pair (both or neither); reused on fx tables |
| `calendar_date` | — | date | the day key of a `calendar` kind |
| `geo_lat` / `geo_lon` | — | numeric | pair required together |
| `geo_point` | — | text/geometry | XOR with lat/lon pair |
| `amount` | `currency:` → sibling attribute ref (role `currency_code`) | numeric | |
| `amount_domestic` | — | numeric | grounding's no-FX-join shortcut |
| `currency_code` | — | text | |
| `fx_from_currency` / `fx_to_currency` | — | text | on `fx_rate` kinds |
| `fx_rate` | — | numeric | on `fx_rate` kinds |

**Kind completeness rules** (validated on the owning entity/table):

- `period_table` ⇒ exactly one `period_start`, one `period_end`, one `period_code` among its attributes/columns.
- `calendar` ⇒ exactly one `calendar_date`.
- `poi` ⇒ exactly one `geo_point` XOR (exactly one `geo_lat` AND one `geo_lon`).
- `fx_rate` ⇒ exactly one each of `fx_from_currency`, `fx_to_currency`, `fx_rate`; `valid_from`/`valid_to` optional as a pair.

**Cross-ref resolution:** `period:` resolves like entity refs (binding machinery);
`currency:` resolves like `name_attribute:` (sibling-attribute ref). Diagnostics
when the target is missing, or lacks the required kind/role.

**Diagnostic codes:** `TTR-SEM-2xx` range (200 unknown key, 201 unknown role,
202 unknown kind, 203 duplicate key, 204 kind on attribute / role on entity,
205 type-constraint violation, 206 completeness violation, 207 multiple
`event_date`, 208 dangling/miskinded `period:` ref, 209 dangling/roleless
`currency:` ref, 210 geo pair violation, 211 valid pair violation). Each carries
a suggested alternative where meaningful (closed-vocabulary nearest match).

## Consumer contract (downstream, for reference)

`ttr-metadata` exposes the validated result on the typed model; ai-platform's
Ariadne maps it to `cz.dfpartner.metadata.v1` `AttributeSemantics` /
`EntitySemantics` (closed proto enums mirroring the tables above — see
ai-platform `feature-grounding-contracts.md` §4). The vocabulary here and the
proto enums version **together**.

## Task lists

Execute in order; each ≤8 checkboxed steps; tick each box right after the task
completes. Tests are written **before** implementation within each list (TDD).

1. [T1 — Grammar 4.2 + regeneration](./T1-grammar.md)
2. [T2 — TS parser AST + walker](./T2-parser-ast-walker.md)
3. [T3 — TS semantics validation](./T3-semantics-validation.md)
4. [T4 — Kotlin parity (parser / writer / semantics)](./T4-kotlin-parity.md)
5. [T5 — ttr-metadata typed-model surface + publish](./T5-ttr-metadata.md)
6. [T6 — TTR-P BuiltinCatalog twin entries](./T6-ttrp-twin.md)
