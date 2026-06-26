# Stage 2A — Symbol table, namespaces & catalog preload

Goal: register the seven MD logical kinds as resolvable symbols in the right namespaces, with
dimension-qualified attributes, and pre-load `MD_CALC_CATALOG` as a read-only `calc:` symbol source
— exactly as the stock CNC vocab is pre-loaded today.

Prereq: Phase 1 DONE. TDD: 2A1 before 2A2–2A4.

References (verified):
- Symbol table: `packages/semantics/src/symbol-table.ts`; qualified names: `qname.ts`; project
  symbol collection: `project-symbols.ts`, `reference-index.ts`.
- Stock preload pattern: `packages/semantics/src/stock-loader.ts` (`loadStockVocabularies`) +
  `packages/semantics/src/stock/cnc-roles.ttrm`. **Copy this pattern** for the catalog preload.
- Default schema handling: `default-schema.ts`; area/def registration analogue:
  `area-table.ts` (areas register a symbol — the closest recent example).
- Namespace map to implement: [`../../contracts.md`](../../contracts.md) §5.

---

- [x] **2A1 — Tests first (red).** In `packages/semantics/src/__tests__/`:
  - Assert each MD def registers in its namespace (contracts §5): a `def domain Money` is findable
    as `md.domain.Money`; a `def cubelet sales` as `md.cubelet.sales`; etc.
  - Assert dimension attributes register **dimension-qualified**: `Customer.code` resolvable both as
    the dotted form and (within the dimension) bare.
  - Assert `MD_CALC_CATALOG` entries are present as a read-only `calc:` symbol source (e.g.
    `truncToDay` is "known"); an unknown calc name is absent.
  - Confirm red.

- [x] **2A2 — Wire `@modeler/md-catalog`.** Add `"@modeler/md-catalog": "workspace:*"` to
  `packages/semantics/package.json`; `pnpm install`. Update the dependency-graph diagram in
  `CLAUDE.md` (`md-catalog` is a leaf beside `grammar`; `semantics → md-catalog`).

- [x] **2A3 — Register MD namespaces.** Extend the symbol-table builder (`symbol-table.ts` /
  `project-symbols.ts`) to collect the seven MD kinds into the contracts §5 namespaces. Attributes
  are owned by their dimension; register both the qualified and dimension-local keys.

- [x] **2A4 — Catalog preload.** Add a catalog symbol source (mirror `stock-loader.ts`) that exposes
  `MD_CALC_CATALOG` for `calc:` resolution (read-only; not user-overridable in v1). Load it once at
  project-symbol build time.

- [x] **2A5 — Verify.**
  - 2A1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [x] **2A6 — Commit.** `Section MD-2A: MD symbol namespaces + calc-catalog preload`.
