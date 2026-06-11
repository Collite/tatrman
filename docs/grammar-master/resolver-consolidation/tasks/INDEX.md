# Resolver consolidation — task index

**Use this index to navigate.** Each linked file is a mini-task-list (6–8 tasks)
for one coding session by one developer/agent. Check each box the moment the task
is done — do not batch.

**Read first (normative):**
- [`../architecture.md`](../architecture.md) — approach, identity-model mismatch,
  the adapter, the parity harness.
- [`../contracts.md`](../contracts.md) — exact APIs, the `toProtoQName`
  algorithm, context/diagnostic mappings, versioning.
- [`../plan.md`](../plan.md) — phases, deliverables, DoD.

**Conventions:**
- **Tests precede implementation** within a stage (TDD). Specs are written first
  and must fail to *compile* (red) against the missing production type — never on
  a spec bug.
- **Which repo** is stated per stage. Phase A is modeler-only; Phases B–D are
  ai-platform-only.
- **Docs win.** If a task disagrees with `contracts.md`/`architecture.md`, the
  docs are right and the task is wrong — open a discussion before diverging.
- **No SNAPSHOTs.** Cross-repo iteration uses `publishToMavenLocal` + a temporary
  `mavenLocal()` in ai-platform `settings.gradle.kts`, reverted before commit.
- Commit style: `Section <X>: <desc>` or `<scope>: <desc>`; commit+push per stage.

## Phase A — modeler: `SymbolEntry.namespace` + publish 0.3.0

| Stage | Mini-task-list | Status |
|---|---|---|
| A.1 | [`SymbolEntry.namespace` + publish 0.3.0](A1-symbolentry-namespace.md) | ☑ |

## Phase B — ai-platform: adapter + parity (legacy still wired)

Branch `grammar-master/resolver-consolidation` off ai-platform `main` (after
PR #89, the stock swap, is merged — so the `ttr-semantics` dependency is present).

| Stage | Mini-task-list | Status |
|---|---|---|
| B.1 | [Parity harness scaffolding (red)](B1-parity-harness.md) | ☑ |
| B.2 | [`PublishedResolverAdapter` — build + resolve + maps](B2-adapter.md) | ☑ |
| B.3 | [`toProtoQName` — parity green](B3-toprotoqname.md) | ☑ |

## Phase C — ai-platform: switch the pass, delete the legacy resolver

| Stage | Mini-task-list | Status |
|---|---|---|
| C.1 | [Switch the pass + rework used-imports + delete legacy](C1-switch-and-delete.md) | ☑ |
| C.2 | [Grammar-bump rehearsal + INDEX + PR](C2-rehearsal-and-pr.md) | ☐ |

## Phase D — optional: fold import/circular diagnostics into the published Validator

| Stage | Mini-task-list | Status |
|---|---|---|
| D.1 | [Diagnostic parity + delete hand-rolled emitters](D1-validator-diagnostics.md) | ☐ |

## Project DoD

- [x] `org.tatrman:ttr-semantics:0.3.0` published with `SymbolEntry.namespace`.
- [ ] ai-platform `infra/metadata/resolve/` contains only
      `ReferenceResolutionPass.kt` + `DrillMapValidator.kt`.
- [ ] `:infra:metadata:test` green (≥ the current 247 tests).
- [ ] Grammar-bump rehearsal passes with **no** ai-platform source edits beyond
      the `tatrman-modeler` version ref (Phase 2 DoD 2.8.8).
- [ ] `docs/grammar-master/tasks/INDEX.md` 2.8 row → ☑.
