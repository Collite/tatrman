# Review 002 — Task list

Companion to `review-002.md`. Three P0 items must close before starting Phase 1; five optional cleanups can be folded in opportunistically.

For each task: do exactly what the steps say. Verify with the provided commands.

---

## P0 — Block "Phase 1 may start"

### Task 1 — Fix `endColumn` calculation (and restore the strict test)

**Why:** `walker.ts:212-229`'s `makeSourceLocation` adds the **span length** to the **end token's column**, producing values larger than the input file is wide. Phase 1 §E (diagnostic taxonomy) and §F (parse-recovery diagnostics) emit AST-source ranges via this code path; their diagnostics will land at impossible columns until this is fixed. Today the regression test (`parser.test.ts:62-72`) asserts only `endColumn > 0`, masking the bug. See `review-002.md` §2.1.

- [ ] Open `packages/parser/src/walker.ts`. Replace the body of `makeSourceLocation` with:
      ```ts
      function makeSourceLocation(
        ctx: { start?: { line: number; column: number; start: number } | null; stop?: { line: number; column: number; start: number; stop: number } | null },
        file: string
      ): SourceLocation {
        const startToken = ctx.start ?? { line: 1, column: 0, start: 0 };
        const stopToken = ctx.stop ?? { line: startToken.line, column: startToken.column, start: startToken.start, stop: startToken.start - 1 };
        const stopTokenLength = stopToken.stop - stopToken.start + 1;
        return {
          file,
          line: startToken.line,
          column: startToken.column,
          endLine: stopToken.line,
          endColumn: stopToken.column + stopTokenLength,
          offsetStart: startToken.start,
          offsetEnd: stopToken.stop + 1,
        };
      }
      ```
- [ ] Open `packages/parser/src/__tests__/parser.test.ts`. Replace the existing test on lines 62-72 with two assertions that actually exercise the math:
      ```ts
      it('parseString("def entity foobar {}") endColumn is the column after the closing brace', () => {
        const result = parseString('def entity foobar {}');
        expect(result.errors).toHaveLength(0);
        const def = result.ast?.definitions[0];
        expect(def).toBeDefined();
        // Input is 20 chars on one line. offsetEnd is exclusive => 20.
        expect(def!.source.offsetEnd).toBe(20);
        // endColumn is "column of the character after the closing }". `}` is at column 19, length 1, so endColumn = 20.
        expect(def!.source.endLine).toBe(1);
        expect(def!.source.endColumn).toBe(20);
      });

      it('multi-line def: endLine and endColumn reflect the last token of the span', () => {
        const result = parseString('def entity foo {\n}\n');
        expect(result.errors).toHaveLength(0);
        const def = result.ast?.definitions[0];
        expect(def).toBeDefined();
        // Closing `}` is on line 2 at column 0; length 1; endColumn = 1.
        expect(def!.source.endLine).toBe(2);
        expect(def!.source.endColumn).toBe(1);
      });
      ```
- [ ] Verify by running the built parser interactively:
      ```bash
      pnpm --filter @modeler/parser build
      node -e "import('./packages/parser/dist/index.js').then(({parseString}) => { const r = parseString('def entity foobar {}'); console.log(JSON.stringify(r.ast.definitions[0].source)); });"
      ```
      Expected output (anywhere in the JSON): `"endColumn":20`, `"offsetEnd":20`. **Not** `"endColumn":39`.
- [ ] Run `pnpm --filter @modeler/parser test`. The two new tests must pass.
- [ ] Run `pnpm -r build && pnpm -r test && pnpm -r lint`. All must exit 0.

### Task 2 — Delete the stray `packages/vscode-ext/src/extension.js`

**Why:** A CommonJS-style `extension.js` sits next to `extension.ts` in `src/`. It is not tracked, not ignored, not part of the tsc flow (rootDir is `src/`, outDir is `dist/`, no `allowJs`). It is leftover from an earlier compile run and confuses the source tree. See `review-002.md` §2.2.

- [ ] Delete the file:
      ```bash
      rm packages/vscode-ext/src/extension.js
      ```
- [ ] Run `pnpm -r build`. Must exit 0 and produce `packages/vscode-ext/dist/extension.js` as the only `extension.js` in the package.

