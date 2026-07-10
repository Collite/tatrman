# Review 003 — Task list

Companion to `review-003.md`. Phase 1 ships once all P0 items below close. P1 items are quality cleanup that should land in the same Phase 1 commit. P2 items can be folded into Phase 2's first task.

For each task: do exactly what the steps say and run the verification command. Tick the box only when the command exits 0 AND a quick read-through confirms the intent. Stop writing placeholder test files — if a section is deferred, mark its box `[ ]` and don't create scaffolding that suggests otherwise.

---

## P0 — Block "Phase 1 done"

### Task 1 — Mark §G semantic tokens as deferred (truthfully)

**Why:** Developer's note says §G was deferred; `docs/plan/progress-phase-01.md` lines 53-57 still claim it's done with four `[x]` checkboxes. See `review-003.md` §1G.

- [ ] Open `docs/plan/progress-phase-01.md`. Find the `## Section G — Semantic tokens via LSP` block.
- [ ] Replace all four `[x]` with `[ ]`.
- [ ] Add at the end of the section:
      ```
      **Status:** Deferred — see review-003 §1G. `vscode-languageserver@9`'s
      `ServerCapabilities.semanticTokensProvider` type is workable but the
      partial work was rolled back. Move to Phase 1.1 or Phase 2.A.
      ```
- [ ] In the "Test Results (intermediate)" block at the bottom, change `packages/lsp: 5 tests passed (3 original + 1 ttrl gating + 1 semantic tokens)` to `packages/lsp: 4 tests passed (3 original + 1 ttrl gating)`. Verify by running `pnpm --filter @modeler/lsp test` — the count must be 4.
- [ ] Add a "Deferred to Later Phases" row: `| Semantic tokens (textDocument/semanticTokens/full) | Phase 1.1 or Phase 2.A |`
- [ ] Open `docs/plan/tasks-phase-01-foundation.md`. In §G's checklist, mark every box `[ ] deferred to Phase 1.1 — see review-003`. Do not delete the section; future Phase 1.1 work will pick it up.

**Verification:**
```bash
grep -n "semantic" docs/plan/progress-phase-01.md  # every line shows "[ ]" not "[x]"
pnpm --filter @modeler/lsp test 2>&1 | grep "Tests"  # must say "4 passed (4)", not 5
```

### Task 2 — Remove the `as InitializeResult` cast

**Why:** The cast was added during the rolled-back §G work and now hides a real type issue: `change: 1` should be the enum value `TextDocumentSyncKind.Full`. With the proper enum value, TS infers the return type and the cast is unnecessary. See `review-003.md` §2.2.

- [ ] Open `packages/lsp/src/server.ts`.
- [ ] Add `TextDocumentSyncKind` to the import block from `vscode-languageserver`:
      ```ts
      import {
        InitializeParams,
        InitializeResult,
        TextDocuments,
        TextDocumentSyncKind,
        Diagnostic,
        DiagnosticSeverity,
        TextDocumentChangeEvent,
        Connection,
      } from 'vscode-languageserver';
      ```
- [ ] In `onInitialize`, change `change: 1,` to `change: TextDocumentSyncKind.Full,`.
- [ ] Delete the `as InitializeResult` cast at the end of the return statement.
- [ ] Verify the LSP test still passes — it asserts `change` equals `1`, which is the numeric value of `TextDocumentSyncKind.Full`, so it should keep passing.

**Verification:**
```bash
grep "as InitializeResult\|change: 1" packages/lsp/src/server.ts  # both must be absent
pnpm --filter @modeler/lsp build && pnpm --filter @modeler/lsp test  # exit 0
```

### Task 3 — Re-apply the review-002 `runServer` fix (regression)

**Why:** `server.ts:104-113` uses `process.stdin as any, process.stdout as any` wrapped in `/* eslint-disable */`. Review-002 Task 4 already fixed this to use `createConnection(ProposedFeatures.all, process.stdin, process.stdout)` — no casts, no eslint-disable. The fix was overwritten in Phase 1. See `review-003.md` §2.1.

- [ ] In `packages/lsp/src/server.ts`, add `ProposedFeatures` to the import from `'vscode-languageserver/lib/node/main.js'`:
      ```ts
      import { createConnection, ProposedFeatures } from 'vscode-languageserver/lib/node/main.js';
      ```
