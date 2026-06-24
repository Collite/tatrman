# Resolver consolidation — phased plan

**Status:** Plan v1, 2026-06-09. Delivers the deferred half of grammar-master
Phase 2.8: ai-platform consumes the published resolver/symbol-table instead of
hand-maintaining its own. Read [`architecture.md`](architecture.md) and
[`contracts.md`](contracts.md) first — they are normative.

## Goal & headline DoD

ai-platform's `infra/metadata/resolve/{ReferenceResolver,SymbolTable}.kt` are
deleted; `ReferenceResolutionPass` resolves through `PublishedResolverAdapter`
over `org.tatrman.ttrm.semantics`. A subsequent grammar/version bump in modeler is
absorbed by ai-platform with **no source edits** — only a `tatrman-modeler`
version-ref change (rehearsed in Phase C DoD). ai-platform's full
`:infra:metadata:test` suite stays green throughout (247 tests today).

## Decisions

| # | Decision | Resolution |
|---|---|---|
| R1 | Strategy | **Adapter** over the published library; keep ai-platform's proto identity, orchestration, reference collection, and import/circular diagnostics. (Not a wholesale replacement.) |
| R2 | Modeler change | **Add `SymbolEntry.namespace`** (additive); ship `0.3.0`. No resolver/qname change. |
| R3 | Package in proto identity | Stamped `cnc` only on auto-import (`viaStep == AutoImport`); `""` otherwise — mirrors legacy behaviour exactly. |
| R4 | Migration safety | **Differential parity harness** runs legacy + adapter side-by-side; delete legacy only once it is green. |
| R5 | `DrillMapValidator` | **Stays** (ai-platform-specific). |
| R6 | Import/circular diagnostics | **Stay** in `ReferenceResolutionPass` for now; folding them into the published `Validator` is optional Phase D. |
| R7 | Sequencing | Modeler `0.3.0` first (Phase A), then ai-platform Phases B→C in one branch/PR. |

## Phase A — modeler: `SymbolEntry.namespace` + publish 0.3.0

**Repo:** modeler. **Pre-flight:** Phase 2 complete; `0.2.1` published.

**Deliverables**
- `SymbolEntry` gains `namespace: String`; `DocumentSymbols` populates it from
  the file namespace (contracts §1).
- Any `SymbolEntry(...)` literal in tests updated; `ProjectSymbolTable`/resolver
  unchanged.
- CHANGELOG `0.3.0`; `kotlin/v0.3.0` published (parser/writer/semantics bundle).

**DoD**
- `./gradlew build` green; `pnpm -r test` green.
- Both conformance harnesses (AST + semantics) green — unchanged output.
- `org.tatrman:ttr-semantics:0.3.0` resolvable from GitHub Packages; the jar's
  `SymbolEntry` carries `namespace`.

## Phase B — ai-platform: adapter + parity harness (legacy still present)

**Repo:** ai-platform. **Pre-flight:** Phase A done; `0.3.0` resolvable.
Branch `grammar-master/resolver-consolidation`.

**Deliverables**
- `tatrman-modeler` → `0.3.0`.
- `PublishedResolverAdapter.kt` per contracts §2 (build + `resolve` +
  `toProtoQName` + used-import detail).
- `ResolverParitySpec.kt` per contracts §3 — legacy `ReferenceResolver` and the
  adapter run over the shared corpus + edge cases; asserts identical proto
  `QualifiedName` / diagnostic code.
- `ReferenceResolutionPass` **not yet switched** — both resolvers exist; the pass
  still uses the legacy one. (This phase only *proves* the adapter matches.)

**DoD**
- `ResolverParitySpec` green (every corpus + edge case matches).
- Full `:infra:metadata:test` green; ktlint clean.
- Any parity gap is either an adapter fix or a documented, agreed behavioural
  delta (none expected for real fixtures).

## Phase C — ai-platform: switch the pass, delete the legacy resolver

**Repo:** ai-platform. **Pre-flight:** Phase B DoD met (parity green).

**Deliverables**
- `ReferenceResolutionPass.run()` uses `PublishedResolverAdapter` only
  (contracts §2.3); `recordUsedImport` reworked onto adapter/`viaStep`
  (contracts §2.4).