### Task 3 — Commit (or remove) the untracked production-code files

**Why:** `eslint.config.js`, the Designer LSP client, the language-configuration JSON, and the Phase 1 plan are all on disk but not in version control. A fresh clone (which CI does on every push) would fail. See `review-002.md` §2.3.

- [ ] Confirm what is genuinely production-code (must be committed) vs. ephemeral by running `git status`. The expected set to commit:
      ```
      eslint.config.js
      packages/designer/src/lsp-client.ts
      packages/vscode-ext/language-configuration.json
      docs/plan/tasks-phase-01-foundation.md
      review-001.md
      tasks-review-001.md
      review-002.md
      tasks-review-002.md
      CLAUDE.md
      ```
      Plus all the modified files currently in "Changes not staged for commit" (the review-001 fix wave).
- [ ] Stage them explicitly (do **not** use `git add -A` — there is a stray file that Task 2 will remove). For each file in the list above:
      ```bash
      git add <file>
      ```
- [ ] Stage the modified files individually as well (`git add packages/parser/...`, etc.). Run `git status` after each add and confirm the staged set is right.
- [ ] Commit with a message like:
      ```
      review-001 fixes: P0/P1 task list

      Closes the 13 P0 tasks plus the bulk of the P1 follow-ups from
      tasks-review-001.md:
        - grammar target-neutral (Task 1)
        - vscode-ext copy-server (Task 2)
        - server-browser ESM static import (Task 3)
        - Designer LSP client (Task 4)
        - lint toolchain (Task 5)
        - LSP types proper (Task 6)
        - language-configuration.json (Task 7)
        - .vscode/launch.json (Task 8)
        - real LSP tests (Task 9)
        - integration LSP coverage (Task 10)
        - byte offsets on SourceLocation (Task 11)
        - progress-phase-00 updated (Task 13)
        - .gitignore /*.ttrl (Task 15)
        - duplicate version key removed (Task 16)
        - tsconfig exclude cleaned (Task 17)
        - SourceLocation JSDoc (Task 18)
        - grammar/index.ts ESM URL (Task 20)
      ```