- [ ] Replace the `runServer` function body with:
      ```ts
      export function runServer(): void {
        const connection = createConnection(ProposedFeatures.all, process.stdin, process.stdout);
        createServerConnection(connection);
        connection.listen();
      }
      ```
- [ ] Delete both `/* eslint-disable */` and `/* eslint-enable */` comments.
- [ ] Delete `as Connection` (no cast needed — the overload returns a typed `_Connection`).

**Verification:**
```bash
grep -c "as any" packages/lsp/src/server.ts  # must print 0
grep -c "eslint-disable" packages/lsp/src/server.ts  # must print 0
pnpm --filter @modeler/lsp build && pnpm --filter @modeler/lsp test && pnpm --filter @modeler/lsp lint
```

### Task 4 — §F: emit `ttr/parse-recovery-info` or remove the code

**Why:** The diagnostic code is defined and mapped in `server.ts:41-43` but no parser code ever emits it. `recovery-fixtures.ts` does not assert recovery-info; 5 of 10 fixtures aren't even recovery cases. See `review-003.md` §1F.

Pick **one** of A or B and do it fully. Do not do half of A.

#### Option A — Implement the recovery hook (the plan's intent)

- [ ] In `packages/parser/src/walker.ts`, add an import:
      ```ts
      import { DefaultErrorStrategy } from 'antlr4ng';
      ```
- [ ] Define a class right above `parseString`:
      ```ts
      class RecoveryReportingStrategy extends DefaultErrorStrategy {
        constructor(private readonly errors: ParseError[], private readonly fileLabel: string) {
          super();
        }

        recover(recognizer: Parser, e: RecognitionException): void {
          const token = e.offendingToken;
          this.errors.push({
            code: DiagnosticCode.ParseRecoveryInfo,
            message: `Parser recovered at token '${token?.text ?? ''}' — partial AST follows`,
            severity: 'warning',
            source: {
              file: this.fileLabel,
              line: token?.line ?? 1,
              column: token?.column ?? 0,
              endLine: token?.line ?? 1,
              endColumn: (token?.column ?? 0) + (token?.text?.length ?? 1),
              offsetStart: token?.start ?? 0,
              offsetEnd: (token?.stop ?? -1) + 1,
            },
          });
          super.recover(recognizer, e);
        }
      }
      ```
      Add `Parser` and `RecognitionException` to the `antlr4ng` import block.
- [ ] In `parseString`, after `parser.removeErrorListeners()`, install the strategy:
      ```ts
      parser.errorHandler = new RecoveryReportingStrategy(errors, fileLabel);
      ```
- [ ] Update `recovery-fixtures.ts`: split into two arrays. Remove the 5 cases that have `expectErrors: false` (they belong in a separate `permissive-grammar-fixtures.ts` if you want them). Keep the 5 that do produce errors. For each, set `expectRecoveryInfo: true` if the parser actually recovers and produces a partial AST.
- [ ] In `parser.test.ts`, extend the recovery loop to also assert:
      ```ts
      const hasRecoveryInfo = result.errors.some((e) => e.code === DiagnosticCode.ParseRecoveryInfo);
      if (fixture.expectRecoveryInfo) {
        expect(hasRecoveryInfo, `"${fixture.name}" should have at least one ttr/parse-recovery-info`).toBe(true);
      }
      ```

**Verification (Option A):**
```bash
grep -c "ParseRecoveryInfo" packages/parser/src/walker.ts  # must be >= 1 (the emission)
pnpm --filter @modeler/parser test 2>&1 | grep "Tests"  # all pass
```

#### Option B — Remove the unused code

- [ ] Open `packages/parser/src/diagnostics.ts`. Delete the `ParseRecoveryInfo = 'ttr/parse-recovery-info',` line.
- [ ] Open `packages/lsp/src/server.ts`. Delete the `err.code === 'ttr/parse-recovery-info' ? DiagnosticSeverity.Information : ...` branch; replace with simple `DiagnosticSeverity.Error` (or `Warning` based on `err.severity`).
- [ ] Open `docs/design/diagnostics.md`. Delete the `ttr/parse-recovery-info` row from the Foundation Tier table and the corresponding section.
- [ ] Split `recovery-fixtures.ts` per Option A's bullet 4 above (the cleanup is the same regardless).

