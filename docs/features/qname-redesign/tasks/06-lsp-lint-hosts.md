# 06 — LSP, lint, hosts (modeler / TS)

Goal: editor behaviour follows the new model end-to-end. See [`../plan.md`](../plan.md)
Phase 6, [`../contracts.md`](../contracts.md) §5.

**Pre-flight:** Phases 04–05 merged. Put new LSP feature tests in
`tests/integration/` (the canonical `PassThrough`-paired-connection harness), not in
`packages/lsp/__tests__/` (CLAUDE.md).

## Tasks

- [ ] **6.1 Tests first (lint).** Rules from contracts §5: `schema-name-collision`,
  `unknown-package-schema`, `schema-on-logical-model` (warn; er/md/cnc/binding),
  `ambiguous-reference`, `require-qualified-refs` (off→warn). One failing test each.
- [ ] **6.2 Tests first (integration LSP).** completion offers schema handles from the
  manifest; hover/definition resolve `shop.sales.Orders`; go-to-def on an er ref with
  no schema slot.
- [ ] **6.3 Implement lint rules.** In `@modeler/lint`; wire into the diagnostics
  pipeline.
- [ ] **6.4 LSP slot awareness.** completion/hover/definition speak the new slots;
  schema-handle + package completions from `Vocab`.
- [ ] **6.5 Host regen only.** `@modeler/vscode-ext`: no language logic — confirm the
  regenerated TextMate (Phase 01) + LSP client wiring still load; F5 smoke.

## Done when

- [ ] lint + `tests/integration/` + lsp suites green.
- [ ] F5 Extension Dev Host opens a `.ttrm`, resolves `shop.sales.Orders`, shows the
  collision/ambiguity diagnostics.

**Verify:** `pnpm --filter @modeler/lint test` ·
`pnpm --filter @modeler/integration-tests test` · `pnpm --filter @modeler/lsp test`
