# Stage 1A — `@modeler/md-catalog` package

Goal: create the new data-only workspace package that holds the built-in calc-map catalog (the v1
Time entries) as typed data, plus a stable version string. This is the cross-repo contract home
(decision 2026-06-25) mirroring `@modeler/grammar`. Consumed by `@modeler/semantics` in Phase 2 and
vendored to ai-platform in Phase 4.

Prereq: clean tree, gates green. TDD: 1A2 (tests) before 1A3–1A4. No grammar/parser changes here.

References (verified):
- Contract shapes: [`../../map-catalog.md`](../../map-catalog.md) §4 and
  [`../../contracts.md`](../../contracts.md) §8 (kept in lockstep — if you change a type, change both).
- Package conventions: `CLAUDE.md` → "Conventions" (`@modeler/<name>`, extend `tsconfig.base.json`,
  ESM `"type": "module"`, `.js` import extensions, `src/` → `dist/`).
- Sibling leaf package to copy structure from: `packages/grammar/` (`package.json`, `tsconfig.json`,
  `src/index.ts`). It has no runtime deps — the model to follow.
- Catalog entries to seed: [`../../map-catalog.md`](../../map-catalog.md) §2 (four families:
  truncation, extraction, rollup, fiscal).

---

- [ ] **1A1 — Scaffold the package.**
  - Create `packages/md-catalog/` with `package.json` (`"name": "@modeler/md-catalog"`,
    `"private": true`, `"type": "module"`, `version` = catalog semver `0.1.0`, `main`/`types`/
    `exports` mirroring `packages/grammar/package.json`, scripts `build`/`typecheck`/`lint`/`test`).
  - `tsconfig.json` extending `../../tsconfig.base.json` (`src/` → `dist/`).
  - `pnpm-workspace.yaml` already globs `packages/*` — no edit needed; run `pnpm install` to link.

- [ ] **1A2 — Tests first (red).** `packages/md-catalog/src/__tests__/catalog.test.ts` (Vitest):
  - Every entry has a unique `name`; `MD_CALC_CATALOG.size` equals the count seeded.
  - Each entry has a valid `category`, an `input`/`output` shape, `cardinality === 'N:1'`, and every
    declared param has a `type` (and a `default` where the catalog doc marks one).
  - `MD_CATALOG_VERSION` matches a semver regex.
  - Spot-check three known entries by name (`truncToDay`, `monthOfDate`, `quarterOfMonth`) for the
    exact `input`/`output` shapes from [`../../map-catalog.md`](../../map-catalog.md) §2.
  - Run; confirm red (module not yet implemented).

- [ ] **1A3 — Types.** `packages/md-catalog/src/types.ts`:
  - `TimeShape`, `IntShape`, `CatalogShape`, `CatalogParam`, `CatalogEntry` exactly per
    [`../../map-catalog.md`](../../map-catalog.md) §4. No `any` (ESLint forbids it outside
    `generated/**`).

- [ ] **1A4 — Catalog data.** `packages/md-catalog/src/catalog.ts`:
  - Build `MD_CALC_CATALOG: ReadonlyMap<string, CatalogEntry>` from the four families in
    [`../../map-catalog.md`](../../map-catalog.md) §2 (truncation, extraction, rollup, fiscal),
    including params (`weekStart`, `scheme`, `fiscalYearStartMonth`) with defaults.
  - `index.ts` re-exports the types, `MD_CALC_CATALOG`, and `MD_CATALOG_VERSION = '0.1.0'`.

- [ ] **1A5 — Verify.**
  - `pnpm --filter @modeler/md-catalog test && pnpm --filter @modeler/md-catalog typecheck && pnpm --filter @modeler/md-catalog lint && pnpm --filter @modeler/md-catalog build`
  - `pnpm -r build` still green (the new package builds in the workspace).

- [ ] **1A6 — Commit.** `Section MD-1A: add @modeler/md-catalog package`.
  - Note: do **not** wire it into `@modeler/semantics` yet — that is Stage 2A.
