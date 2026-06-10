# P3 — Config schema, precedence, presets, back-compat, watch

**Package:** `@modeler/lint`, `@modeler/lsp` · **Pre-flight:** P2c DONE · **Contracts:** §5, §8

Goal: real `.ttrlint.toml` config — per-rule/category severity, presets, precedence, correctness
clamp, `modeler.toml` back-compat, and live re-lint on config change.

Tick when done; commit as `Section P3: <task>`.

---

- [ ] **1. (test) Precedence + preset resolution.**
  `packages/lint/src/__tests__/config.test.ts`: assert resolution order default → `extends` →
  `[categories]` → `[rules]` (most specific wins) for representative rules; assert each preset
  (`recommended`, `strict`, `all`, `none`) yields the documented severities (design §5.4). Fails
  until task 3.

- [ ] **2. (test) Clamp, back-compat, unknown-rule.**
  Same file: assert a `correctness` rule requested `off`/`warning` is clamped to `error` with a
  `ttrlint/clamped-severity` diagnostic; assert `modeler.toml [lint].strict=true` (no `.ttrlint.toml`)
  ⇒ `strict`-preset behaviour and `requireDescriptions=true` ⇒ `missing-description=warning`; assert
  both-present ⇒ `.ttrlint.toml` wins + `ttrlint/deprecated-lint-config`; assert an unknown rule id /
  category yields `ttrlint/unknown-rule` / `ttrlint/unknown-category`.

- [ ] **3. Config loader + resolver.**
  Create `packages/lint/src/config.ts` (contracts §5): `loadLintConfig(projectRoot)` discovers
  `.ttrlint.toml` beside `modeler.toml`, parses with `smol-toml` (mirror `manifest.ts`), applies
  presets + precedence + clamp, reads `modeler.toml [lint]` fallback via the existing
  `ResolvedManifest.lint`, and returns `ResolvedLintConfig` (`severityOf`, `failOn`, `applyFixes`,
  `diagnostics`). Make tasks 1–2 pass.

- [ ] **4. Feed config into the runner + presets file.**
  Replace the temporary `recommended` config in `lsp` `publishDiagnostics` with
  `loadLintConfig(projectRoot)` (cached; invalidated in task 6). Put preset definitions in
  `packages/lint/src/presets.ts`. Ensure `off` rules are skipped end-to-end.

- [ ] **5. Surface config-level diagnostics on `.ttrlint.toml`.**
  In `lsp`, publish `config.diagnostics` against the `.ttrlint.toml` URI (range at the offending
  key, or file start if unlocatable). These are `ttrlint/*` codes, mapped to LSP severities like
  any other.

- [ ] **6. (integration) Live config watch.**
  Extend the existing config-invalidation pattern (`server.ts:405,422` completion config) to watch
  `.ttrlint.toml`: on change, invalidate the cached lint config and re-`publishDiagnostics` for all
  open docs. Add a `tests/integration/` test: open a doc with a `warning`, write a `.ttrlint.toml`
  raising that rule to `error`, assert the re-published diagnostic severity changes.

- [ ] **7. Docs + gates.**
  Document `.ttrlint.toml` in `docs/manual/en/` (a short section near the `[lint]` mention in
  `10-packages-and-imports.md` / `14-reference.md`), listing rule ids and presets. Run
  `pnpm -r {build,test,typecheck,lint}` — green. No `any`.
