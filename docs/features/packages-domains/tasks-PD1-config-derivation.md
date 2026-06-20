# PD1 — Config, derivation, no-cascade

**Goal:** add `[packages].root`/`layout` config, implement the declaration-authoritative derivation with the configurable root prefix and the no-cascade rule, and wire the mismatch + prefix-divergence diagnostics with config-driven severity.

**Reads:** [contracts §13.1–§13.2](../../design/v1-1-contracts.md#13-packages--domains-increment-2026-06-19), [design §14.1–§14.3](../../design/v1.1-packages-and-graphs.md#141-derivation-algorithm-b15-b17-b18), [architecture §3–§5](architecture.md).
**Blocked by:** v1.1 A (grammar) + B (package-aware symbol table) merged.
**Blocks:** PD3 (domain closure reuses derivation), PD4 (artifact serialises canonical names).
**Estimated time:** 3–4 days.

## Tests-first

Define these before touching implementation. Unit tests in `packages/semantics/src/__tests__/`; one component test in `tests/integration/`.

- [ ] `packages/semantics/src/__tests__/derivation.test.ts` — pure-function `derivedPackage` / `effectivePackage` cases:
  - root `""`, file `a/b/er.ttr`, no declaration → effective `a.b`.
  - root `"cz.dfpartner"`, file `a/b/er.ttr`, no declaration → effective `cz.dfpartner.a.b`.
  - declaration `a.b`, file `a/b/er.ttr`, any root → effective `a.b` (**declaration wins**); no mismatch dx.
  - declaration `x.y`, file `a/b/er.ttr` → effective `x.y`; mismatch dx fires.
  - **no-cascade:** project has files `a/er.ttr` (declares `package renamed`) and `a/b/er.ttr` (no declaration). Assert `a/b/er.ttr` derives `a.b` (or `<root>.a.b`), **not** `renamed.b`.
  - root elision: reference `a.b.er.entity.x` and `cz.dfpartner.a.b.er.entity.x` resolve to the **same** symbol when root=`cz.dfpartner`.
- [ ] `packages/semantics/src/__tests__/package-diagnostics.test.ts`:
  - `layout="flexible"`: declaration/dir mismatch → `ttr/package-declaration-mismatch` **Warning**.
  - `layout="strict"`: same input → **Error**.
  - `layout="off"`: same input → no diagnostic.
  - declaration whose **prefix** segment diverges (`a/b` declares `totally.different.thing`) → `ttr/package-prefix-divergence` (Warning under flexible, Error under strict), in addition to/instead of the plain mismatch per contracts §13.2.
  - leaf-only override (`a/b` declares `a.renamed`) → plain mismatch only, **no** prefix-divergence.
- [ ] `tests/integration/packages-config.test.ts` — boot the LSP harness with a fixture project carrying `modeler.toml [packages] root="cz.dfpartner" layout="strict"`; assert `getProjectInfo` returns prefixed `PackageInfo.name`s and that a mismatching file surfaces an Error diagnostic.

## Library reference

No external library. Reuse the existing `modeler.toml` parse path (find it via `grep -rn "modeler.toml" packages/lsp/src packages/semantics/src`) and the existing `PackageInfo` shape (contracts §8.7). `SourceLocation` rules per CLAUDE.md — diagnostics point at the `package` declaration token.

## Implementation tasks

- [ ] **PD1.1 — Config type + loader.** Add `PackagesConfig` (contracts §13.1) and `defaultPackagesConfig` to the project-config module. Parse `[packages].root` (string, default `""`) and `[packages].layout` (`"flexible"|"strict"|"off"`, default `"flexible"`); reject unknown `layout` values with a config diagnostic. Tick checkbox here after the loader test passes.
- [ ] **PD1.2 — Derivation functions.** Implement `derivedPackage(fileUri, projectRoot, cfg)` and `effectivePackage(doc, fileUri, projectRoot, cfg)` in `packages/semantics` per design §14.1. `derivedPackage` = `join(".", filter-nonempty([cfg.root, ...relDirSegments]))`. `effectivePackage` = declaration if present, else `derivedPackage`. **No directory walk-up** — children never read an ancestor's declaration.
- [ ] **PD1.3 — Symbol table uses `effectivePackage`.** Replace the current package-name source in the symbol-table builder (v1.1.B.2) with `effectivePackage`. Canonical qnames now carry the `root` prefix. Confirm existing v1.1 symbol-table tests still pass (root defaults to `""`, so unprefixed projects are unchanged).
- [ ] **PD1.4 — Root elision in the resolver.** In `packages/semantics/src/resolver.ts`, normalise a reference's leading segments: if a bare/qualified reference omits `cfg.root`, try it both with and without the prefix so both forms resolve to the canonical symbol. Add to the resolution chain without disturbing step ordering (§4.2). Cover with the elision test from Tests-first.
- [ ] **PD1.5 — Mismatch severity by config.** Make `ttr/package-declaration-mismatch` severity a function of `cfg.layout` (Warning/Error/suppressed). It is no longer a fixed Error — update the validator and the diagnostics table reference.
- [ ] **PD1.6 — Prefix-divergence diagnostic.** Add `ttr/package-prefix-divergence`: fires when a present declaration's non-leaf segments differ from the derived path (i.e. more than just the final segment is overridden). Severity per §13.2. Ensure it does **not** double-fire with plain mismatch for leaf-only overrides — decide precedence (recommended: emit prefix-divergence *instead of* plain mismatch when prefix segments differ).
- [ ] **PD1.7 — Surface `root` in `getProjectInfo`.** Ensure `PackageInfo.name` values are canonical (prefixed). Add the config echo if useful for the Designer/CLI (optional; only if a consumer needs it).

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test -- packages-config
pnpm -r build && pnpm -r typecheck && pnpm -r lint
```

All exit 0. Existing v1.1 semantics/integration tests still green with `root=""` defaults.

## DONE when

- [ ] Every checkbox above ticked.
- [ ] `effectivePackage` is the single source of a file's package; no code path walks up directories for a parent declaration.
- [ ] `[packages].layout` drives mismatch severity; `[packages].root` is applied to canonical qnames and elidable in references.
- [ ] `ttr/package-prefix-divergence` fires only for prefix overrides; leaf-only overrides stay on the plain mismatch code.
- [ ] No regression: a project with no `[packages]` block behaves byte-identically to shipped v1.1.