- Delete `ReferenceResolver.kt`, `SymbolTable.kt`, and `ResolverParitySpec.kt`
  (its only purpose was the migration).
- `infra/metadata/resolve/` now contains only `ReferenceResolutionPass.kt` and
  `DrillMapValidator.kt`.

**DoD**
- Full `:infra:metadata:test` + `:infra:metadata` lint green.
- `ResolutionIntegrationSpec`, `StockRoleResolutionSpec`,
  `MetadataServiceFixtureSpec`, `SearchBlockEndToEndSpec`,
  `Phase2_2ExpressivenessSpec` green.
- **Grammar-bump rehearsal (Phase 2 DoD 2.8.8):** bump the modeler grammar
  version (e.g. `2.2`→`2.3`) + republish `org.tatrman:*` at a new version, switch
  ai-platform's `tatrman-modeler` ref, rebuild — the suite passes with no
  ai-platform source edits beyond the version ref (and any genuinely-new-syntax
  fixtures). This is the headline promise; record the result in the PR.
- PR opened; `docs/grammar-master/tasks/INDEX.md` 2.8 row flipped to ☑ done.

## Phase D — optional: fold import/circular diagnostics into the published Validator

**Status: EVALUATED — NOT PURSUED (2026-06-11, owner-approved).** The D.1.1 parity
gate was built and run; it fails. The published `Validator`/`PackageGraphBuilder`
are package-import-model-based and cannot match ai-platform's directory-package +
schema.namespace model — most importantly they **do not detect ai-platform's
circular-dependency fixtures** (the package graph's edges don't land on its
nodes). Folding them in would regress diagnostics, so the hand-rolled emitters
stay. Full evidence in [`tasks/D1-validator-diagnostics.md`](tasks/D1-validator-diagnostics.md).
Phases A–C already deliver the headline promise, so this optional cleanup adds no
value at acceptable risk.

**Repo:** both. **Pre-flight:** Phase C done. **Pursue only if desired.**

**Deliverables**
- `ReferenceResolutionPass`'s unused-import / duplicate-import /
  wildcard-no-match / missing-package / circular-dependency diagnostics replaced
  by `org.tatrman.ttrm.semantics.Validator.validateImports` +
  `validateCircularDependencies` (+ `PackageGraphBuilder`), via the same adapter
  pattern (proto `LoadWarning` mapping).
- Differential parity for the diagnostic set before deleting the hand-rolled
  emitters.

**DoD**
- Diagnostic parity proven, then the hand-rolled emitters deleted; suite green.
- Then literally the only TTR-semantics code left in ai-platform is proto/model
  glue + `DrillMapValidator`.

## Cross-cutting risks (see architecture §Risks)

- Same-package edge cases, stock package stamping, nested `parent.child` names,
  the bare-import guard — all covered by the Phase B parity harness, which is the
  gate for every later deletion.
- A modeler `0.3.0` republish is outward-facing (user-gated), as in Phase 2.

## What this plan does NOT cover

- Re-architecting ai-platform's proto identity model (preserved as-is).
- Migrating the TS LSP (unaffected; it already uses the published TS semantics).
- New grammar features — orthogonal.

## Suggested task-list breakdown (for a later "create the task lists" pass)

Per the planning skill, each stage below becomes one 6–8-task mini-list under
`docs/grammar-master/resolver-consolidation/tasks/`, TDD-first:

- A.1 — `SymbolEntry.namespace` + tests + conformance re-verify + publish 0.3.0.
- B.1 — parity-harness scaffolding (`ParityCase`, `assertSameResolution`, corpus
  from existing specs) — tests first, red.
- B.2 — `PublishedResolverAdapter` (build + context map + diagnostic map).
- B.3 — `toProtoQName` (package/stock/nested rules) → parity edge cases green.
- B.4 — used-import detail via `viaStep`; full suite green with legacy still wired.
- C.1 — switch `ReferenceResolutionPass`; delete legacy + parity scaffold.
- C.2 — grammar-bump rehearsal; INDEX update; PR.
- (D.1–D.2 — only if Phase D is taken.)
