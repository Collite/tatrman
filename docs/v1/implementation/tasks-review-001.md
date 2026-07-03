# Review 001 — Task list

Companion to `review-001.md`. Sections are ordered by priority: **P0 = blocks "Phase 0 done"**, **P1 = should fix before Phase 1 starts**, **P2 = nice-to-have cleanup**.

For each task: do exactly what the steps say. If a step says "create file `X` with content `Y`", create file `X` with literally that content. If a step says "run command `C`", run command `C` and check that it exits 0. Do not skip the verification steps.

When you complete a task, tick its box. When you complete a whole section, run the verification command at the bottom of that section and make sure it passes before moving on.

---

## P0 — Block "Phase 0 done"

### Task 1 — Remove TypeScript-target lock-in from the canonical grammar

**Why:** `packages/grammar/src/TTR.g4` currently contains `options { language = TypeScript; }`. This file is the canonical grammar that is also vendored into ai-platform's Kotlin parser via `scripts/sync-to-ai-platform.sh`. With this option present, the synced file is no longer target-neutral. See `review-001.md` §1.1.

- [ ] Open `packages/grammar/src/TTR.g4`.
- [ ] Delete the entire `options { language = TypeScript; }` block (lines 16–18 at time of review). Save.
- [ ] Open `packages/grammar/scripts/generate-typescript-parser.sh`.
- [ ] Change the `antlr-ng` invocation to pass the language target on the CLI. The current line is:
      `npx antlr-ng -o "$OUTPUT_DIR" -l -v -- "$GRAMMAR_FILE"`
      Replace it with:
      `npx antlr-ng -o "$OUTPUT_DIR" -l -v -Dlanguage=TypeScript -- "$GRAMMAR_FILE"`
      (If `antlr-ng` rejects `-Dlanguage=TypeScript`, run `npx antlr-ng --help` and use whichever flag selects the TypeScript backend. Document the flag chosen in `packages/grammar/README.md`.)
- [ ] Run `cd packages/parser && pnpm run prebuild` from the repo root. Verify the script completes without errors and that `packages/parser/src/generated/TTRParser.ts` exists and has the same shape as before (i.e. exports `TTRParser`, `DocumentContext`, etc.).
- [ ] Run `pnpm -r build` from the repo root. Verify it exits 0.
- [ ] Run `pnpm -r test` from the repo root. Verify it exits 0.
- [ ] Update `packages/grammar/README.md` to note: "The TypeScript target is selected via the `antlr-ng` CLI in `scripts/generate-typescript-parser.sh`. The grammar file itself is target-neutral so it can be vendored into ai-platform's Kotlin parser."

### Task 2 — Fix the VS Code extension's path to the LSP bundle

**Why:** `packages/vscode-ext/src/extension.ts:9` looks for `dist/server-stdio.js` inside the extension itself, but the bundle lives in `packages/lsp/dist/`. F5 fails. See §1.4.

- [ ] Open `packages/vscode-ext/package.json`. In the `"scripts"` block, add a `"copy-server"` script and prepend it to `build`:
      ```json
      "scripts": {
        "build": "pnpm run copy-server && tsc",
        "copy-server": "node -e \"const fs=require('fs');const path=require('path');const src=require.resolve('@modeler/lsp/dist/server-stdio.js');const dstDir=path.join(__dirname,'dist');fs.mkdirSync(dstDir,{recursive:true});fs.copyFileSync(src,path.join(dstDir,'server-stdio.js'));\"",
        "typecheck": "tsc --noEmit"
      }
      ```
- [ ] Run `pnpm -r build`. Verify `packages/vscode-ext/dist/server-stdio.js` exists and is ~495 KB.
- [ ] Open `packages/vscode-ext/src/extension.ts`. Leave the `vscode.Uri.joinPath(context.extensionUri, 'dist', 'server-stdio.js')` line as-is — the copy step now satisfies it.
- [ ] Verify by hand: open `packages/vscode-ext` in VS Code, press F5, open `samples/v1-metadata/er.ttr` in the Extension Development Host. Confirm: (a) the file is highlighted, (b) the TTR Language Server channel appears in the Output panel, (c) no error popup about a missing module.

