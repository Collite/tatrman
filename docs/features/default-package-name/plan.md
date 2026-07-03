# Implementation plan — Default (root) package name

Six phases across **two repos** (modeler + ai-platform). Each phase is behaviour-preserving until a
`modeler.toml` actually sets `[project] package`. TDD throughout: tests in the named suite are written
and made to fail before the implementation in that phase.

Global pre-flight (all phases): `pnpm install`; baseline green `pnpm -r typecheck && pnpm -r test`;
for Kotlin phases, baseline green `./gradlew :packages:kotlin:ttr-semantics:test`.

> Tooling note: `packages/lsp/src/server.ts` contains a NUL byte — search it with `rg -a`, plain
> `grep` silently skips it (architecture §10).

---

## Phase 1 — Manifest plumbing (modeler / TS)

**Deliverable.** `modeler.toml` accepts `[project] package`; resolves to
`ResolvedManifest.rootPackage` (default `""`); invalid values fall back to `""` (non-fatal).

**Work.** `semantics/manifest.ts`: add `project.package?: string`; add `rootPackage` to
`ResolvedManifest`; add + export `isValidPackageName()` (regex per contracts §1); wire `resolveManifest()`.

**Tests first** (`semantics` manifest suite): `isValidPackageName` cases; `resolveManifest`
valid/invalid/missing → correct `rootPackage`.

**DONE.** New manifest tests green; existing unchanged; `pnpm --filter @modeler/semantics test` green.

---

## Phase 2 — Effective-package core (modeler / TS)

**Deliverable.** `effectivePackageName()` helper + an *activated* symbol-table package parameter, so
the symbol table can name root-package defs — proven in isolation.

**Work.**
- New `semantics/effective-package.ts` `effectivePackageName()` (contracts §3); export it.
- `symbol-table.ts`: `DocumentSymbolTable` takes the package from its constructor arg, not `ast.packageDecl?.name ?? ''`.
- `project-symbols.ts`: rename `_packageName`→`packageName`; pass it into `DocumentSymbolTable`.
- Keep the `isStockCnc` gate intact (E1).

**Tests first** (`semantics`): `effectivePackageName` matrix (contracts §8); symbol-table registers
`df.er.entity.X` for a root file when the caller passes `df`; **stock-leak regression** (configured
`rootPackage` must NOT rename `cnc.cnc.role.*`).

**Pre-flight.** Phase 1 merged. Enumerate all `upsertDocument` callers (grep) before flipping the
source-of-truth — `lint/src/cli.ts:117,151` and `lsp/server.ts:747,791,852` pass a package today.

**DONE.** Helper + symbol-table tests green; **full** `@modeler/semantics` suite green (proves no-op
when `rootPackage===""`).

---

## Phase 3 — Thread through LSP qname builders + resolution (modeler / TS)

**Deliverable.** Every qname path (symbol table, model graph, completion/reference, resolution
context) agrees on the effective package; cross-package resolution to a named root package works E2E.

**Work.**
- `lsp/server.ts`: replace the three `ast.packageDecl?.name ?? ''` sites (`:747,791,852`) with
  `effectivePackageName(ast, uri, manifest.projectRoot, manifest.rootPackage)`; feed the same value to
  the resolution context.
- Route `lsp/graph-methods.ts:56`, `lsp/model-graph.ts:541`, `lsp/completion-reference.ts:{102,107,114,309}`
  through the helper.

**Tests first.**
- `semantics` component: root-package def reachable via `import df.*`, `import df.er.entity.X`, and
  bare-but-unique step-6 FQN `df.er.entity.X` (contracts §8).
- `tests/integration`: server boot with `modeler.toml` `[project] package = "df"`, a root `.ttr` with no
  decl + a second file in package `app` importing `df.*`; assert the cross-package ref resolves (no
  diagnostic) and `modeler/getModelGraph` emits node id `df.er.entity.X`. (New LSP-feature tests live
  in `tests/integration`, per `CLAUDE.md`.)

**Pre-flight.** Phase 2 merged; confirm `ResolvedManifest`/`projectRoot` reach each call site.

**DONE.** Integration scenario green; model-graph node ids match symbol-table qnames; `pnpm -r test`
green; VS Code Ext Dev Host smoke: a root `.ttr` resolves `import df.*` with no false "unresolved".

---

## Phase 4 — Lint awareness + UX (modeler / TS)

**Deliverable.** The lint package rules and editor affordances respect a configured root package.

**Work.**
- `lint/src/rules/packages.ts`:
  - `missing-package-declaration`: a **root** file with no declaration but a configured `rootPackage`
    must NOT be nudged as "no package" — either stay silent or message "in package 'df' (project
    default)". (Today it already skips root files via `isRootFile`; verify the configured-root case.)
  - `package-declaration-mismatch`: unchanged for sub-dir files; for root files keep D2 (declaration
    absolute) — optionally an **info** (OQ3, default-off) when a root decl ≠ `rootPackage`.
- `lsp/completion-property.ts`: offer `manifest.rootPackage` in `package`/`import` completion (contracts §7).
- `lsp/code-lens.ts` / `document-symbol.ts`: show the effective package for root files.

