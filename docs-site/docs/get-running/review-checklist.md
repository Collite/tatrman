<!-- SPDX-License-Identifier: Apache-2.0 -->
# How-to: walk the import review checklist

`ttr import-schema` writes two review artifacts beside your model: `import-review.md` (read this)
and `import-review.json` (its machine twin — one documented schema, for tooling and CI).

Walk the markdown top to bottom:

1. **Relations (evidence grades).** Each derived relation shows its grade and the rule that found
   it. Trust `DECLARED` and `VERIFIED_FULL`; scrutinise `VERIFIED_SAMPLED` (orphan count is an
   estimate) and `NAMED_ONLY` (unconfirmed — decide whether to keep it).
2. **Junctions collapsed.** Pure many-to-many join tables became direct relations. Confirm each is
   really payload-free.
3. **Folds proposed.** Header/detail folds are suggestions only — accept the ones that match how
   you think about the data.
4. **Codebooks proposed.** `Ciselnik*`-style lookup tables, offered as enum-like entities.
5. **Contradictions.** A name suggested a relation but the data disagreed — these were kept **out**
   of the model. Investigate the orphans if the relation should exist.
6. **Unmatched columns / renamed identifiers.** Columns that look like foreign keys but resolve
   nowhere, and any identifier the tool had to rewrite to a legal TTR name (the original is here).
7. **Coverage (Q-5).** How much of the estate was introspected and probed — and, if a budget was
   hit, exactly which candidates were left unprobed.

Every item is a decision. Accept or correct it, then commit the model and the checklist together —
they ride the same pull request.

## A worked example

Import the seven-table Czech ERP from the [quickstart](quickstart.md) and open its
`import-review.md`. Here is the real checklist, section by section, with the decision each item
asks of you.

**Relations.** Six relations, each graded:

```markdown
- `Artikl_Odberatel` Artikl → Odberatel — DECLARED (junction:public.Artikl_Odberatel)
- `Faktura_Ciselnik_StavFaktury` Faktura → Ciselnik_StavFaktury — VERIFIED_FULL (pk-name-match) · FULL · orphans=0
- `Faktura_Odberatel` Faktura → Odberatel — DECLARED (declared:Faktura_IDOdberatel_fkey) · FULL · orphans=0
- `Odberatel_Ciselnik_Stat` Odberatel → Ciselnik_Stat — VERIFIED_FULL (pk-name-match) · FULL · orphans=0
- `PolozkaFaktury_Artikl` PolozkaFaktury → Artikl — VERIFIED_FULL (pk-name-match) · FULL · orphans=0
- `PolozkaFaktury_Faktura` PolozkaFaktury → Faktura — DECLARED (declared:PolozkaFaktury_IDFaktura_fkey)
```

The `DECLARED` rows came from real foreign keys in the database — accept them. The two
`VERIFIED_FULL (pk-name-match)` rows are more interesting: the database declared **no** foreign key
for `Faktura → Ciselnik_StavFaktury`, but the importer saw `Faktura.IDStav` line up with
`Ciselnik_StavFaktury`'s key, *probed every row*, and found `orphans=0` — no `IDStav` that fails to
resolve. The evidence is as strong as a declared key; accept it, and consider adding the missing FK
to your database.

**Junctions collapsed.**

```markdown
- public.Artikl_Odberatel — pure M:N junction collapsed into relation Artikl ↔ Odberatel
```

`Artikl_Odberatel` had nothing but the two foreign keys, so it is not really an entity — it is a
many-to-many *relationship*. The importer collapsed it. Confirm the table truly carries no payload
columns of its own (this one doesn't).

**Folds proposed** (suggestions, never applied):

```markdown
- PolozkaFaktury/Faktura — possible header/detail fold (PolozkaFaktury is detail of Faktura) — PROPOSED only
```

`PolozkaFaktury` (invoice *lines*) reads as the detail side of `Faktura` (invoice *headers*). If you
model line-items as part of the invoice, accept the fold; if they are a first-class entity, leave
it. Your call — the importer will not decide it for you.

**Codebooks, unmatched columns, renamed identifiers:**

```markdown
## Codebooks proposed (enum-like)
- public.Ciselnik_Stat — codebook table — proposed as an enum-like entity
- public.Ciselnik_StavFaktury — codebook table — proposed as an enum-like entity

## Unmatched columns (look like FKs, resolve to no table)
- public.Artikl(IDKategorie) — looks like a foreign key but resolves to no table — left as a plain attribute

## Renamed identifiers (original ← TTR name)
- `Sleva_` ← `Sleva %` (COLUMN public.Faktura)
```

`IDKategorie` *looks* like a foreign key but points at no table the importer could find — maybe the
category table lives elsewhere, or the column is dead. It was left as a plain attribute for you to
resolve. And `Sleva %` — a percent sign is illegal in a TTR identifier — was rewritten to `Sleva_`,
with the original recorded here so the rename is never silent. The model text stays clean; the
provenance lives in the checklist.

Nothing in this list changed your data or hid a decision. That is the contract: the importer shows
its work, grades its confidence, and leaves the judgement to you.