**Verification (Option B):**
```bash
grep -rn "ParseRecoveryInfo\|parse-recovery-info" packages/ docs/  # must be empty
pnpm -r build && pnpm -r test
```

### Task 5 — §J: Real smoke test or remove the scaffold

**Why:** `extension.smoke.test.ts` is `expect(samplesDir).toBeTruthy()` — no VS Code. `run-smoke-test.js` has three broken path/version arguments. Either fix or remove. See `review-003.md` §1J.

Pick **one** of A or B.

#### Option A — Real smoke test

- [ ] Open `packages/vscode-ext/scripts/run-smoke-test.js`. Replace with:
      ```js
      #!/usr/bin/env node
      import { runTests } from '@vscode/test-electron';
      import path from 'path';
      import { fileURLToPath } from 'url';

      const __dirname = path.dirname(fileURLToPath(import.meta.url));
      const extensionDevelopmentPath = path.resolve(__dirname, '..');
      const extensionTestsPath = path.resolve(extensionDevelopmentPath, 'dist/__tests__/extension.smoke.test.js');
      const samplePath = path.resolve(__dirname, '../../../samples/v1-metadata/er.ttr');

      try {
        await runTests({
          extensionDevelopmentPath,
          extensionTestsPath,
          launchArgs: [samplePath, '--disable-extensions'],
          version: 'stable',
        });
      } catch (err) {
        console.error('Smoke test failed:', err);
        process.exit(1);
      }
      ```
- [ ] Rewrite `packages/vscode-ext/src/__tests__/extension.smoke.test.ts` to use VS Code APIs (it runs inside the EDH, so `vscode` is available):
      ```ts
      import * as vscode from 'vscode';
      import * as path from 'path';
      import * as assert from 'assert';

      export async function run(): Promise<void> {
        const sample = vscode.workspace.workspaceFolders?.[0]?.uri ?? vscode.Uri.file(process.argv.find(a => a.endsWith('.ttr'))!);
        const doc = await vscode.workspace.openTextDocument(sample);
        await vscode.window.showTextDocument(doc);

        assert.strictEqual(doc.languageId, 'ttr', 'expected languageId to be "ttr"');

        await new Promise((r) => setTimeout(r, 1500));

        const broken = await vscode.workspace.openTextDocument({
          language: 'ttr',
          content: 'def entity {',
        });
        await vscode.window.showTextDocument(broken);
        await new Promise((r) => setTimeout(r, 1500));
        const diags = vscode.languages.getDiagnostics(broken.uri);
        assert.ok(diags.length > 0, 'expected at least one diagnostic on broken input');
        assert.ok(diags.some((d) => d.code === 'ttr/parse-error'), 'expected a ttr/parse-error diagnostic');
      }
      ```
- [ ] Adjust `tsconfig.json` so smoke tests compile to `dist/__tests__/`. The exact tsconfig change is: don't exclude `src/__tests__/**/*` (the compiled `.js` files are what `extensionTestsPath` points at).
- [ ] Add `@vscode/test-electron` to `devDependencies` if not present (it already is — line 33).
- [ ] Add a CI job in `.github/workflows/ci.yml` that runs smoke tests on Linux with xvfb:
      ```yaml
      - name: Install xvfb (Linux)
        run: sudo apt-get install -y xvfb
      - name: Smoke test
        run: xvfb-run -a pnpm --filter @modeler/vscode-ext test:smoke
      ```

#### Option B — Remove the scaffold and mark §J deferred

- [ ] Delete `packages/vscode-ext/src/__tests__/extension.smoke.test.ts`.
- [ ] Delete `packages/vscode-ext/scripts/run-smoke-test.js`.
- [ ] Remove the `test:smoke` script from `packages/vscode-ext/package.json`.
- [ ] In `docs/plan/progress-phase-01.md` §J, replace the bullets with:
      ```
      **Status:** Deferred — see review-003 §1J. Move to Phase 1.1.
      ```
- [ ] In `docs/plan/tasks-phase-01-foundation.md` §J, mark every box `[ ] deferred to Phase 1.1 — see review-003`.

**Verification (either A or B):**
```bash
# Option A:
pnpm --filter @modeler/vscode-ext test:smoke  # exit 0 (requires xvfb on Linux)
# Option B:
ls packages/vscode-ext/src/__tests__/ packages/vscode-ext/scripts/run-smoke-test.js 2>&1 | grep "smoke\|No such"  # both absent
grep "test:smoke" packages/vscode-ext/package.json  # absent
```

