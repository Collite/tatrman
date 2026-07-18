<!-- SPDX-License-Identifier: Apache-2.0 -->
# Tutorial: model your first three tables

The fastest way to a model is to let `import-schema` draft it, then shape a few tables by hand so
you learn the vocabulary. We will use three tables from the quickstart's Czech ERP: `Faktura`
(invoices), `Odberatel` (customers), and `PolozkaFaktury` (invoice lines).

## 1. Import

Run `ttr import-schema` against the database (see
[Import a database](../get-running/import-schema.md)). You get `db.public.ttrm`, `er.ttrm` and the
review checklist.

## 2. Read the `db` mirror

`db.public.ttrm` is the machine's faithful picture — exactly what is in the database, nothing
interpreted. Here is `Faktura`:

```ttrm
def table Faktura { primaryKey: ["IDFaktura"],
    columns: [
        def column IDFaktura { type: int, isKey: true, },
        def column CisloFaktury { type: text, },
        def column IDOdberatel { type: int, },
        def column DatumVystaveni { type: date, },
        def column Castka { type: decimal, },
        def column IDStav { type: int, optional: true, },
        def column Poznamka { type: text, optional: true, },
        def column Sleva_ { type: decimal, optional: true, },
    ]
}
```

This layer is machine-owned — `import-schema` re-runs keep it in sync with the database and you do
not hand-edit it. Note `Sleva_`: the source column was `Sleva %`, rewritten to a legal identifier
(the original is in the [review checklist](../get-running/review-checklist.md)).

## 3. Shape the `er` entities

`er.ttrm` is the first cut of *meaning*, and it is **yours** — born once, never overwritten. The
importer gives you a plain mirror-shaped entity:

```ttrm
def entity Faktura {
    attributes: [
        def attribute IDFaktura { type: int, isKey: true, },
        def attribute CisloFaktury { type: text, },
        def attribute IDOdberatel { type: int, },
        def attribute DatumVystaveni { type: date, },
        def attribute Castka { type: decimal, },
        def attribute IDStav { type: int, optional: true, },
        def attribute Poznamka { type: text, optional: true, },
        def attribute Sleva_ { type: decimal, optional: true, },
    ]
}
```

Now make it mean something. Give the entity human labels, and drop the raw foreign-key columns that
the *relations* already express (`IDOdberatel`, `IDStav`) — meaning lives in the relation, not in a
loose integer:

```ttrm
def entity Faktura {
    displayLabel: { cs: "Faktura", en: "Invoice" },
    attributes: [
        def attribute IDFaktura { type: int, isKey: true, },
        def attribute CisloFaktury { type: text, displayLabel: { en: "Invoice number" }, },
        def attribute DatumVystaveni { type: date, displayLabel: { en: "Issued on" }, },
        def attribute Castka { type: decimal, displayLabel: { en: "Amount" }, },
        def attribute Poznamka { type: text, optional: true, },
        def attribute Sleva_ { type: decimal, optional: true, displayLabel: { en: "Discount %" }, },
    ]
}
```

Do the same for `Odberatel` — give it a `displayLabel` of *Customer* and mark its display name with
`nameAttribute: Nazev` — and for `PolozkaFaktury` (*Invoice line*). You are not restructuring the
database; you are teaching the model what the tables *are*.

## 4. Accept the relations

Walk the [review checklist](../get-running/review-checklist.md). Between these three tables it found:

- `Faktura → Odberatel` — **DECLARED** (a real foreign key). Accept.
- `PolozkaFaktury → Faktura` — **DECLARED**. Accept — and consider the proposed **header/detail
  fold** (invoice lines as part of the invoice).

Each accepted relation is what lets an agent answer *"which customer is on invoice F2?"* without
you ever exposing `IDOdberatel`.

## 5. Commit

The `er` model is now yours. Commit it beside the `db` mirror and the checklist — they ride the same
pull request. Re-running `import-schema` later refreshes the `db` layer and proposes new `er`
candidates as checklist items; it never overwrites the meaning you just wrote. That is the
[layered design](layers.md) working for you: the machine keeps the mirror, you keep the meaning.
