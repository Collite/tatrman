# 1.1.G — Migrate samples + docs cleanup

**Goal:** every sample is on the v1.1 model; `docs/v1/design/architecture.md` is updated to reflect v1.1's shape; `docs/v1-1/design/grammar-v1-1-changes.md` is finalised based on what shipped in A; `progress-phase-v1.1.md` is written.

**Reads:** all v1 samples; [`v1-1-contracts.md`](../../design/v1-1-contracts.md); [`grammar-v1-1-changes.md`](../../design/grammar-v1-1-changes.md); [`v1.1-packages-and-graphs.md`](../../design/v1.1-packages-and-graphs.md); v1's `docs/v1/design/architecture.md`.
**Blocked by:** 1.1.F (uses the CLI to migrate).
**Blocks:** v1.1 release.
**Estimated time:** 2–3 days.

## Tests-first

- [ ] After migration, `pnpm --filter @modeler/parser test` includes a "samples parse cleanly" fixture run against every file under `samples/v1.1-*/` — 0 errors.
- [ ] `tests/integration/src/v1.1-samples.test.ts` — new file. Opens each `_all_<schema>.ttrg` from the migrated samples via `client.getGraph`; asserts `missingObjects === []` and `nodes.length > 0`.

## Implementation tasks

- [ ] **G.1 — Run migration on `samples/v1-mini/` → `samples/v1.1-mini/`.** Use `pnpm exec modeler-migrate` from sub-phase F. Inspect the diff; commit the migrated output as a new directory (keep the v1 originals as input fixtures for the migration test).
- [ ] **G.2 — Run migration on `samples/v1-metadata/` → `samples/v1.1-metadata/`.** Same as G.1 but the larger sample. Expect more imports per file; review the output for sane `import` grouping.
- [ ] **G.3 — Hand-author additional `.ttrg` fixtures.** Add 2–3 hand-authored `.ttrg` files under each migrated sample exercising non-trivial scopes (subdomain views, focused single-entity views). These are the demo content for the "Open existing graph" entry mode.
- [ ] **G.4 — Update `samples/builtin/cnc-stock-roles.ttr`.** Per the open-question #10 resolution (accept doubled `cnc.cnc.role.*` for v1.1), this file gets `package cnc` prepended. Add a `// TODO: revisit when conceptual model lands (v2.x)` comment near the package declaration.
- [ ] **G.5 — Finalise `docs/v1-1/design/grammar-v1-1-changes.md`.** Re-check every `TTR.g4` diff fragment against what actually shipped in 1.1.A. Update the §3.5 unified-diff block to match the real grammar. Update §8 open items: mark resolved any that were settled during the build.
- [ ] **G.6 — Update `docs/v1/design/architecture.md`.** Five sub-updates:
  - §4.3 stock vocabulary: reference the new package shape and the doubled-cnc decision
  - §4.4 edit synthesizer: note that `WorkspaceEdit` synthesis is now load-bearing (no longer a v1 placeholder) for rename + graph mutations
  - §5 project model: reference packages and classpath root
  - §6 layout sidecar (`.ttrl`): replace the section with a one-line pointer to the v1.1 design doc and note that `.ttrl` is removed
  - §10 open questions: mark any closed by v1.1 work
  - §11 Designer ↔ LSP: update to reflect graph-centric flow (one `.ttrg` at a time, not project-wide)
- [ ] **G.7 — Write `docs/v1-1/plan/progress-phase-v1.1.md`.** Mirror the structure of `docs/v1/plan/progress-phase-03.md`. One section per sub-phase (A–I); checkboxes per task from the corresponding mini-task-list; one-line completion notes; test totals at the bottom.
- [ ] **G.8 — Update root `README.md` and `CLAUDE.md` for v1.1.** README: add a "v1.1 — shipped" section listing the new file kinds, the package model, the migration CLI. CLAUDE.md: update the "key invariants" section if any v1 invariants changed (e.g., the layout sidecar invariant becomes per-graph).

## Verify by running

```bash
pnpm -r build
pnpm -r test
pnpm -r typecheck
pnpm -r lint
```

All green. Open one of the migrated samples in VS Code (manual smoke) — every file has its `package` declaration, every cross-reference resolves, every `.ttrg` opens in the Designer.

## DONE when

- [ ] Every checkbox above is ticked.
- [ ] `samples/v1.1-mini/` and `samples/v1.1-metadata/` exist, parse cleanly, all references resolve.
- [ ] `progress-phase-v1.1.md` accurately reflects what shipped.
- [ ] `grammar-v1-1-changes.md` is sign-off-ready for ai-platform.
- [ ] `docs/v1/design/architecture.md` reflects v1.1's shape with no stale `.ttrl` references.