### Task 3 — Fix `server-browser.ts` so the Designer worker can actually start

**Why:** The browser server uses `require('@modeler/parser')`, which throws in an ESM browser bundle. `--external:@modeler/parser` and `--external:antlr4ng` in the bundler invocation strip them out so the Worker has no way to load them. See §1.3.

- [ ] Open `packages/lsp/src/server-browser.ts`.
- [ ] Delete both occurrences of `const { parseString } = require('@modeler/parser');`.
- [ ] At the top of the file, change the import block so it includes:
      ```ts
      import { parseString } from '@modeler/parser';
      ```
- [ ] Save.
- [ ] Open `packages/lsp/package.json`. In `"scripts"`, find `"bundle-browser"`. Remove `--external:@modeler/parser` and `--external:antlr4ng` from that command. The corrected command:
      ```
      esbuild src/server-browser.ts --bundle --platform=browser --format=esm --target=es2022 --outfile=dist/server-browser.js --external:vscode-languageserver --external:vscode-languageserver-protocol --external:vscode-jsonrpc
      ```
- [ ] Run `pnpm --filter @modeler/lsp build`. Verify `dist/server-browser.js` is produced and is now in the hundreds of KB (the bundle now embeds the parser + ANTLR runtime — that is expected).
- [ ] Verify no `require(` strings remain in `dist/server-browser.js`:
      `grep -c "require(" packages/lsp/dist/server-browser.js` — must print `0`.

### Task 4 — Make the Designer actually use the LSP (the point of the thin slice)

**Why:** Decision D6 says the Designer must speak to the LSP in Phase 0. Currently App.tsx uses a regex `def <kind> <name>` matcher. See §1.2.

- [ ] In `packages/designer/`, add `vscode-languageserver-protocol` and `vscode-jsonrpc` to `dependencies`:
      `cd packages/designer && pnpm add vscode-languageserver-protocol vscode-jsonrpc`
- [ ] Create `packages/designer/src/lsp-client.ts` with this content:
      ```ts
      import {
        BrowserMessageReader,
        BrowserMessageWriter,
        createProtocolConnection,
      } from 'vscode-languageserver-protocol/browser.js';
      import {
        InitializeRequest,
        DidOpenTextDocumentNotification,
        PublishDiagnosticsNotification,
      } from 'vscode-languageserver-protocol';

      export interface ModelGraph {
        nodes: Array<{ qname: string; kind: string; label: string }>;
        edges: unknown[];
      }

      export interface LspClient {
        openDocument(uri: string, content: string): Promise<void>;
        getModelGraph(uri: string): Promise<ModelGraph>;
        onDiagnostics(handler: (uri: string, messages: string[]) => void): void;
        dispose(): void;
      }

      export async function createLspClient(): Promise<LspClient> {
        const worker = new Worker(
          new URL('@modeler/lsp/dist/server-browser.js', import.meta.url),
          { type: 'module' }
        );
        const reader = new BrowserMessageReader(worker);
        const writer = new BrowserMessageWriter(worker);
        const connection = createProtocolConnection(reader, writer);
        connection.listen();
        await connection.sendRequest(InitializeRequest.type, {
          processId: null,
          rootUri: null,
          capabilities: {},
        } as any);
        const diagnosticHandlers: Array<(uri: string, msgs: string[]) => void> = [];
        connection.onNotification(PublishDiagnosticsNotification.type, (params) => {
          const messages = params.diagnostics.map((d) => d.message);
          for (const h of diagnosticHandlers) h(params.uri, messages);
        });
        return {
          async openDocument(uri, content) {
            await connection.sendNotification(DidOpenTextDocumentNotification.type, {
              textDocument: { uri, languageId: 'ttr', version: 1, text: content },
            });
          },
          async getModelGraph(uri) {
            return connection.sendRequest('modeler/getModelGraph', {
              textDocument: { uri },
            }) as Promise<ModelGraph>;
          },
          onDiagnostics(handler) {
            diagnosticHandlers.push(handler);
          },
          dispose() {
            worker.terminate();
          },
        };
      }
      ```