### Task 6 — Fix the `ttrl` grammar contribution

**Why:** `packages/vscode-ext/package.json` lines 66-70 map `ttrl` to `scopeName: "source.json"` but `path: "./syntaxes/ttr.tmLanguage.json"`. VS Code will use the TTR grammar but label it as `source.json` scope — producing wrong highlighting. See `review-003.md` §1D.

Pick **one** of A or B.

#### Option A — Ship a real `ttrl.tmLanguage.json` that includes `source.json`

- [ ] Create `packages/vscode-ext/syntaxes/ttrl.tmLanguage.json` with:
      ```json
      {
        "scopeName": "source.ttrl",
        "fileTypes": ["ttrl"],
        "name": "TTR Layout",
        "patterns": [{ "include": "source.json" }]
      }
      ```
- [ ] In `packages/vscode-ext/package.json`, change the `ttrl` grammar entry:
      ```json
      { "language": "ttrl", "scopeName": "source.ttrl", "path": "./syntaxes/ttrl.tmLanguage.json" }
      ```

#### Option B — Remove the grammar entry entirely

- [ ] In `packages/vscode-ext/package.json`, delete the `ttrl` entry from `contributes.grammars` (keep `jsonValidation` for schema validation; `.ttrl` files will fall back to plain-text highlighting).
- [ ] Note in `docs/plan/progress-phase-01.md` §D: "ttrl highlighting deferred — relying on schema validation only in Phase 1."

**Verification:**
```bash
node -e "const m=require('./packages/vscode-ext/package.json'); const g=m.contributes.grammars.find(x=>x.language==='ttrl'); console.log(g ? g.scopeName + ' / ' + g.path : 'absent');"
# Option A: should print "source.ttrl / ./syntaxes/ttrl.tmLanguage.json"
# Option B: should print "absent"
```

### Task 7 — Fix `language-configuration-ttrl.json` to match a JSON dialect

