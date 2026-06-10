# P4 — Fix model, re-home quick-fixes, `--fix`, CodeActions

**Package:** `@modeler/lint`, `@modeler/edit`, `@modeler/lsp` · **Pre-flight:** P0 + P2 DONE; P3 for
`[fix]` · **Contracts:** §6, §7.1, §8

Goal: rules carry `fix`; safe fixes batch-apply to a fixpoint; suggestions are CodeAction-only; the
four existing quick-fixes are re-homed; `ttr lint --fix` ships.

Tick when done; commit as `Section P4: <task>`.

---

- [ ] **1. Move quick-fix edit logic into `@modeler/edit`.**
  Move the edit-building bodies of `quickFixUnusedImport`, `quickFixMissingPackageDeclaration`,
  `quickFixUnimportedReference`, `quickFixPackageDeclarationMismatch` from
  `packages/lsp/src/code-actions.ts` into `@modeler/edit` as reusable `WorkspaceEdit` builders
  (offset-based, trivia-preserving since edits are minimal ranges). Keep `refactorExtractDefToNewFile`
  where it is (it depends on the formatter, not a lint rule).

- [ ] **2. (test) Per-fix unit tests.**
  `packages/lint/src/__tests__/fix.test.ts`: for each safe fix (`unused-import`, `duplicate-import`,
  `unimported-reference`, `missing-package-declaration`, `fuzzy-without-searchable`,
  `graph-layout-stale-node`), assert the produced `WorkspaceEdit` applied to the source yields the
  expected text, and that surrounding comments are preserved. Assert suggestion fixes
  (`ambiguous-reference`, `package-declaration-mismatch`, `graph-name-mismatch`,
  `duplicate-search-property`) are `kind:'suggestion'`. Fails until task 3.

- [ ] **3. Populate `Rule.fix`.**
  Add `fix` to the rules per design §5.5 Fix column, calling the `@modeler/edit` builders. `safe`
  for the auto subset, `suggestion` for judgment calls. `file-ordering`'s "fix" is deferred to the
  formatter — do not synthesize an edit. Make task 2 pass.

- [ ] **4. (test) Collect/merge/fixpoint.**
  `packages/lint/src/__tests__/fix-runner.test.ts`: assert `collectSafeFixes` includes only `safe`
  fixes, merges non-overlapping edits, drops overlaps into `deferred`; assert a `--fix` fixpoint
  loop converges (applies, re-parses, re-lints until no safe fix or max passes) and is idempotent;
  assert suggestion-only diagnostics are never applied.

- [ ] **5. Fix collector.**
  Create `packages/lint/src/fix.ts` (contracts §6): `collectSafeFixes(diags, ctx)` → `FixResult`.
  Honour `config.applyFixes` (`safe`|`none`). Make task 4 pass.

- [ ] **6. `ttr lint` CLI with `--fix`.**
  Create `packages/lint/src/cli.ts` (commander, mirror migrate). Implement `modeler-lint <root>`
  with `--fix`, `--format pretty|json`, `--fail-on`, `--rule`, `--explain`, `--quiet` (contracts
  §7.1). `--fix` runs the fixpoint then reports the remainder. Exit codes 0/1/2. Add the `bin` entry.
  Add a CLI test (`execSync`, fixture) asserting `--fix` removes an unused import and exits 0, and
  `--explain <id>` prints docs.

- [ ] **7. Rewire `onCodeAction`.**
  In `server.ts` replace the hard-coded `switch (diag.code)` (`:965+`) with: for each context
  diagnostic, look up `ruleForCode(diag.code)`; if it has a `fix`, return a `CodeAction` with
  `fix.build(ctx, diag)` as the edit — `safe`→`CodeActionKind.QuickFix`,
  `suggestion`→`CodeActionKind.RefactorRewrite`. Keep `refactorExtractDefToNewFile` as a separate
  refactor action.

- [ ] **8. (integration) + gates.**
  `tests/integration/`: open a doc with an unused import, request code actions, assert a quick-fix is
  offered and applying it removes the import; assert an `ambiguous-reference` offers a suggestion
  (refactor) action, not a batch-applied fix. Run `pnpm -r {build,test,typecheck,lint}` — green. No
  `any`. Update the design doc's autofix section if any fix's safe/suggestion classification changed.