- [ ] Run `git status`. The output must be clean (no untracked files except whatever ad-hoc local artefacts the developer doesn't want to commit; none should be load-bearing).
- [ ] **Verify CI replicability** — simulate a fresh clone:
      ```bash
      pnpm install --frozen-lockfile
      pnpm -r build && pnpm -r test && pnpm -r lint
      pnpm --filter @modeler/integration-tests test
      ```
      All four must exit 0.

**Verification gate for P0:**

```bash
pnpm -r build && pnpm -r test && pnpm -r lint && pnpm --filter @modeler/integration-tests test
git status --short    # must be empty (or only contain files the developer intends not to commit)
```

If all of the above pass and Task 1's interactive `node -e ...` check returned `endColumn: 20`, Phase 1 is unblocked.

---

## P1 — Opportunistic cleanup (do alongside Phase 1 §A)

These do not block Phase 1 but each is a 1–5 minute fix. Do them as the first action of Phase 1 §A so the carryover is genuinely done.

### Task 4 — Replace `as never` casts in `runServer()`

**Why:** `server.ts:96` is the last type-system bypass in the LSP. See `review-002.md` §3.1.

- [ ] In `packages/lsp/src/server.ts`, replace `runServer` with:
      ```ts
      export function runServer(): void {
        const connection = createConnection(ProposedFeatures.all);
        createServerConnection(connection);
        connection.listen();
      }
      ```
- [ ] Add `ProposedFeatures` back to the import block at the top of `server.ts`.
- [ ] Run `pnpm --filter @modeler/lsp build && test && lint`. Must exit 0.

### Task 5 — Rename the `@modeler/lsp` internal export

**Why:** `./dist/server.js` is an implementation-detail path leaking into the public exports map. See `review-002.md` §3.2.

- [ ] In `packages/lsp/package.json`, change the third exports entry from:
      ```json
      "./dist/server.js": "./dist/server.js"
      ```
      to:
      ```json
      "./server": {
        "import": "./dist/server.js",
        "types": "./dist/server.d.ts"
      }
      ```
- [ ] In `tests/integration/src/integration.test.ts`, change the import from `'@modeler/lsp/dist/server.js'` to `'@modeler/lsp/server'`.
- [ ] Run `pnpm install` (workspace resolution), then `pnpm -r build && pnpm --filter @modeler/integration-tests test`. Both must exit 0.

### Task 6 — Add `"type": "module"` to root package.json

**Why:** Eliminates the `MODULE_TYPELESS_PACKAGE_JSON` warning on every `pnpm -r lint` run. See `review-002.md` §3.3.

- [ ] In `package.json` (root), add `"type": "module",` after the `"private": true,` line.
- [ ] Verify no script changes are needed: `pnpm -r build && test && lint`. All must exit 0 and the lint warning must be gone.

### Task 7 — Clean stale entries in `progress-phase-00.md`

**Why:** Phase 1 pre-flight expects this file to accurately list outstanding carryover. See `review-002.md` §3.4.

- [ ] In `docs/plan/progress-phase-00.md`, in the "Carried into Phase 1" section:
      - Remove the line `Make Definition a proper discriminated union ...` (done).
      - Remove the line `TextMate grammar token audit ...` (done; no audit needed — all tokens were present).
- [ ] In Section G's "Review-001 fixes applied" subsection, rewrite the TextMate audit line as: "TextMate generator tokens verified against TTR.g4 — every token in `generate-tm-grammar.ts` has a matching lexer rule; no removals needed."
- [ ] In Section I's "Review-001 fixes applied" subsection, add: "Integration test scope: 4 tests (2 parser + 2 LSP). Plan asked for `didOpen` of every sample with a no-diagnostics assertion; current coverage only opens `er.ttr` via LSP. Tighten in Phase 1 §K when broken-fixture infrastructure lands."

### Task 8 — Trim Phase 1 §A's stale carryover checklist

**Why:** Phase 1 §A lists 8 carryover items from review-001 as `[ ]` checkboxes; most are already done. Reading the plan in its current shape would waste a half-day on "is this done?" archaeology. See `review-002.md` §3.5.

- [ ] In `docs/plan/tasks-phase-01-foundation.md` §A:
      - Replace the entire carryover checklist (Tasks 14–22 reproduced as bullets) with a single verification step:
        ```
        - [ ] Confirm review-001 P1/P2 items are reflected on disk:
              - `Definition` is a discriminated union in `packages/parser/src/ast.ts`
              - `.gitignore` uses `/*.ttrl` (root-only)
              - No duplicate `version` keys in `packages/vscode-ext/package.json`
              - `packages/parser/tsconfig.json` has no `exclude` for `src/generated/**`
              - `SourceLocation` carries a JSDoc indexing-convention block
              - `eslint.config.js` ignores `**/generated/**`
              - `packages/grammar/src/index.ts` uses `fileURLToPath(new URL(...))`
              - `progress-phase-00.md` "Carried into Phase 1" lists only outstanding items
              - Parser test fixtures path: tracked as Known Issue 3 in progress-phase-00; address as part of §K (broken-fixture infrastructure) by introducing a shared `_fixtures.ts` helper
              If any line above is **not** true on disk, fix that single item now before continuing.
        ```
      - In the §A "Acceptance" line, replace the current sentence with: "`pnpm -r build && test && lint` green; on-disk state matches the verification block above."
- [ ] In Phase 1 §E (Diagnostic taxonomy), add as the **first** sub-task:
      ```
      - [ ] Pre-req from review-002: confirm `walker.ts`'s `makeSourceLocation`
            uses the last-token's length for `endColumn` (not the span length).
            Run `node -e "..."` (see review-002 task 1) and assert `endColumn === 20`
            on `def entity foobar {}` before proceeding.
      ```
      This makes the diagnostic-range correctness explicit at the point it matters.

---

## Definition of done for this re-review

Phase 1 may start once:

- [ ] All P0 tasks above checked.
- [ ] `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm --filter @modeler/integration-tests test` exits 0 on a fresh clone (`git clean -fdx; pnpm install; …`).
- [ ] `git status --short` shows no load-bearing untracked files.
- [ ] The interactive `endColumn` check returns 20, not 39.
- [ ] `progress-phase-00.md` "Carried into Phase 1" reflects only items genuinely outstanding (Task 7).

P1 tasks (4–8) should land as Phase 1's first commit, before §B substantive work begins.