- [ ] Open `packages/designer/src/App.tsx`.
- [ ] Replace the entire `useEffect`/`handleFileLoad` block with this logic (keep the rest of App.tsx structurally the same):
      ```ts
      const clientRef = useRef<Awaited<ReturnType<typeof createLspClient>> | null>(null);

      useEffect(() => {
        let cancelled = false;
        createLspClient().then((client) => {
          if (cancelled) {
            client.dispose();
            return;
          }
          client.onDiagnostics((_uri, messages) => {
            setError(messages.length === 0 ? null : messages.join(', '));
          });
          clientRef.current = client;
        });
        return () => {
          cancelled = true;
          clientRef.current?.dispose();
          clientRef.current = null;
        };
      }, []);

      const handleFileLoad = async (content: string, uri: string) => {
        const client = clientRef.current;
        if (!client) return;
        const fileUri = `file:///${uri}`;
        await client.openDocument(fileUri, content);
        const graph = await client.getModelGraph(fileUri);
        setNodes(graph.nodes);
        setEdges(graph.edges as never[]);
      };
      ```
- [ ] Add `import { createLspClient } from './lsp-client';` to App.tsx's imports.
- [ ] Delete the old regex parser code (`const lines = content.split(...)` through `setNodes(parsedNodes)`).
- [ ] Run `pnpm --filter @modeler/designer build`. It must exit 0.
- [ ] Run `pnpm --filter @modeler/designer dev` and load `samples/v1-metadata/er.ttr` via the file picker. The canvas must render >0 nodes whose labels are entity names from `er.ttr` (not hand-parsed regex matches). Confirm in the browser DevTools Network/Sources tab that `server-browser.js` was loaded as a Worker.

### Task 5 — Fix `pnpm -r lint` so CI passes

**Why:** CI runs `pnpm -r lint`. No package has a `lint` script. ESLint is not even installed. CI is red. See §1.6.

- [ ] At the repo root, install the linting toolchain:
      ```
      pnpm add -D -w eslint@^9 @typescript-eslint/parser@^8 @typescript-eslint/eslint-plugin@^8 eslint-config-prettier eslint-plugin-prettier prettier
      ```
- [ ] Replace `.eslintrc.cjs` at the repo root with the flat-config equivalent (ESLint 9 uses flat config by default; if you keep `.eslintrc.cjs`, set `ESLINT_USE_FLAT_CONFIG=false` in the CI workflow and the lint script). Recommended: create `eslint.config.js` at the repo root with:
      ```js
      import tseslint from '@typescript-eslint/eslint-plugin';
      import tsparser from '@typescript-eslint/parser';
      import prettier from 'eslint-plugin-prettier';
      import prettierConfig from 'eslint-config-prettier';

      export default [
        {
          files: ['packages/*/src/**/*.{ts,tsx}'],
          ignores: ['**/dist/**', '**/generated/**', '**/node_modules/**'],
          languageOptions: {
            parser: tsparser,
            parserOptions: { ecmaVersion: 2022, sourceType: 'module' },
          },
          plugins: { '@typescript-eslint': tseslint, prettier },
          rules: {
            ...tseslint.configs.recommended.rules,
            ...prettierConfig.rules,
            '@typescript-eslint/no-explicit-any': 'error',
            '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
          },
        },
      ];
      ```
- [ ] Delete `.eslintrc.cjs` (the old config is incompatible with ESLint 9 flat config; if you stay on ESLint 8, keep it and add a `"lint": "eslint src --ext .ts,.tsx"` script per package).
- [ ] Add a `lint` script to **each** of these packages' `package.json` (`grammar`, `parser`, `semantics`, `edit`, `lsp`, `vscode-ext`, `designer`): `"lint": "eslint src"`.
- [ ] Add a top-level `lint` script. The root `package.json` already has `"lint": "pnpm -r lint"`; leave it as-is.
- [ ] Run `pnpm -r lint`. It must exit 0. **Expect failures from Task 6 first**; fix those before declaring this task done.

### Task 6 — Type the LSP server properly (remove `as any`)

**Why:** Lint will fail otherwise (the `no-explicit-any` rule); also, the LSP is the central component every host depends on. See §1.5. After this task, Task 5's `pnpm -r lint` must be green.

- [ ] Open `packages/lsp/src/server.ts`.
- [ ] At the top, add:
      ```ts
      import { TextDocument } from 'vscode-languageserver-textdocument';
      ```
- [ ] Change:
      ```ts
      const connection = (createConnection as any)();
      const documents: any = new (TextDocuments as any)(undefined);
      ```
      to:
      ```ts
      const connection = createConnection(ProposedFeatures.all);
      const documents = new TextDocuments(TextDocument);
      ```
- [ ] Change every `event: any` to `event: TextDocumentChangeEvent<TextDocument>` (import `TextDocumentChangeEvent` from `vscode-languageserver`).
- [ ] Repeat the same three substitutions in `packages/lsp/src/server-stdio.ts` and `packages/lsp/src/server-browser.ts`.
- [ ] Remove the duplicate factory pattern: in `server-stdio.ts` and `server-browser.ts`, delete the inline server logic and replace the file body with:
      ```ts
      import { runServer } from './server.js';
      runServer();
      ```
      Then export `runServer` from `server.ts` (rename the IIFE/top-level code into an exported function if needed; the current `server.ts` already does this).
- [ ] Run `pnpm --filter @modeler/lsp build`. It must exit 0.
- [ ] Run `pnpm --filter @modeler/lsp test`. It must exit 0 (still the placeholder test — that gets fixed in Task 9).
- [ ] Run `pnpm -r lint`. The previously failing `no-explicit-any` errors must be gone.

### Task 7 — Add the missing language-configuration.json

**Why:** Section G of the plan requires bracket pairs, comment toggle, auto-close, and indentation rules. None of those work today because there is no language configuration file. See §2.4.

- [ ] Create `packages/vscode-ext/language-configuration.json` with:
      ```json
      {
        "comments": {
          "lineComment": "//",
          "blockComment": ["/*", "*/"]
        },
        "brackets": [["{", "}"], ["[", "]"], ["(", ")"]],
        "autoClosingPairs": [
          { "open": "{", "close": "}" },
          { "open": "[", "close": "]" },
          { "open": "(", "close": ")" },
          { "open": "\"", "close": "\"", "notIn": ["string"] }
        ],
        "surroundingPairs": [
          ["{", "}"], ["[", "]"], ["(", ")"], ["\"", "\""]
        ]
      }
      ```
- [ ] Open `packages/vscode-ext/package.json`. In `contributes.languages[0]` (the `ttr` entry), add `"configuration": "./language-configuration.json"`.
- [ ] Press F5 to launch the Extension Development Host. In a `.ttr` file: type `{` and confirm `}` auto-closes; select a block and press `Cmd+/` and confirm `//` toggles; press `Cmd+]` and confirm indentation works.

