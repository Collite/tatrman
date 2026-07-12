# PD1 — Config, derivation, no-cascade

**Goal:** add `[packages].root`/`layout` config, implement the declaration-authoritative derivation with the configurable root prefix and the no-cascade rule, and wire the mismatch + prefix-divergence diagnostics with config-driven severity.

**Reads:** [contracts §13.1–§13.2](../../v1-1/design/v1-1-contracts.md#13-packages--domains-increment-2026-06-19), [design §14.1–§14.3](../../v1-1/design/v1.1-packages-and-graphs.md#141-derivation-algorithm-b15-b17-b18), [architecture §3–§5](architecture.md).
**Blocked by:** v1.1 A (grammar) + B (package-aware symbol table) merged.
**Blocks:** PD3 (domain closure reuses derivation), PD4 (artifact serialises canonical names).
**Estimated time:** 3–4 days.

## Tests-first

Define these before touching implementation. Unit tests in `packages/semantics/src/__tests__/`; one component test in `tests/integration/`.

- [x] `packages/semantics/src/__tests__/derivation.test.ts` — pure-function `derivedPackage` / `effectivePackage` cases:
  - root `""`, file `a/b/er.ttr`, no declaration → effective `a.b`.
  - root `"com.tatrman"`, file `a/b/er.ttr`, no declaration → effective `com.tatrman.a.b`.
  - declaration `a.b`, file `a/b/er.ttr`, any root → effective `a.b` (**declaration wins**); no mismatch dx.
  - declaration `x.y`, file `a/b/er.ttr` → effective `x.y`; mismatch dx fires.
  - **no-cascade:** project has files `a/er.ttr` (declares `package renamed`) and `a/b/er.ttr` (no declaration). Assert `a/b/er.ttr` derives `a.b` (or `<root>.a.b`), **not** `renamed.b`.
  - root elision: reference `a.b.er.entity.x` and `com.tatrman.a.b.er.entity.x` resolve to the **same** symbol when root=`com.tatrman`. *(in `resolver-elision.test.ts`)*
- [x] `package-diagnostics.test.ts` — **placed in `packages/lint/src/__tests__/`, not `packages/semantics`** (the package validator lives in `@modeler/lint`, not a semantics validator; runtime location wins per CLAUDE.md):
  - `layout="flexible"`: declaration/dir mismatch → `ttr/package-declaration-mismatch` **Warning**.
  - `layout="strict"`: same input → **Error**.
  - `layout="off"`: same input → no diagnostic.
  - declaration whose **prefix** segment diverges (`a/b` declares `totally.different.thing`) → `ttr/package-prefix-divergence` (Warning under flexible, Error under strict), **instead of** the plain mismatch per contracts §13.2.
  - leaf-only override (`a/b` declares `a.renamed`) → plain mismatch only, **no** prefix-divergence.
- [x] **(follow-on, B24)** `package-segment-validity.test.ts` (alongside `package-diagnostics.test.ts` in `@modeler/lint`):
  - **invalid segment, no declaration:** folder `my-pkg/` (hyphen), no `package` decl → `ttr/invalid-package-segment` (Warning under `flexible`, Error under `strict`). Assert **no** `-`→`_` normalization (effective package is not `my_pkg`).
  - **invalid segment, with declaration:** folder `my-pkg/` declaring `package my_pkg` → declaration wins; `ttr/invalid-package-segment`, `ttr/package-declaration-mismatch`, **and** `ttr/package-prefix-divergence` are **all suppressed** for that segment. (Note: this is the one case where prefix-divergence *is* suppressed — see PD1.8 for how it interacts with PD1.6's "never suppressed" default.)
  - valid underscore segment (`obchodni_doklady/`) → no diagnostic (regression: the project convention must stay clean).
- [x] `tests/integration/src/packages-config.test.ts` — boot the LSP harness with a fixture project carrying `modeler.toml [packages] root="com.tatrman" layout="strict"`; assert `getProjectInfo` returns prefixed `PackageInfo.name`s and that a mismatching file surfaces an Error diagnostic.

## Library reference

No external library. Reuse the existing `modeler.toml` parse path (find it via `grep -rn "modeler.toml" packages/lsp/src packages/semantics/src`) and the existing `PackageInfo` shape (contracts §8.7). `SourceLocation` rules per CLAUDE.md — diagnostics point at the `package` declaration token.

## Implementation tasks

- [x] **PD1.1 — Config type + loader.** `PackagesConfig` + `defaultPackagesConfig` + `resolvePackagesConfig` added to `packages/semantics/src/manifest.ts` (the project-config module; consumed by both LSP and lint). `[packages].root`/`[packages].layout` parsed into `ResolvedManifest.packages`; unknown `layout` falls back to default + yields a config diagnostic. Loader test: `manifest-packages.test.ts`.
- [x] **PD1.2 — Derivation functions.** `derivedPackage` / `effectivePackage` (+ `classifyPackageMismatch`, `elideRoot`) in `packages/semantics/src/derivation.ts` per design §14.1. **No directory walk-up.** Guarded against an unknown/non-matching project root (would otherwise derive garbage from the absolute path).
- [x] **PD1.3 — Symbol table uses `effectivePackage`.** `DocumentSymbolTable`/`ProjectSymbolTable.upsertDocument` accept an explicit effective package; live callers (LSP `server.ts`, lint helpers + CLI) compute `effectivePackage`. Existing v1.1 symbol-table tests still green (root defaults to `""`).
- [x] **PD1.4 — Root elision in the resolver.** `Resolver(symbols, root)`; references resolve via both their written form and the root-normalised variant (`rootVariants`/`getCanonical`), step ordering preserved. Cover: `resolver-elision.test.ts`.
- [x] **PD1.5 — Mismatch severity by config.** `ttr/package-declaration-mismatch` severity is now a function of `cfg.layout` (Warning/Error/suppressed) via a per-report severity override in the lint runner.
- [x] **PD1.6 — Prefix-divergence diagnostic.** `ttr/package-prefix-divergence` (new `DiagnosticCode`, new lint rule) fires when a declaration's non-leaf segments diverge; emitted **instead of** the plain mismatch for prefix overrides. Never suppressed (warns even under `off`).
- [x] **PD1.7 — Surface `root` in `getProjectInfo`.** Response gains `packages: PackageInfo[]` with canonical (effective, root-prefixed) names + dependency edges.
- [x] **PD1.8 — (follow-on, B24) Segment validity + `ttr/invalid-package-segment`.** Validate each segment produced by `derivedPackage` against the `IDENT` shape (`[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*` — letters/digits/underscore, **no hyphen**). Add the rule in `@modeler/lint` (where the other package rules live) backed by a segment-check helper in `derivation.ts`. Behaviour per design §14.1 / contracts §13.2: **no `-`→`_` normalization** — reject, don't rewrite. When a segment is invalid **and** there is no `package` declaration → emit `ttr/invalid-package-segment` (severity by `cfg.layout`). When a valid declaration is present, the directory was never a valid candidate, so **suppress** `invalid-package-segment`, `package-declaration-mismatch`, and `package-prefix-divergence` for that file. **Reconcile with PD1.6:** prefix-divergence is otherwise "never suppressed" — add the single carve-out "unless the diverging directory segment is itself an invalid `IDENT`" so the escape-hatch case (`my-pkg/` + `package my_pkg`) stays quiet. Update the `DiagnosticCode` union and the diagnostics doc.

## Verify by running

```bash
pnpm --filter @modeler/semantics test
pnpm --filter @modeler/integration-tests test -- packages-config
pnpm -r build && pnpm -r typecheck && pnpm -r lint
```

All exit 0. Existing v1.1 semantics/integration tests still green with `root=""` defaults.

## DONE when

- [x] Every checkbox above ticked. *(PD1.8 / B24 segment-validity is a follow-on added after PD1 first completed — see its own box below.)*
- [x] `effectivePackage` is the single source of a file's package; no code path walks up directories for a parent declaration.
- [x] `[packages].layout` drives mismatch severity; `[packages].root` is applied to canonical qnames and elidable in references.
- [x] `ttr/package-prefix-divergence` fires only for prefix overrides; leaf-only overrides stay on the plain mismatch code.
- [x] No regression: a project with no `[packages]` block behaves as shipped v1.1 (mismatch severity now Warning by default per B16, the one intended behaviour change). Full `build`/`test`/`typecheck`/`lint` green; integration suite green (one pre-existing timing-flaky SQL test, unrelated to PD1).
- [x] **(B24 follow-on, PD1.8)** `ttr/invalid-package-segment` implemented: hyphenated/invalid directory segments are **rejected, not normalized** (`my-pkg/` never becomes `my_pkg`); the declaration escape hatch (`my-pkg/` + `package my_pkg`) suppresses segment/mismatch/prefix-divergence diagnostics; underscore segments stay clean.
