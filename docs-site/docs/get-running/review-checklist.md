<!-- SPDX-License-Identifier: Apache-2.0 -->
# How-to: walk the import review checklist

> **Stub (SV-P4·S4·T7).** The checklist artifacts are built and golden-tested; S6 adds a fully
> worked walkthrough with screenshots.

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

_TODO(S6): worked example with a real checklist and the accept/correct flow in the IDE._
