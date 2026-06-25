# Stage 4D — Manual chapter + CHANGELOG

Goal: document the MD model + binding in the user manual (mirroring the ER/CNC/mapping chapters) and
record the additive 3.1 grammar surface in the changelog. Last stage of v1.

Prereq: Phases 1–3 DONE; the surface is stable. TDD: n/a (docs); verify by example-compilation.

References (verified):
- Manual: `docs/manual/en/` — existing chapters `07-er-schema.md`, `08-mapping.md`,
  `09-cnc-roles.md`, `14-reference.md`, `_Sidebar.md`, `Home.md`. Worked example project:
  `docs/manual/en/examples/retail/`.
- Source of truth for content: [`../../design.md`](../../design.md), [`../../contracts.md`](../../contracts.md),
  [`../../map-catalog.md`](../../map-catalog.md).
- Changelog: root `CHANGELOG.md`; grammar header `Changes in 3.1` block (from Stage 1B).

---

- [ ] **4D1 — Manual chapter.** Add a new chapter (e.g. `15-md-model.md`, or slot after
  `09-cnc-roles.md` and renumber if the sidebar convention requires): the seven logical objects,
  the calc-map catalog (link the Time table), hierarchies, measures/additivity, and cubelets.
  Use small runnable `.ttrm` snippets.

- [ ] **4D2 — Binding section.** Document `schema binding` for MD: `md2db_cubelet` (wide/long),
  journaling, multi-source, `md2db_domain`, `md2db_map`, and the thin structural `md2er_cubelet`.
  Cross-link to the existing `08-mapping.md` (er2db) so authors see the unified binding schema.

- [ ] **4D3 — Reference + sidebar.** Add the MD keywords/defs and the `md/*` diagnostic codes to
  `14-reference.md`; add the new chapter to `_Sidebar.md` and `Home.md`.

- [ ] **4D4 — Worked example.** Extend (or add beside) the `examples/retail/` project with a small
  MD model + binding so the manual's examples compile clean against the shipped tooling.

- [ ] **4D5 — CHANGELOG.** Document the additive **3.1** surface in `CHANGELOG.md`: new `md` schema,
  the six logical def kinds, the `md2db_*`/`md2er_cubelet` binding kinds, the `@modeler/md-catalog`
  package, and the calc-map catalog v1. Note it is additive (no breaking change vs 3.0).

- [ ] **4D6 — Verify (v1 DONE).**
  - Manual `.ttrm` snippets and the worked example parse + validate clean against the built tooling.
  - `pnpm -r test` green; conformance green; ai-platform loads an MD fixture clean (4B).
  - Phase 4 INDEX DoD all checked.

- [ ] **4D7 — Commit.** `Section MD-4D: MD manual chapter + CHANGELOG`.
