# Review 003 — Phase 1 (Foundation tier complete)

**Reviewer:** Claude (Opus 4.7)
**Date:** 2026-05-14
**Subject:** Developer claims Phase 1 is done. Plan is `docs/plan/tasks-phase-01-foundation.md`. Progress log is `docs/plan/progress-phase-01.md`.
**Verdict:** **Not done.** Build/test/lint are green, the TextMate generator and language configuration are real Phase-1 work, and the `.ttrl` LSP gate is correctly wired. But three plan sections (§F recovery, §G semantic tokens, §J smoke test) are **claimed done while implementation is missing or fake**, one previously-fixed defect (`runServer` casts) **regressed**, and several smaller issues (ttrl grammar contribution, fixture/README mismatch, doc example, CI Node 20 ↔ TypeScript-strip incompatibility) need closing. The pattern of "scaffolded test file with placeholder body" + "progress doc says ✓" has recurred from Phase 0.

Companion task list in `tasks-review-003.md`.

---

## 0. Build status

| Command | Result | Comment |
|---|---|---|
| `pnpm -r build` | ✅ 0 | clean |
| `pnpm -r test` | ✅ 45 tests across 5 packages | numbers below |
| `pnpm -r lint` | ✅ 0 | clean |
| `pnpm --filter @modeler/integration-tests test` | ✅ 5 tests | parser×3, lsp×2 |
| Test counts: parser 29, semantics 1, lsp 4, vscode-ext 6, integration 5 | | progress doc says LSP has 5; actually 4 (semantic-tokens test was removed but the doc wasn't updated) |

Working tree: many uncommitted modifications + 8 untracked files/dirs. Phase 1 PR cannot be made from this state without staging discipline.

---

## 1. Section-by-section verification

### §A — Carryover from review-001/review-002 P1/P2 ✅

All eight verification points hold on disk. `endColumn === 20` regression test still passes (verified by `pnpm --filter @modeler/parser test`). Real work.

### §B — TextMate grammar full coverage 🟡 (real work, with sloppiness)

What's right:
- The generator (`packages/vscode-ext/scripts/generate-tm-grammar.ts`) now reads `TTR.g4` via regex (`^([A-Z_][A-Z0-9_]*)\s*:\s*(.+?)\s*;`), extracts every lexer rule's literal alternatives, and groups them by scope. This is the **right** Phase 1 move — Phase 0's hardcoded token list is gone.
- The generated `syntaxes/ttr.tmLanguage.json` covers 9 scope categories.
- `regen-tmgrammar` script + CI guard (`git diff --exit-code`) is in `.github/workflows/ci.yml`.

What's wrong:
- **`generate-tm-grammar.ts` has two duplicate `case` arms** in its `tokenToScope` switch:
  - `case 'QUERY':` at lines 51 and 66 (both return `'keyword.other.schema.ttr'` — second is dead code)
  - `case 'BOOLEAN_LITERAL':` at lines 138 and 140 (both return `'constant.language.ttr'`)
  Accidentally same-value, so the output is correct, but the source is rotting. A future contributor changing one of them won't realize the other is silently overriding.
- **`case 'NULL_LITERAL':`** (line 139) — no `NULL_LITERAL` lexer rule exists in `TTR.g4`. Harmless but misleading.
- **The generator tests copy the generator's source instead of importing it.** `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts` re-implements `parseGrammar` and `tokenToScope` inline (lines 10–137). Every assertion runs against the test's own copy, not the generator. If the generator drifts, the tests don't notice. The "QUERY is classified as schema keyword, not kind" assertion (line 189) doesn't actually verify the generator's behavior — it verifies the test's copy. **The 6-test "generator unit test" suite covers zero generator code.**
- **`pnpm run regen-tmgrammar` invokes `node scripts/generate-tm-grammar.ts` directly.** Native TS execution requires Node ≥ 22.6 (default off) or Node 22.6+ with `--experimental-strip-types` enabled by default in Node 23+. CI uses `node-version: '20'`. **The CI guard step will fail at runtime in CI** with `ERR_UNKNOWN_FILE_EXTENSION` (or similar). Local dev passes because Node 24 strips types natively. Run CI once to confirm.
- **Plan §B categories not implemented:** `entity.name.tag.ttr` (def-names — needs context-aware match) and `variable.other.qname.ttr` (dotted refs) are listed in the plan but not produced by the generator. These are the most useful TextMate additions; they need begin/end patterns or `match` with regex contexts. Defer-with-note is fine, but the plan didn't say "subset"; the progress doc doesn't note the gap.

### §C — Full language configuration ✅ (mostly)

- `wordPattern` for Latin Extended is in place.
- `indentationRules` with `increaseIndentPattern: ".*\\{[^}]*$"` and `decreaseIndentPattern: "^\\s*\\}"` are in place.
- `onEnterRules` cover empty `{}` and `[]`.

Minor: the `onEnterRules` `indentAction: 1` value should ideally be the named `IndentAction.Indent` (= 1) constant; VS Code accepts the numeric literal, so OK.

### §D — `.ttrl` layout sidecar support 🟡 (LSP gate good; grammar contribution broken)

What's right:
- LSP gates parsing: `server.ts:36` `if (uri.endsWith('.ttrl')) return;` — correct.
- The LSP unit test `'textDocument/didOpen with .ttrl file produces no diagnostics'` actually asserts no diagnostics arrive after `didOpen` of a `.ttrl` URI — real test.
- `packages/vscode-ext/schemas/ttrl.schema.json` matches architecture §6 (version/viewports/nodes/edges + displayMode enum). `contributes.jsonValidation` correctly binds `*.ttrl` to the schema.
- `language-configuration-ttrl.json` exists with JSON-style brackets/auto-close.

What's wrong:
- **`contributes.grammars` entry for `ttrl` is malformed.** `packages/vscode-ext/package.json` lines 66–70:
  ```json
  { "language": "ttrl", "scopeName": "source.json", "path": "./syntaxes/ttr.tmLanguage.json" }
  ```
  The path points at the **TTR** TextMate grammar but labels the scope as `source.json`. VS Code will load the TTR grammar and apply it to `.ttrl` files under the JSON scope name — i.e. `.ttrl` files highlight as TTR but get JSON-themed colors. Plan said "Reuse `source.json` — no per-`.ttrl` grammar needed." The correct shape is either (a) drop the entry entirely and rely on VS Code's built-in JSON if you also register `ttrl` as a JSON alias, or (b) ship a tiny `syntaxes/ttrl.tmLanguage.json`:
  ```json
  { "scopeName": "source.ttrl", "patterns": [{ "include": "source.json" }] }
  ```
  and register that. Either way, today's setup produces wrong highlighting.
- **`language-configuration-ttrl.json` declares `lineComment: "//"`** (line 3). Plain JSON does not allow `//`. If the intent is JSONC, the JSON schema validator (`jsonValidation`) will still complain. The architecture §6 says it's plain JSON. Either remove `lineComment` from the config or move the file to `JSONC` mode (and accept comments in the schema validation). Today's setup is internally inconsistent.

### §E — Diagnostic taxonomy 🟡 (codes defined; two of three never fire)

What's right:
- `DiagnosticCode` enum in `packages/parser/src/diagnostics.ts`.
- `ParseError.code` field added; `DiagnosticErrorListener.syntaxError` sets it to `DiagnosticCode.ParseError`.
- LSP propagates `code` and `source: 'modeler'` on every published `Diagnostic` (server.ts:52-53). Verified by an LSP unit test that asserts both fields.
- Severity mapping table is in `docs/design/diagnostics.md`.

What's wrong:
- **`ttr/unknown-property` is defined but never emitted.** The TTR grammar accepts any identifier as a property key (`propertyEntry : key propSep? value` where `key : id`). The parser never rejects an unknown property name — it just accepts the value. So the `UnknownProperty` enum value is dead. The plan said "the parser already rejects these" (Phase 1 task list §E); that's incorrect — the parser does not reject these, and never could without semantic context (which is Phase 2 work). Either delete the code or document it as "reserved for Phase 2".
- **`ttr/parse-recovery-info` is defined but never emitted.** No code path in `walker.ts` or `server.ts` produces this code. The plan said it's emitted by §F's recovery work; that work didn't happen (see §F below). The LSP's severity-mapping branch (`server.ts:41-43`) for this code is also dead.
- **`docs/design/diagnostics.md`'s `ttr/parse-error` example is wrong.** The doc says (line 24-27):
  > `def entity foo { bar: int }` — `bar` is not a valid `entity` property — the parser emits `ttr/parse-error`.
  
  Wrong: that input parses cleanly. The grammar permits any property name; semantic validation of property names is Phase 2.D, not Phase 1. The example needs to be replaced with a real syntax error (e.g. unmatched brace, missing `def` keyword).

### §F — Parser error recovery ❌ (the actual recovery work was not done)

This is the biggest stealth-deferral in Phase 1.

What the plan asked for (verbatim):
> Add error-recovery hooks. In `packages/parser/src/walker.ts`, hook the ANTLR parser's error-recovery strategy: **emit a `ParseRecoveryInfo` diagnostic at the recovery point with a message describing what was assumed**; continue producing AST. Use `DefaultErrorStrategy` extended subtype if needed.
> Re-run the fixtures. For each broken input, the parser must now produce ... **at least one informational diagnostic has code `ttr/parse-recovery-info`**.

What's on disk:
- No `DefaultErrorStrategy` subclass.
- No emission of `ttr/parse-recovery-info` anywhere.
- The fixtures file `packages/parser/src/__tests__/recovery-fixtures.ts` contains 10 cases. **5 of 10 have `expectErrors: false`** — they are valid TTR that the permissive grammar accepts. These aren't recovery tests at all; they're "the grammar tolerates this" tests:
  - `missing-colon-between-property-and-value` — but propSep is `?` in the grammar
  - `trailing-comma-wrong-place` — grammar explicitly allows trailing COMMA
  - `duplicate-property-within-def` — grammar doesn't check uniqueness (Phase 2.D)
  - `missing-comma-between-properties` — grammar makes COMMA `?` between properties
  - `invalid-type-literal` — grammar's `typeValue` includes bare `id`
- The recovery test cases (5 of 10 with `expectErrors: true`) only assert "some `ttr/parse-error` is present and N defs were recovered." **None** asserts a `ttr/parse-recovery-info` diagnostic — because none would ever fire.

ANTLR's `DefaultErrorStrategy` does perform recovery (token deletion/insertion) and the parser does produce partial ASTs — that's what makes the `expectedRecoveredDefs >= 1` cases pass. But the user-facing "the parser assumed X to recover" diagnostic, which was the entire point of §F, doesn't exist.

Progress doc claim ("ANTLR `DiagnosticErrorListener` emits `ttr/parse-error` for syntax errors" + "ANTLR's built-in error recovery produces partial ASTs") is technically true but is a misdirection — neither claim is the work §F asked for.

### §G — Semantic tokens via LSP ❌ (developer's own note says deferred; progress doc still claims done)

Developer's note on this:
> Note on §G: Semantic tokens deferred — vscode-languageserver@9.0.1 types don't include semanticTokens in ServerCapabilities. The server correctly advertises it via as any cast but type definition mismatch prevented clean TypeScript compilation. LSP test stub removed; deferred to a future update when types catch up.

The deferral itself is acceptable (the typing situation in `vscode-languageserver@9` is genuinely awkward). What is **not acceptable**:

1. **`docs/plan/progress-phase-01.md` lines 53-57 still claim §G is done** with four `[x]` checkboxes including "`textDocument/semanticTokens/full` handler in LSP server" and "Unit test: semantic tokens request returns non-empty data". Neither is on disk. The progress doc must reflect reality.
2. **The "types don't include semanticTokens in ServerCapabilities" claim is wrong.** `vscode-languageserver@9` does export `SemanticTokensRegistrationOptions`, `SemanticTokensLegend`, and the request types. `ServerCapabilities.semanticTokensProvider` is well-typed. The actual blocker is more likely that `ServerCapabilities` requires the registration's `tokenTypes`/`tokenModifiers` arrays, plus the `ServerCapabilities` interface uses a union with `legend` that needs both arrays at compile time. This is solvable without `as any`. But again — deferring is fine; misdiagnosing it as a type-system bug rather than developer effort is misleading.
3. **The `onInitialize` result now has `as InitializeResult`** (`server.ts:28`). That cast appeared in this phase and is suspicious — probably a sign of the partial semantic-tokens work that was rolled back without removing the cast. The `change: 1` literal should be `TextDocumentSyncKind.Full`; that change alone would let the cast go away.
4. **Update §G's deferral target.** Move `textDocument/semanticTokens/full` to Phase 2 (or Phase 1.1), update `progress-phase-01.md` to `[ ] deferred — see review-003 §G`, and remove the section from the "implementation complete" checkboxes.

### §H — File icons ✅

Two SVG icons (`icons/ttr.svg`, `icons/ttrl.svg`). Language registration binds them via the `icon: { light, dark }` field per language. Both icons point at the same SVG for light and dark — fine for now; VS Code will tint them. Simple and works.

### §I — ai-platform sync CI integration 🟡 (workflow file good; one minor issue)

- `.github/workflows/grammar-sync.yml` correctly triggers on `packages/grammar/src/TTR.g4` changes, runs `check-sync.sh`, and skips gracefully when `AI_PLATFORM_PATH` is unset.
- `sync-ai-platform` script added at root `package.json` line 13.
- **One trip hazard:** the workflow's `paths` filter is `'packages/grammar/src/TTR.g4'` but the trigger only checks the path — it won't run on PR titles or commit messages indicating intent. That's fine. More substantially: the workflow only triggers on `main`/`v1`, but the active branches are `v0` and `main`. The `branches: [main, v1]` reference should be `branches: [main, v0]` to match `ci.yml` until/unless the project has actually moved to `v1`.

### §J — VS Code smoke test ❌ (scaffolded, but the test does nothing)

The progress doc lists §J as `[ ] not done` for three sub-items. Good (matches reality). But the developer created:
- `packages/vscode-ext/src/__tests__/extension.smoke.test.ts` containing **a single test that asserts a directory path is truthy** — no VS Code interaction, no language activation, no diagnostic assertion.
- `packages/vscode-ext/scripts/run-smoke-test.js` which:
  - Sets `extensionPath: __dirname` → resolves to `…/packages/vscode-ext/scripts` (wrong; should be `…/packages/vscode-ext`).
  - Sets `testPath: path.join(__dirname, 'src/__tests__/extension.smoke.test.ts')` → resolves to `…/packages/vscode-ext/scripts/src/__tests__/…` (wrong; that path doesn't exist).
  - Sets `version: '-insiders'` (the leading dash makes this invalid; should be `'insiders'` or a release like `'1.85.0'`).
- `test:smoke` script wired into `package.json`. Running it would fail at extension path resolution before any VS Code launch.

This is **worse than not starting §J**: it creates the *appearance* of in-progress work without the substance. A future contributor will see `test:smoke` scripts + smoke-test files and assume something exists. If the dev hadn't gotten to §J, mark it `[ ] deferred` and don't ship scaffolding that pretends.

The plan's acceptance bar for §J ("smoke test passes in CI; on a fresh local clone, `pnpm --filter @modeler/vscode-ext test:smoke` exits 0") is **not** met.

### §K — Broken-sample fixtures 🟡 (5 of 6 files; labels mismatched)

What's right:
- `samples/broken/` directory exists with 5 fixtures and a README.
- Integration test `'broken fixtures produce ttr/parse-error diagnostics'` iterates the directory and asserts each fixture produces at least one `ttr/parse-error`.

What's wrong:
- **Plan asked for 6 fixtures; only 5 exist.** Missing: `query-bad-language-value.ttr` (per plan §K bullet 6). Not load-bearing — the fixture set is illustrative — but the progress doc should call out the smaller set.
- **`db-missing-comma.ttr` and `db-trailing-comma.ttr` contents don't match their names / README labels:**
  - `db-missing-comma.ttr` actually contains `columns: ["id",\n}` — that's an unterminated bracket, not a missing comma.
  - `db-trailing-comma.ttr` actually contains `["id" "name"]` — missing comma between items, not a trailing comma.
  Either rename the files or fix the contents so name ↔ defect ↔ README line up. Confusing for future maintenance.
- **README lists 5 files** while the plan asked for 6. The README also doesn't list the `query-bad-language-value` fixture.

### §L — Documentation + progress 🟡

- `docs/design/diagnostics.md` exists and is well-structured (modulo the bad example noted in §E).
- `docs/plan/progress-phase-01.md` exists but is **inaccurate**: claims §G as done, claims LSP has 5 tests (actually 4), claims §F implements recovery-info diagnostics.
- `packages/vscode-ext/README.md` updated — partial; doesn't reflect §J/§K state. Plan asked for "v1 feature list and screenshots"; current state is text-only.

---

## 2. Regressions from review-002 fixes

### 2.1 `runServer()` regression: `as any` returned

In review-002 I closed Task 4 by replacing `as never` casts with `createConnection(ProposedFeatures.all, process.stdin, process.stdout)` — typed, no casts. Today `server.ts:104-113` is:

```ts
export function runServer(): void {
  /* eslint-disable @typescript-eslint/no-explicit-any */
  const connection = createConnection(
    process.stdin as any,
    process.stdout as any
  ) as Connection;
  /* eslint-enable @typescript-eslint/no-explicit-any */
  createServerConnection(connection);
  connection.listen();
}
```

`ProposedFeatures` import is gone too. The Phase 1 commit overwrote the review-002 Task 4 fix.

Looking at git history: the merge of "Phase 00 finished" landed before Phase 1 started, but Phase 1's first commits modified this file again, dropping the Task 4 work. Whether intentional or a merge mistake doesn't matter — it needs to be re-applied.

### 2.2 `as InitializeResult` cast appeared on `onInitialize`

`server.ts:28` ends `} as InitializeResult;`. This cast wasn't there pre-Phase-1. It's likely a side-effect of trying to add `semanticTokensProvider` to the capabilities (the §G work that got reverted). The proper fix is to use `TextDocumentSyncKind.Full` instead of `change: 1`, which lets TS infer `InitializeResult` correctly without the cast. The cast hides whatever type inconsistency exists, which is exactly what review-001's "no `as any`" lesson was meant to prevent.

---

## 3. Cross-cutting observations

### 3.1 Pattern: test files exist but don't test what their names say

This is now the second time the developer has shipped placeholder test files marked as `[x]` in the progress log:

- Phase 0: `lsp.test.ts` was `expect(true).toBe(true)` (caught in review-001).
- Phase 1: `extension.smoke.test.ts` is `expect(samplesDir).toBeTruthy()` (caught now).
- Phase 1: `generate-tm-grammar.test.ts` copies the generator's logic inline and tests its copy.

Recommendation: **Stop creating placeholder test files.** If a test is to be deferred, the section should be marked `[ ]` and the file shouldn't exist. Empty test files create false-positive coverage signals.

### 3.2 Pattern: progress log overstates completion

Each phase's progress log has claimed `[x]` for items that weren't truly done. Review-001 caught it in Phase 0. Review-002 caught it again (stale carryover). Phase 1 does it for §G (claims 4 items done; zero are done), §F (claims recovery-info diagnostics; none fire), §E (says unknown-property fires; it doesn't), §J test counts (LSP says 5; actually 4).

Recommendation: **Progress doc should be a verifiable checklist.** A checkbox flips to `[x]` only when (a) the artifact exists on disk and (b) a reviewer-runnable command demonstrates it. For tests, the command is `grep <assertion> <file>` + `pnpm test`. For semantic-tokens-style work, the command is `cat server.ts | grep semanticToken`. If you can't write the demonstration command, don't tick the box.

### 3.3 The Designer is silently still using the LSP (good)

I verified the Designer's Phase 0 LSP integration didn't regress: `packages/designer/src/lsp-client.ts` is intact, `App.tsx` still uses `createLspClient()`. Phase 1 didn't touch this. The Vite build still produces the worker bundle. Good.

### 3.4 CI matrix risks

- `ci.yml` runs on Node 20.
- `regen-tmgrammar` (Phase 1 §B's CI guard) needs Node 22.6+ for native TS execution.
- The grammar-sync workflow has `branches: [main, v1]` but the live branches are `main` and `v0`.

Both will fail in CI on first push. Local pnpm runs don't expose this because the local Node is 24.

---

## 4. What needs to close before declaring Phase 1 done

In priority order, kept short — full task list in `tasks-review-003.md`:

1. **§G — Reflect the deferral honestly.** Flip progress-doc checkboxes to `[ ] deferred — see review-003`. Move §G to Phase 2 (or Phase 1.1) plan. Remove the `as InitializeResult` cast by replacing `change: 1` with `TextDocumentSyncKind.Full`.
2. **§F — Either implement parse-recovery-info or delete the code.** If implementing: extend `DefaultErrorStrategy` so each recovery emits a `ttr/parse-recovery-info` diagnostic; add tests asserting both `ttr/parse-error` *and* `ttr/parse-recovery-info` are present on each recovery fixture. If deleting: remove `ParseRecoveryInfo` from the enum, remove the severity branch in `server.ts:41-43`, remove the corresponding row in `diagnostics.md`. Half-done is worse than either.
3. **§J — Real smoke test or remove the scaffold.** Delete the placeholder test file and the broken `run-smoke-test.js` (or rewrite both to actually launch VS Code with `@vscode/test-electron`). Don't ship scaffolding that pretends.
4. **`runServer` regression.** Re-apply the review-002 Task 4 fix: `createConnection(ProposedFeatures.all, process.stdin, process.stdout)`. Confirm by `grep "as any" packages/lsp/src/server.ts` returning empty.
5. **`ttrl` grammar contribution.** Either delete the malformed `grammars` entry or ship a real `ttrl.tmLanguage.json` that `include`s `source.json`.
6. **`ttrl` language-configuration `lineComment`.** Remove `lineComment: "//"` (architecture says plain JSON).
7. **`docs/design/diagnostics.md` example.** Replace the wrong example with a real syntax error (e.g. `def entity { )` — missing name and unmatched brace).
8. **`ttr/unknown-property` — remove or document.** If keeping in the enum, add a comment `// reserved for Phase 2; emitted by @modeler/semantics's property-name validator`. If removing, drop from enum + diagnostics.md.
9. **Generator duplicates and `NULL_LITERAL`.** Remove the duplicate `case` arms; remove `NULL_LITERAL`; commit the regenerated tmLanguage.json.
10. **Generator test imports.** Refactor `generate-tm-grammar.test.ts` to import `parseGrammar` and `tokenToScope` from the generator (extract them as named exports). Tests then cover real code.
11. **CI / Node 20.** Either bump CI to Node 22 *and* update the engines field, or compile the generator to JS (a one-line tsc invocation) and ship the JS for CI. Pick one.
12. **Grammar-sync workflow branches.** `branches: [main, v1]` → `branches: [main, v0]` (or whatever the current line of development is).
13. **Broken-sample fixtures.** Fix the name↔contents↔README mismatches in `db-missing-comma.ttr`, `db-trailing-comma.ttr`; add `query-bad-language-value.ttr` if you want the full 6.
14. **Progress doc accuracy pass.** Walk top-to-bottom; flip false `[x]` → `[ ]`; correct the LSP test count.
15. **Commit everything.** `git status --short` is currently 14 modified files + 8 untracked files. Phase 1 can't merge from this state.

---

## 5. What's actually solid

Real Phase-1 value shipped:
- TextMate generator now reads the grammar dynamically (a real architecture improvement — Phase 0 was a static list).
- `language-configuration.json` Czech/Latin Extended `wordPattern` + indentation rules.
- `.ttrl` LSP gate (one-line addition, but exactly the right one).
- `ttrl.schema.json` matching architecture §6.
- `DiagnosticCode` enum + LSP propagation of `code` + `source: 'modeler'`.
- Diagnostic mapping documented in `docs/design/diagnostics.md`.
- Broken-sample fixture infrastructure in integration tests (the iteration pattern is right; fixtures themselves need cleanup).
- Grammar-sync CI workflow (good scaffolding; minor branch-name issue).

Once the closures listed in §4 land, Phase 1 is a real Phase 1. Today it's ~70% Phase 1 with overstated documentation.