### Task 8 — Add the missing `.vscode/launch.json`

**Why:** Section G required it. See §2.6.

- [ ] Create `packages/vscode-ext/.vscode/launch.json` with:
      ```json
      {
        "version": "0.2.0",
        "configurations": [
          {
            "name": "Run Extension",
            "type": "extensionHost",
            "request": "launch",
            "args": ["--extensionDevelopmentPath=${workspaceFolder}"],
            "outFiles": ["${workspaceFolder}/dist/**/*.js"],
            "preLaunchTask": "npm: build"
          }
        ]
      }
      ```
- [ ] Open `packages/vscode-ext` in a fresh VS Code window. Press F5. The Extension Development Host must launch without configuration prompts.

### Task 9 — Write the LSP tests the plan required

**Why:** Section F required three real tests; only a `expect(true).toBe(true)` placeholder exists. See §2.1.

- [ ] Replace `packages/lsp/src/__tests__/lsp.test.ts` with three tests (using `vscode-languageserver/node`'s `createMessageConnection` and `MessageConnection` against an in-memory duplex stream — see the `vscode-languageserver-protocol` test utilities). Required cases:
      1. After sending `initialize`, the server returns capabilities with `textDocumentSync.openClose === true`.
      2. After `textDocument/didOpen` with content `def entity {` (missing name), at least one diagnostic is published on the document URI.
      3. After `textDocument/didOpen` with content `def entity foo {}`, calling `modeler/getModelGraph` returns `{ nodes: [{ qname: 'foo', kind: 'entity', label: 'foo' }], edges: [] }`.
- [ ] Run `pnpm --filter @modeler/lsp test`. All three must pass.

### Task 10 — Extend the integration tests to the LSP

**Why:** Section I required parser + LSP + `modeler/getModelGraph` coverage; only parser coverage exists. See §2.2.

- [ ] Open `tests/integration/src/integration.test.ts`.
- [ ] Add a second `describe('lsp integration', ...)` block that:
      - Boots the LSP server in-process (import the same `runServer` factory after Task 6, wire it through `vscode-jsonrpc`'s in-memory `MessageConnection`).
      - For each file in `samples/`, sends `didOpen` and asserts that the published diagnostics array is empty.
      - For `samples/v1-metadata/er.ttr`, calls `modeler/getModelGraph` and asserts: `result.nodes.length > 0`; every node has string `qname`, `kind`, and `label`; `result.edges` is an array (length 0 in Phase 0 is fine).
- [ ] Add `@modeler/lsp` to `tests/integration/package.json` `devDependencies` as `"workspace:*"`.
- [ ] Run `pnpm --filter @modeler/integration-tests test`. All assertions must pass.

### Task 11 — Fix byte offsets in `SourceLocation`

**Why:** `offsetStart`/`offsetEnd` are hardcoded to 0 everywhere. The architecture's edit synthesizer requires real offsets. See §2.7.

- [ ] Open `packages/parser/src/walker.ts`.
- [ ] In `DiagnosticErrorListener.syntaxError`, get the offending token's `start`/`stop` if present and store them in `offsetStart`/`offsetEnd` (when `_offendingSymbol` is not null, use `_offendingSymbol.start` and `_offendingSymbol.stop + 1`; when it's null, use `0` and `0` — keep behavior reasonable for fatal lexer errors).
- [ ] In `makeSourceLocation`, replace `offsetStart: 0, offsetEnd: 0` with `offsetStart: ctx.start?.start ?? 0, offsetEnd: (ctx.stop?.stop ?? ctx.start?.stop ?? -1) + 1`.
- [ ] Open `packages/parser/src/__tests__/parser.test.ts` and add a regression test: parse `def entity foo {}` and assert that the entity definition's `source.offsetStart` is `0` (or wherever `def` begins) and `source.offsetEnd` is `17` (the position one past the closing `}`). Compute the expected value from the input; do not just read it back from the parser.
- [ ] Run `pnpm --filter @modeler/parser test`. Must pass.

### Task 12 — Fix the off-by-one in diagnostic ranges (`endColumn`)

**Why:** `walker.ts:211` does `endColumn: end.column + 1`, which is wrong for any token longer than one character. Diagnostics render with the wrong squiggly length. See §3.1.

- [ ] In `packages/parser/src/walker.ts`, change `makeSourceLocation` so the end column accounts for the stop token's length:
      ```ts
      const stopToken = ctx.stop ?? ctx.start;
      const stopText = stopToken?.text ?? '';
      const tokenLines = stopText.split('\n');
      const lastLineText = tokenLines[tokenLines.length - 1];
      const endLineComputed = (stopToken?.line ?? start.line) + tokenLines.length - 1;
      const endColumnComputed =
        tokenLines.length > 1
          ? lastLineText.length
          : (stopToken?.column ?? start.column) + stopText.length;
      // return { ..., endLine: endLineComputed, endColumn: endColumnComputed, ... }
      ```
- [ ] Add a test in `parser.test.ts`: `parseString('def entity foobar {}')` produces an entity definition whose `source.endColumn` matches the column **after** the closing `}` (i.e. `source.endColumn === 20`). Do the same arithmetic in the test, do not copy from the parser output.
- [ ] Run `pnpm --filter @modeler/parser test`. Must pass.

### Task 13 — Update `progress-phase-00.md` to match reality

**Why:** Multiple sections claim `[x]` for work that does not exist. Future readers of this file will be misled. See §2.

- [ ] Open `docs/plan/progress-phase-00.md`.
- [ ] In Section F, replace the `[x]` line about `modeler/getModelGraph` test with a `[ ]` and add a note: "Tests stubbed in v0; real tests added in Review-001 Task 9".
- [ ] In Section G, change the language-configuration `[x]` to reflect Task 7's completion (post-Task 7), and `.vscode/launch.json` similarly (post-Task 8). Add explicit lines for the VS Code smoke test (`@vscode/test-electron`) — leave it `[ ]` for now and move it to Phase 1's task list.
- [ ] In Section H, change the Playwright smoke test line to `[ ] deferred to Phase 3 with full LSP integration — Phase 0 ships LSP-driven node load instead (Review-001 Task 4)`.
- [ ] In Section I, change the LSP-in-process and `modeler/getModelGraph` shape lines to `[x]` after Task 10 is done; if not yet done, leave `[ ]`.
- [ ] In the Known Issues section, add a Phase 1 task to remove the `as any` workaround (now resolved in Task 6 — note as resolved).
- [ ] Status field at the top: change "Complete" to "In review — see review-001.md".

**Verification for P0 section:**
```bash
pnpm -r build      # exits 0
pnpm -r test       # exits 0
pnpm -r lint       # exits 0
pnpm --filter @modeler/integration-tests test  # exits 0
```
Plus the by-hand verifications in Tasks 2, 4, and 7.

---

## P1 — Fix before Phase 1 starts

### Task 14 — Make `Definition` a proper discriminated union

**Why:** §3.9. Plan said discriminated union; current code is a flat interface. Phase 2.A is going to expand each variant.

- [ ] Open `packages/parser/src/ast.ts`.
- [ ] Replace the single `Definition` interface with 17 per-kind interfaces (`ModelDef`, `TableDef`, …, `Er2CncRoleDef`), each carrying `kind: '<the-kind>'`, `name: string`, `source: SourceLocation`.
- [ ] Add `export type Definition = ModelDef | TableDef | ... | Er2CncRoleDef;`.
- [ ] Update `walker.ts` so each branch in `walkDefinition` returns a value typed as the specific variant (use `as const` on the kind, or build the object inside the branch).
- [ ] Run `pnpm --filter @modeler/parser build && pnpm --filter @modeler/parser test`. Must pass.

### Task 15 — Fix `.gitignore`

**Why:** `*.ttrl` is global; would silently swallow future Phase 3 layout sidecars. See §3.6.

- [ ] In `.gitignore`, change the `*.ttrl` line to `/*.ttrl` so only repo-root layout files (used in ad-hoc dev testing) are ignored.

### Task 16 — Remove duplicate `"version"` in vscode-ext/package.json

**Why:** §3.5.

- [ ] Open `packages/vscode-ext/package.json`. Delete one of the two `"version": "0.1.0",` lines (lines 3 and 8). Save. Confirm `node -e "JSON.parse(require('fs').readFileSync('packages/vscode-ext/package.json','utf8'))"` exits 0.

### Task 17 — Clean up `packages/parser/tsconfig.json` and ESLint exclusions

**Why:** The `exclude` is misleading and the ESLint generated-file override path doesn't match the actual location. See §3.4.

- [ ] In `packages/parser/tsconfig.json`, delete the `"exclude": ["src/generated/**/*"]` line. (TypeScript still has to compile these files because `walker.ts` imports them; the exclude is decorative.)
- [ ] In the new `eslint.config.js` (from Task 5), add an `ignores: ['**/generated/**']` rule explicitly so generated files are never linted.
- [ ] Run `pnpm -r build && pnpm -r lint`. Must exit 0.

### Task 18 — Add JSDoc to `SourceLocation` documenting the indexing convention

**Why:** §3.8. `line` is 1-indexed (ANTLR); `column` is 0-indexed (ANTLR). LSP wants both 0-indexed. Convention is currently undocumented; the diagnostic mapping in `server.ts` quietly relies on it.

- [ ] Open `packages/parser/src/ast.ts`. Above `SourceLocation`, add:
      ```ts
      /**
       * Source-file location for a parsed node or diagnostic.
       *
       * Conventions (match ANTLR's TokenStream, not LSP):
       *   - `line` and `endLine` are 1-indexed.
       *   - `column` and `endColumn` are 0-indexed (column of the first character of the token / one past the last).
       *   - `offsetStart` and `offsetEnd` are 0-indexed byte offsets into the source file;
       *     `offsetEnd` is exclusive.
       *
       * LSP consumers must subtract 1 from `line`/`endLine` to produce LSP positions.
       */
      ```

### Task 19 — Audit `generate-tm-grammar.ts` keyword list against the actual grammar

**Why:** §4.1. The TextMate generator lists tokens that are not lexer rules in `TTR.g4`.

- [ ] Open `packages/vscode-ext/scripts/generate-tm-grammar.ts` and `packages/grammar/src/TTR.g4`.
- [ ] For each token in the `tokens` array, grep the grammar file for a matching lexer rule (`grep -n "^<TOKEN>" packages/grammar/src/TTR.g4`). If absent, remove it from the generator's array.
- [ ] Re-run `node packages/vscode-ext/scripts/generate-tm-grammar.ts` and commit the regenerated `syntaxes/ttr.tmLanguage.json`.
- [ ] Add a note in `progress-phase-00.md` Section G that Phase 1's "TextMate grammar covers every token" will be a structural rebuild, not an audit-of-current.

---

## P2 — Cleanup, low priority

### Task 20 — Replace `grammar/index.ts`'s `__dirname` path with the package `exports` entry

**Why:** §3.7. Today the path works because `dist/../src/TTR.g4` happens to resolve; consumers should use `@modeler/grammar/grammar` (already declared in `exports`).

- [ ] In `packages/grammar/src/index.ts`, replace the body with:
      ```ts
      import { fileURLToPath } from 'url';
      export const grammarFile = fileURLToPath(new URL('../src/TTR.g4', import.meta.url));
      ```
      (functionally equivalent but uses ESM-native URL resolution).
- [ ] Greater fix: deprecate `grammarFile` in favor of the `./grammar` export. Add a comment: `// Prefer `@modeler/grammar/grammar` (declared in package exports) over this constant.`

### Task 21 — Remove unused `ProposedFeatures` imports

**Why:** §3.3. Once Task 6 lands and lint is wired, ESLint will catch these.

- [ ] In `server.ts`, `server-stdio.ts`, `server-browser.ts`: either pass `ProposedFeatures.all` into `createConnection(...)` (Task 6 already does this) or remove the import.

### Task 22 — Make Phase 0 progress doc track open Phase-1 transitions

**Why:** §4.4. The Known Issues section lists workarounds without follow-up owners.

- [ ] In `progress-phase-00.md`, replace the "Known Issues" section with a "Carried into Phase 1" section listing the items (TextMate audit; LSP type cleanup; VS Code smoke test) with a `[ ]` checkbox each. Phase 1's task list (when written) must consume this list.

---

## Definition of done for this review

Phase 0 is "complete" once:

- [ ] All P0 tasks above are checked.
- [ ] Section 5 of `review-001.md` ("What is actually solid") still applies (i.e. no regressions).
- [ ] `pnpm -r build && pnpm -r test && pnpm -r lint && pnpm --filter @modeler/integration-tests test` exits 0 on a fresh clone.
- [ ] The hand-verified demo path works: F5 in VS Code opens `samples/v1-metadata/er.ttr`, shows highlighting, shows a red squiggly on a deliberately-broken variant; `pnpm --filter @modeler/designer dev` loads the same file and renders entity nodes whose data came through the LSP (not the regex parser).
- [ ] `progress-phase-00.md` has been updated to reflect reality.

The P1 and P2 tasks should be merged into Phase 1's task list as it gets written; they do not need to ship before declaring Phase 0 done.
