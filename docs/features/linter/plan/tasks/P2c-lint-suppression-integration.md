# P2c — Suppression + LSP rewire + golden test

**Package:** `@modeler/lint`, `@modeler/lsp`, `@modeler/semantics` · **Pre-flight:** P2b DONE ·
**Contracts:** §4, §8

Goal: trivia-based suppression, prove byte-parity with the old `Validator`, switch the LSP to the
runner, and delete `Validator`.

Tick when done; commit as `Section P2c: <task>`.

---

- [x] **1. (test) Suppression unit tests.**
  `packages/lint/src/__tests__/suppression.test.ts`: for each directive form
  (`ttr-disable-next-line`, `ttr-disable-line`, `ttr-disable`/`ttr-enable` range, `ttr-disable-file`),
  with and without explicit rule ids, assert `isSuppressed(ruleId, line)` is correct. Assert
  `unused()` reports a directive that matched nothing. Assert a `correctness` rule is reported as
  `ttrlint/cannot-suppress` and is NOT suppressed. Inputs carry comments (P0 trivia). Fails until t2.

- [x] **2. Suppression index.**
  Create `packages/lint/src/suppression.ts` (contracts §4): `buildSuppressionIndex(ast)` reads
  `leadingTrivia`/`trailingTrivia` comment tokens, parses directives (comma/space-separated ids,
  empty = all), resolves block/file ranges to line sets. Wire it into the runner's suppression hook
  (replacing the P2a no-op): drop suppressed diagnostics; for correctness rules, ignore suppression
  and emit `ttrlint/cannot-suppress`; collect unused → `ttrlint/unused-suppression`.

- [x] **3. (test) Golden parity corpus.**
  `packages/lint/src/__tests__/golden.test.ts`: assemble a corpus covering every diagnostic. Run
  BOTH the old `Validator` (still present) and the new runner under `recommended`-equivalent
  severities; assert the two diagnostic sets are byte-identical (same code/message/source set per
  uri). This is the gate that authorizes deleting `Validator`.

- [x] **4. Rewire `publishDiagnostics`.**
  In `packages/lsp/src/server.ts` add `"@modeler/lint": "workspace:*"`. Replace the eight
  `validator.validate*` calls (`:252–259`) with `lintDocument(uri, ast, tokenStream, deps, config)`
  + cached `lintProject(...)` filtered to `uri`. Build `deps` from existing `symbols`/`resolver`/
  `manifest`. Keep parser errors emitted first (`:241–247`). Add an (unreachable) `off` branch to
  `severityToLsp`. Pass a temporary `recommended` config until P3.

- [x] **5. Delete `Validator`; re-home helpers.**
  Remove `packages/semantics/src/validator.ts` and its exports. Move shared helpers still needed by
  rules (`searchBlocksOf` already moved in P2b; `enclosingQnameOf` stays in `semantics` and is
  imported by `rules/references.ts`). Delete the old `semantics` validator tests (now ported).
  Fix all references across the repo (grep `validateDocument`, `new Validator`).

- [x] **6. (integration) LSP diagnostics test.**
  In `tests/integration/`, boot the server, `didOpen` a doc with a known violation and a
  `// ttr-disable-next-line <rule>` above it; assert the published diagnostics reflect the rule set
  and that the suppressed one is absent. Use the `PassThrough`-paired-connection harness already
  there.

- [x] **7. Gates.**
  `pnpm -r {build,test,typecheck,lint}` green; golden test green; no `Validator` references remain;
  no `any`. Update `docs/v1/design/diagnostics.md` with a pointer that the linter now owns these
  diagnostics (link to this feature's design).
