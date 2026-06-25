# Stage 4B — Catalog vendoring + ai-platform sync + 3.1 publish

Goal: get ai-platform consuming grammar 3.1 and the vendored `@modeler/md-catalog`, document the
sync/version-pin story, and publish the additive 3.1 grammar release.

Prereq: Stage 4A green (parity holds). Phase 0 Stage D shipped 3.0. TDD: n/a (release/coordination
stage) — verification is "ai-platform builds + loads an MD fixture clean."

References (verified):
- Publishing policy: `PUBLISHING.md`; tag-driven `.github/workflows/publish.yml`.
- Grammar-version + vendoring precedent: `docs/grammar-master/` (how `@modeler/grammar` is vendored
  to ai-platform); `CLAUDE.md` → "Kotlin artifacts" / "Grammar regeneration".
- Catalog contract: [`../../contracts.md`](../../contracts.md) §8; signatures
  [`../../map-catalog.md`](../../map-catalog.md). `MD_CATALOG_VERSION` is the cross-repo sync key.
- Companion repos: ai-platform (`~/Dev/ai-platform`), ai-models (`~/Dev/ai-models`).

---

- [ ] **4B1 — Publish `@modeler/md-catalog`.** Decide the publish channel mirroring `@modeler/grammar`
  (npm package and/or vendored copy). Document in the package README how ai-platform vendors it and
  pins `MD_CATALOG_VERSION`. Record the semver bump rules (contracts §8.2): add entry/param = minor;
  signature change = major.

- [ ] **4B2 — ai-platform sync doc.** Write/extend the coordination note (alongside
  `grammar-md-changes.md`, which is already the ai-platform-facing spec) describing what ai-platform
  must implement: regenerate the Kotlin parser from 3.1 `TTR.g4`, load the new MD defs, and provide
  the **per-dialect lowerings** for every catalog entry (Teradata/SQL Server/Postgres) keyed by
  `(name, dialect)`. ai-platform owns lowerings; modeler owns signatures.

- [ ] **4B3 — Version-mismatch behaviour.** Confirm the contract: a model referencing a catalog
  entry newer than the runtime's pinned `MD_CATALOG_VERSION` is an **ai-platform deploy-time** error,
  **not** a modeler diagnostic (contracts §8.3). Document where ai-platform reports it.

- [ ] **4B4 — Publish grammar 3.1.** Per `PUBLISHING.md`, cut the 3.1 grammar release (additive on
  top of 3.0). Do **not** tag until 4A is green and 3.0 is shipped. Update
  `packages/grammar/package.json` + the `@grammar-version:` marker if not already at 3.1.

- [ ] **4B5 — Verify.**
  - ai-platform builds against grammar 3.1 + the vendored catalog; its metadata service loads an MD
    fixture model (a small `schema md` + `schema binding` project) with **zero** errors.
  - The published artifacts resolve from a clean consumer checkout.

- [ ] **4B6 — Commit.** `Section MD-4B: catalog vendoring + ai-platform sync + 3.1 publish`.
