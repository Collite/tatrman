# Phase 4 — Conformance, cross-repo, fixtures & docs (management doc)

Status: **ready after Phases 1–3** · Final v1 phase · Owner: editor-tooling

Phase 4 proves cross-target parity (TS ↔ Kotlin ↔ Python), gets ai-platform consuming grammar 3.1 +
the catalog, validates real RAE-derived models end-to-end, and documents MD in the manual. See
[`../implementation-plan.md`](../implementation-plan.md) §"Phase 4".

## Pre-flight

- Phases 1–3 DONE.
- **Phase 0 fully closed** (Stages D/E — the 3.0 grammar release + ai-models migration), since 3.1
  publishes on top of 3.0.
- Gates green.

## Stages (TDD-ordered mini-task-lists)

- [ ] **Stage 4A** — [Cross-target conformance (Kotlin + Python)](4A-conformance.md) — **deferred (cross-repo / cross-target).** Needs the Kotlin `ttr-parser` (and Python reference) ported for the MD constructs and the `conformance.yml` corpus extended. Gated on Phase 0 Stage D shipping the 3.0 grammar release first (3.1 publishes on top of 3.0). Adding MD conformance fixtures before the Kotlin port lands would break the TS↔Kotlin byte-diff gate, so they are intentionally **not** added yet.
- [ ] **Stage 4B** — [Catalog vendoring + ai-platform sync + 3.1 publish](4B-catalog-and-publish.md) — **deferred (cross-repo).** Vendoring `@modeler/md-catalog` into ai-platform and tagging the 3.1 Maven/PyPI release are gated on 4A green and the 3.0 release. The catalog package + `MD_CATALOG_VERSION` sync key are ready in-repo (Phase 1A).
- [x] **Stage 4C** — [RAE end-to-end fixtures](4C-rae-fixtures.md) — done in-repo (`packages/lint/src/__tests__/md-rae.test.ts`: four clean RAE-derived models + seeded variants through the full logical+binding validator set).
- [x] **Stage 4D** — [Manual chapter + CHANGELOG](4D-manual-changelog.md) — done (`docs/manual/en/15-md-model.md`, sidebar, `14-reference.md` MD codes, `CHANGELOG.md` 3.1 entry).

## Sequencing

**4A** first (parity is the gate everything else trusts). **4B** publishes once parity holds. **4C**
can proceed in parallel with 4B (in-repo fixtures). **4D** last (documents the shipped surface).

## Working rules

- Conformance is verification-driven: the cross-target AST/semantic **diff** is the gate, not new
  unit tests. Re-run the Phase 2–3 integration fixtures against the real RAE-derived models.
- Do not tag the 3.1 release until 4A is green and Phase 0 Stage D has shipped 3.0.

## Definition of DONE for Phase 4 (and v1)

1. Conformance harness green across TS + Kotlin (+ Python) on the MD corpus.
2. ai-platform builds against grammar 3.1 and the vendored `@modeler/md-catalog`; its metadata
   service loads an MD fixture model with zero errors.
3. RAE end-to-end fixtures (`costCenterTransactions`, `otherDrivers` long, `costCenterM2`
   map-mediated) validate clean.
4. Manual MD chapter merged; `CHANGELOG.md` documents the additive 3.1 surface; 3.1 published.
