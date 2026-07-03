# P2a — Lint package foundation (types, registry, runner)

**Package:** new `@modeler/lint` · **Pre-flight:** P0 DONE · **Contracts:** §3, §9

Goal: stand up the rule model, registry, and runner with **zero rules ported yet** — prove the
machinery with one trivial rule, then P2b ports the real 26.

Tick each box when done; commit as `Section P2a: <task>`.

---

- [x] **1. Scaffold the package.**
  Create `packages/lint/` (`@modeler/lint`, ESM, extends base tsconfig, Vitest). Deps:
  `@modeler/parser`, `@modeler/semantics`, `@modeler/edit` (all `workspace:*`). Empty `src/index.ts`;
  confirm it builds.

- [x] **2. Core types.**
  Create `packages/lint/src/rule.ts` with `Severity`, `RuleCategory`, `RuleScope`, `RuleId`,
  `LintDiagnostic`, `Fix`, `Rule`, `BaseContext`, `DocumentRuleContext`, `ProjectRuleContext`,
  `RuleContext` — exactly per contracts §3.1–§3.2. `Fix`/`WorkspaceEdit` typed but unused until P4.

- [x] **3. (test) Registry invariants.**
  `packages/lint/src/__tests__/registry.test.ts`: assert `RULES` has unique ids, at most one rule
  per owned `DiagnosticCode`, every rule's `category`/`defaultSeverity` valid, and `ruleForCode` /
  `rulesByCategory` return correctly. Seed with one trivial test rule until P2b. Fails until task 4.

- [x] **4. Registry.**
  Create `packages/lint/src/registry.ts` (contracts §3.3): assemble `RULES` from `rules/*` (empty +
  one trivial `always-ok`-style rule for now), throw at module load on any invariant violation,
  export `ruleForCode`, `rulesByCategory`. Make task 3 pass.

- [x] **5. (test) Runner behaviour.**
  `packages/lint/src/__tests__/runner.test.ts`: with one stub document rule that always reports,
  assert `lintDocument` returns the diagnostic with severity stamped from a passed-in config; assert
  an `off` rule is never invoked (spy on `check`); assert a project rule runs once and results bucket
  by `uri`. Use a hand-built `ResolvedLintConfig` stub (full config is P3).

- [x] **6. Runner.**
  Create `packages/lint/src/runner.ts` (contracts §3.5): `lintDocument` / `lintProject` build the
  shared context once (compute `refs` via `collectAllReferences`), invoke enabled rules of matching
  scope, collect reports, stamp severity from `config.severityOf(rule.id)`, skip `off`. Suppression
  hook is a no-op stub here (filled in P2c). Make task 5 pass.

- [x] **7. Gates.**
  `pnpm -r {build,test,typecheck,lint}` green. No `any`. The package exports `lintDocument`,
  `lintProject`, `RULES`, and the types from `src/index.ts`.