**Tests first.** Lint: root file + `rootPackage` set → no `missing-package-declaration` error;
sub-dir behaviour unchanged. Completion offers the configured root package. Display assertions.

**Pre-flight.** Phase 3 merged.

**DONE.** Lint + completion + display tests green; full suite green.

---

## Phase 5 — Kotlin twin conformance (modeler / Kotlin — `ttr-semantics`)

**Deliverable.** The Kotlin semantics computes the same effective root-package name as the TS layer,
proven by the conformance harness, and is published.

**Work.**
- `ttr-semantics`: add `Manifest.kt` (parse `[project] package`, `isValidPackageName`, `rootPackage`
  default `""`) — TOML dep decision per OQ2 (proposed: add to `ttr-semantics`).
- Add `effectivePackageName(ast, uri, projectRoot, rootPackage)` mirroring TS §4, reusing the existing
  `PackageInference.inferFromUri` (`ttr-semantics/.../PackageInference.kt`).
- Wire `SymbolTable` ingestion to accept the effective name (it already takes a declared `packageName`
  — extend the caller, not the table's qname rule).

**Tests first** (Kotest + conformance).
- `ttr-semantics` unit (mirror the TS `effectivePackageName` matrix; stock-leak regression).
- **Conformance fixture**: a project with `modeler.toml` `[project] package = "df"` + a root file;
  extend `SemanticsConformanceDump` so TS and Kotlin emit identical qnames (`df.er.entity.X`).

**Pre-flight.** Phases 1–2 merged (TS semantics is the source of truth the Kotlin twin mirrors —
[[grammar-master-phase2]]). Decide OQ2 (manifest-parsing location).

**DONE.** Kotlin unit + conformance green; `./gradlew :packages:kotlin:ttr-semantics:test` green;
publish `kotlin-semantics/v<x.y.z>` per `PUBLISHING.md`.

---

## Phase 6 — ai-platform integration (read the project file + name root files)

**Deliverable.** ai-platform's runtime loader reads `modeler.toml` and gives root-level files the
configured package, on the go-forward resolution path — so runtime resolution matches the editor.

**Discovery (OQ1, do first).** Confirm which resolution path is authoritative going forward:
`resolve/ReferenceResolutionPass.kt` (uses `computedPackage`) vs `resolve/PublishedResolverAdapter.kt`
(uses `declaredPackage`, the published `ttr-semantics`). Extend the **go-forward** one; note the other.

**Work** (`ai-platform/infra/metadata`).
- Loader: locate `<modelDir>/modeler.toml` (model root = `METADATA_GIT_SUBDIR`), parse `rootPackage`
  (via the published `ttr-semantics` `Manifest` from Phase 5, or local parse per OQ2). No manifest →
  `rootPackage = ""` (today's behaviour).
- `source/Source.kt`: where `computePackageFromPath(...)` yields **empty** (root file) and there's no
  declaration, substitute `rootPackage`. Keep the `declared == computed` mismatch error for non-root.
- `resolve/PublishedResolverAdapter.kt:43`: pass the effective package (root files → `rootPackage`)
  instead of `f.declaredPackage ?: ""`.
- `resolve/ReferenceResolutionPass.kt:45`: root files are no longer unconditionally exempt when
  `rootPackage` is set.

**Tests first** (ai-platform). Component test in `infra/metadata`: a fixture model with `modeler.toml`
`[project] package = "df"` + a root file resolves its defs under `df.*` and a cross-package
`import df.*` succeeds; without a manifest, behaviour is unchanged (root files exempt).

**Pre-flight.** Phase 5 published; bump ai-platform's `ttr-semantics` pin; loop in the ai-platform
owner. Track as an ai-platform issue/PR (lineage: [[resolver-consolidation-done]] PR #90,
[[embedded-sql-phase1]] issue #7).

**DONE.** ai-platform component tests green; runtime resolution of a root-package model matches the
editor (spot-check the same fixture through the modeler LSP and the ai-platform loader); ai-platform
PR merged + new `ttr-semantics` pin recorded.

---

## Sequencing & risk

```
P1 ─► P2 ─► P3 ─► P4            (modeler TS, strictly ordered)
        └─► P5  ─────────► P6   (Kotlin twin → ai-platform; P5 needs P1–2, P6 needs P5)
```

- **Top risk — qname-builder drift** (architecture §5): mitigated by the Phase 3 integration test
  asserting symbol-table and model-graph agree.
- **Stock cnc leakage**: mitigated by explicit regressions in Phases 2 and 5.
- **Cross-repo divergence (OQ1)**: mitigated by Phase 6 discovery extending the go-forward path only,
  and by the Phase 5 conformance fixture pinning TS≡Kotlin for the root case.
- **No grammar change** → no `TTR.g4` regen, no TextMate regen, no grammar sync.

## Next step after approval

Generate per-phase task lists (6–8 tasks each, checkboxes, TDD ordering) under
`docs/features/default-package-name/tasks/`, plus an index task-management doc — per the planning
skill's task-list rules. Phases 5–6 task lists should cite the conformance harness and `PUBLISHING.md`
explicitly.
