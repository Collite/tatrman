# RG-P2 phase-exit review — `ttr-fuzzy` the vocabulary matcher

> Verifies the RG-P2 Definition of DONE ([`../plan.md`](../plan.md) §RG-P2)
> against **runtime**, not progress-doc marks. Performed 2026-07-12 after S1+S2
> committed (tatrman-server branch `rg-p2-fuzzy`: `55c8fe4`, `391840f`, + the
> gRPC smoke). Method: full suite (**149 green**, ktlint clean) + an **in-process
> gRPC smoke** (`GrpcServiceSmokeTest`) driving `BatchMatch`/`GetStatus` over a
> real channel + the end-to-end `HeroResolutionTest` (loader → repo → lemma axis
> → matcher → BatchMatch with real components; only the loader + lemmatiser are
> fixtures, unavoidable offline). `fuzzy-mcp` + `fuzzy-common` consumers build
> clean.

## DoD verification

| DoD clause | Verdict | Evidence |
|---|---|---|
| The hero's `Octavie` (MEMBER) + `pobočkách` (VOCABULARY) both gate through one `BatchMatch`, source-tagged | **✅** | `HeroResolutionTest` (end-to-end) + `GrpcServiceSmokeTest` (over the wire): source/target_ref reach the client. |
| The lemma axis bridges inflection against the Q-17 referee corpus (diacritics/inflection/multi-word/typos) | **✅** | `MatchQualityCorpusTest` (40 cases green) + `MatchQualityAxisProofTest` (axis-on = exact matches; off = strictly lower). |
| Explicit-unknown category returns EMPTY (`RG-FUZ-002` leak guard) | **✅** | `BatchMatchTest` (per-slot EMPTY) + the pre-existing `CategoryCaseInsensitivityTest` (unchanged — GI-2). |
| `vocabulary_version` reflects the snapshot hash | **✅** | `RefreshDisciplineTest` (version = content sig + declared snapshot hash + load stamp; changes with the hash). |
| A `/refresh` without admin identity is refused | **✅** | `AdminGateTest` (isAdminAuthorized) + `/refresh` moved behind `adminOnly` (was unauthenticated — the S-3 fix). |
| The loader report lists PK-skipped declared columns (`RG-FUZ-001`) | **✅** | `HeroResolutionTest` (report + status plumbing) + `MetadataLoaderSource` collects RG-FUZ-001 per no_pk/composite_pk skip → GetStatus. |

## Notes

- **PINNED contract preserved (GI-2).** Everything is additive over
  `fuzzy.match:v1`: `Match` unchanged; `FuzzyMatch` gained source/target_ref/
  provenance; `BatchMatch`/`GetStatus` are new rpcs. The full pre-existing suite
  stays byte-identical (cascade / minScore / diacritics / EMPTY-on-unknown).
- **Behavior change (intended, S-3).** `/refresh` was deliberately open ("DF
  decision: no auth"); S2.T6 reverses this — it now requires admin authority.
  **Downstream:** consumers that call `/refresh` (e.g. Golem) must present an
  `admin` role or admin API key. Flagged for the consumer.
- **Axis proof shape.** The corpus inflections are surface near-misses that
  surface fuzzy also ranks top-1, so the proof is *score quality* (exact-match
  count + total score), not a top-1 flip — still a load-bearing regression guard.

## Stubbed couplings (rule 6 — carried, not gaps)

- **G1 — declared-vocabulary source (RO-13 / RG-P4).** `SnapshotVocabularySource`
  is the seam; a live-metadata step-one adapter + the real snapshot-archive
  reader implement the same interface later. The load path + two-clock refresh
  are live and tested against the fixture stub.
- **G2 — alias-table reporting (RG-P4).** `composeAliasCandidates` (compose +
  merge, SQL identifier-validation) is live and tested; only the *source* of the
  declarations is stubbed — Veles (`org.tatrman.meta.v1`) has no `semantics`
  surface yet, so `aliasTables()` returns empty until the RG-P4 metadata work.

## Verdict

RG-P2 is **code-complete and runtime-verified**: BatchMatch, source tags,
provenance, the lemma axis, the two-clock refresh, the admin gate, and the
loader report all behave correctly end-to-end (including over the gRPC wire). No
defect surfaced. Two forward couplings (G1/G2) are stubbed at clean seams and
land with RG-P4. Recommend proceeding to the next phase (RG-P3 grounding is
independent and can overlap; RG-P5 resolver consumes B's `BatchMatch`).