**Why:** Architecture §6 says `.ttrl` is plain JSON. The config currently declares `lineComment: "//"` (JSON-doesn't-support-comments inconsistency). See `review-003.md` §1D.

- [ ] Open `packages/vscode-ext/language-configuration-ttrl.json`.
- [ ] Delete the `"comments"` block entirely (or change `lineComment` to `null` — VS Code accepts both as "no comment support").
- [ ] If you want to support JSONC-style comments in `.ttrl` (the architecture is silent, so this is a design call): leave the `lineComment` in, but document the choice in `progress-phase-01.md` §D as "Phase 1 ships JSONC-style comments to support inline annotations".

**Verification:**
```bash
node -e "const m=require('./packages/vscode-ext/language-configuration-ttrl.json'); console.log(JSON.stringify(m.comments ?? 'none'));"
# Should print 'none' or a documented JSONC choice
```

### Task 8 — Fix the `diagnostics.md` example

**Why:** Line 24-27 says `def entity foo { bar: int }` triggers `ttr/parse-error`. It doesn't — the grammar accepts that input. See `review-003.md` §1E.

- [ ] Open `docs/design/diagnostics.md`.
- [ ] Replace the `### ttr/parse-error` example with a real syntax error. Suggested:
      ```
      Example:
      ```
      def entity foo {
      ```
      The opening `{` has no matching `}`. The parser's ANTLR error listener emits `ttr/parse-error` at end-of-file.
- [ ] If §F Option A was taken, also add an example for `ttr/parse-recovery-info` showing what the diagnostic looks like.
- [ ] If §F Option B was taken (code removed), delete the `ttr/parse-recovery-info` row from the table and the section.

**Verification:**
```bash
grep "bar: int" docs/design/diagnostics.md  # must be absent
```

### Task 9 — `ttr/unknown-property` — remove or annotate

**Why:** Defined but never emitted; the grammar accepts any property name. See `review-003.md` §1E.

Pick **one** of A or B.

#### Option A — Remove

- [ ] Delete `UnknownProperty = 'ttr/unknown-property',` from `packages/parser/src/diagnostics.ts`.
- [ ] Delete the `ttr/unknown-property` row from `docs/design/diagnostics.md`'s Foundation Tier table.
- [ ] Delete the corresponding section.

#### Option B — Annotate as Phase 2

- [ ] In `packages/parser/src/diagnostics.ts`, add a comment above the line:
      ```ts
      /** Reserved for Phase 2 — emitted by @modeler/semantics's property validator. */
      UnknownProperty = 'ttr/unknown-property',
      ```
- [ ] In `docs/design/diagnostics.md`, in the Foundation Tier table, change `Trigger` from "Property name not recognized by the grammar..." to "Reserved for Phase 2 — will be emitted by the semantics layer when it lands." Add a note that no Phase 1 code path produces this.

**Verification:**
```bash
# Option A:
grep "unknown-property\|UnknownProperty" packages/ docs/ -rn  # absent
# Option B:
grep "Reserved for Phase 2" packages/parser/src/diagnostics.ts docs/design/diagnostics.md  # both present
```

### Task 10 — Generator: deduplicate `case` arms and remove `NULL_LITERAL`

**Why:** `generate-tm-grammar.ts` has duplicate `case 'QUERY'` (lines 51 & 66), duplicate `case 'BOOLEAN_LITERAL'` (lines 138 & 140), and a `case 'NULL_LITERAL'` for a token that doesn't exist. See `review-003.md` §1B.

- [ ] In `packages/vscode-ext/scripts/generate-tm-grammar.ts`, in `tokenToScope`:
  - Delete the duplicate `case 'QUERY':` at line 66 (keep the one at line 51).
  - Delete the duplicate `case 'BOOLEAN_LITERAL':` at line 140 (keep the one at line 138).
  - Delete the `case 'NULL_LITERAL':` at line 139.
- [ ] Run `cd packages/vscode-ext && pnpm run regen-tmgrammar` to regenerate.
- [ ] If the generated `syntaxes/ttr.tmLanguage.json` changed, commit the new version.

**Verification:**
```bash
grep -c "case 'QUERY':" packages/vscode-ext/scripts/generate-tm-grammar.ts  # 1
grep -c "case 'BOOLEAN_LITERAL':" packages/vscode-ext/scripts/generate-tm-grammar.ts  # 1
grep -c "NULL_LITERAL" packages/vscode-ext/scripts/generate-tm-grammar.ts  # 0
```

### Task 11 — Generator tests: import from the source, don't re-implement

**Why:** `scripts/__tests__/generate-tm-grammar.test.ts` copies the generator's logic inline (lines 10-137). Tests cover their own code, not the generator. See `review-003.md` §1B.

- [ ] In `packages/vscode-ext/scripts/generate-tm-grammar.ts`, extract `parseGrammar` and `tokenToScope` as named exports (currently they're file-scoped):
      ```ts
      export function parseGrammar(...) { ... }
      export function tokenToScope(...) { ... }
      ```
- [ ] In `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts`, delete the local copies of `parseGrammar` and `tokenToScope` (lines 10-137).
- [ ] At the top, add:
      ```ts
      import { parseGrammar, tokenToScope } from '../generate-tm-grammar.js';
      ```
- [ ] Run the tests; they must still pass.

**Verification:**
```bash
grep -c "^function parseGrammar\|^function tokenToScope" packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts  # must be 0
pnpm --filter @modeler/vscode-ext test 2>&1 | grep "Tests"  # all 6 still pass
```

### Task 12 — CI: handle Node 20 vs native TS execution

**Why:** `ci.yml` runs the regen-tmgrammar step on Node 20, which doesn't natively run `.ts` files. CI will fail. See `review-003.md` §3.4.

Pick **one** of A or B.

#### Option A — Bump CI Node to 22

- [ ] In `.github/workflows/ci.yml`, change `node-version: '20'` to `node-version: '22'`. (Node 22 enables `--experimental-strip-types` by default starting 22.6.)
- [ ] In the root `package.json`, update `engines.node` from `">=20.0.0"` to `">=22.6.0"`.
- [ ] Verify by pushing a no-op PR; the CI step "regen-tmgrammar" must pass.

#### Option B — Stay on Node 20; compile the generator to JS

- [ ] Add a build step for the generator: in `packages/vscode-ext/package.json` scripts, add:
      ```
      "build-generator": "tsc scripts/generate-tm-grammar.ts --outDir scripts --module nodenext --target es2022"
      ```
- [ ] Change the existing `regen-tmgrammar` script to invoke the compiled JS:
      ```
      "regen-tmgrammar": "pnpm run build-generator && node scripts/generate-tm-grammar.js"
      ```
- [ ] Make `build` depend on `build-generator` so CI sees the same code path.

**Verification:**
```bash
# Option A:
grep "node-version" .github/workflows/ci.yml  # must print '22'
# Option B:
ls packages/vscode-ext/scripts/generate-tm-grammar.js  # must exist after a build
```

### Task 13 — Grammar-sync workflow branches

**Why:** `.github/workflows/grammar-sync.yml` triggers on `branches: [main, v1]` but live development is `main` and `v0`. The workflow will never run. See `review-003.md` §1I.

- [ ] Open `.github/workflows/grammar-sync.yml`.
- [ ] Change both `branches:` lines (the `push` and `pull_request` triggers) from `[main, v1]` to `[main, v0]`. Alternatively, drop the branch filter on `pull_request` (`pull_request:` alone, triggered by any PR), and keep `push: branches: [main, v0]`.

**Verification:**
```bash
grep "branches:" .github/workflows/grammar-sync.yml  # both lines show v0
```

### Task 14 — Broken-sample fixture name ↔ content alignment

**Why:** `db-missing-comma.ttr` has unterminated brackets, not a missing comma; `db-trailing-comma.ttr` has missing commas, not a trailing one. README labels don't match contents. See `review-003.md` §1K.

- [ ] Inspect each broken fixture:
      ```bash
      for f in samples/broken/*.ttr; do echo "=== $f ==="; cat "$f"; done
      ```
- [ ] For each file, either rename the file to describe the actual defect or rewrite the contents to match the name. Pick whichever is easier; aim for: file name = README label = actual defect.
- [ ] Update `samples/broken/README.md`'s table accordingly.
- [ ] Optional: add `query-bad-language-value.ttr` containing `language: NOTREAL` to match the plan's full 6-fixture set.

**Verification:**
```bash
pnpm --filter @modeler/integration-tests test 2>&1 | grep "broken fixtures"  # passes
# Then visually: for each row in the README table, the file's contents demonstrate the labeled defect.
```

### Task 15 — Progress-doc accuracy pass

**Why:** The doc claims things that aren't true. See `review-003.md` §3.2.

- [ ] Open `docs/plan/progress-phase-01.md`.
- [ ] Walk every `[x]` checkbox; for each, verify the artifact exists or the behavior demonstrably works. Flip any false `[x]` to `[ ]`.
- [ ] In §E, change `LSP propagates code and source on every Diagnostic` claim if it's not yet true post-Task 2/3.
- [ ] In §F, replace the misleading "ANTLR's built-in error recovery produces partial ASTs" claim with the truth: either "implemented per review-003 Task 4 Option A" or "ttr/parse-recovery-info code removed — never emitted in Phase 1; recovery is ANTLR-default only".
- [ ] In §H, confirm the icons render in a real EDH session (the plan asked for this; if not done by hand, mark `[ ]`).
- [ ] In §I, note the grammar-sync workflow branches were corrected per review-003 Task 13.
- [ ] In §J, replace the unchecked bullets with either "Implemented per review-003 Task 5 Option A" or "Deferred — see review-003 §1J".
- [ ] Update the "Test Results" block to reflect actual current test counts after this review's tasks land.

**Verification:**
```bash
# Cross-check: every [x] section must have at least one git-tracked file produced by it.
grep -n "^- \[x\]" docs/plan/progress-phase-01.md
# Then, for each line, confirm the matching file/feature is present.
```

### Task 16 — Commit Phase 1 work

**Why:** `git status --short` shows 14 modified files + 8 untracked. Phase 1 can't merge from this state. See `review-003.md` §0.

- [ ] Stage modifications and new files explicitly (don't `git add -A`; review what's going in):
      ```bash
      git add .github/workflows/ci.yml .github/workflows/grammar-sync.yml \
              package.json pnpm-lock.yaml \
              packages/parser/src/{ast,index,walker,diagnostics}.ts \
              packages/parser/src/__tests__/{parser.test,recovery-fixtures}.ts \
              packages/lsp/src/server.ts \
              packages/lsp/src/__tests__/lsp.test.ts \
              packages/vscode-ext/package.json \
              packages/vscode-ext/language-configuration.json \
              packages/vscode-ext/language-configuration-ttrl.json \
              packages/vscode-ext/scripts/generate-tm-grammar.ts \
              packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts \
              packages/vscode-ext/syntaxes/ttr.tmLanguage.json \
              packages/vscode-ext/icons/ packages/vscode-ext/schemas/ \
              packages/vscode-ext/vitest.config.ts \
              tests/integration/src/integration.test.ts \
              docs/design/diagnostics.md docs/plan/progress-phase-01.md \
              samples/broken/ \
              review-003.md tasks-review-003.md
      # Add or remove smoke-test files based on Task 5 choice
      ```
- [ ] Run `git status`. Untracked files: none load-bearing. If `packages/vscode-ext/src/extension.js` (compiled output) somehow reappeared, delete it and confirm it's git-ignored.
- [ ] Commit:
      ```bash
      git commit -m "$(cat <<'EOF'
      Phase 1: Foundation tier (LSP diagnostics, .ttrl, TextMate generator)
      
      Sections complete: A (carryover), B (TextMate generator from grammar),
      C (language config), D (.ttrl LSP gate + schema), E (DiagnosticCode
      enum + LSP propagation), F ([Option chosen]), H (icons), I (grammar-sync
      workflow), K (broken-sample fixtures), L (diagnostics.md).
      
      Sections deferred: G (semantic tokens — vscode-languageserver@9 typing),
      J ([Option chosen]).
      
      See review-003.md for the closure history.
      EOF
      )"
      ```

**Final verification gate:**
```bash
pnpm install --frozen-lockfile
pnpm -r build && pnpm -r test && pnpm -r lint
pnpm --filter @modeler/integration-tests test
git status --short  # must be empty
```

---

## P1 — Quality cleanup (land alongside Phase 1)

### Task 17 — Stop creating placeholder test files

**Why:** Recurring pattern — Phase 0's `expect(true).toBe(true)`, Phase 1's `expect(samplesDir).toBeTruthy()`. Placeholder tests masquerade as coverage. See `review-003.md` §3.1.

- [ ] Add to `docs/plan/progress-phase-01.md` (and future phase progress docs): "**Placeholder rule:** never commit a test file whose body is `expect(true|truthy).toBe(true|truthy)` or equivalent. If a test cannot yet be written, the file does not exist."
- [ ] Re-audit `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts` after Task 11 — confirm every test actually exercises real code.

### Task 18 — TextMate categories not implemented (`entity.name.tag.ttr`, `variable.other.qname.ttr`)

**Why:** Plan §B listed these but the generator doesn't produce them. They are the most valuable distinct-from-TextMate categories. See `review-003.md` §1B.

- [ ] Either implement these as TextMate `match` rules with regex contexts (begin/end patterns for `def <kind> <NAME>` and dotted-id-with-`\.` separator), or document in `progress-phase-01.md` §B that they are deferred to semantic-tokens (§G, Phase 1.1).

### Task 19 — `language-configuration-ttrl.json`: drop or document `lineComment`

This is folded into Task 7 above. Listed here to flag the design decision needs to be recorded in `progress-phase-01.md` §D regardless of which path Task 7 picks.

---

## P2 — Defer to Phase 2

### Task 20 — Refactor §F's fixtures into two files

Cleanup that can wait. Split `recovery-fixtures.ts` into:
- `recovery-fixtures.ts` (real broken inputs with `expectErrors: true`)
- `permissive-grammar-fixtures.ts` (inputs that look broken but the grammar accepts)

The two files serve different testing purposes and shouldn't share a name.

---

## Definition of done for this review

Phase 1 is done when:

- [ ] Tasks 1–16 (P0) checked.
- [ ] `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm --filter @modeler/integration-tests test` exits 0 on a fresh clone (`git clean -fdx; pnpm install; ...`).
- [ ] `git status --short` is empty.
- [ ] CI run on the Phase 1 PR is green (build, test, lint, regen-tmgrammar guard, grammar-sync — the last skips when ai-platform is absent).
- [ ] `grep "as any" packages/lsp/src/server.ts` returns nothing.
- [ ] `grep "ParseRecoveryInfo" packages/parser/src/walker.ts` either prints the emission line (Option A) or is empty (Option B). No middle state.
- [ ] No file under `packages/*/src/__tests__/` contains a body that only asserts `true` or string truthiness.
- [ ] Progress doc reflects reality.

P1 (Tasks 17–19) and P2 (Task 20) should be folded into Phase 2's first commit.
