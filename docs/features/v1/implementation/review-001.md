# Review 001 — Phase 0 (vertical thin slice)

**Reviewer:** Claude (Opus 4.7)
**Date:** 2026-05-14
**Subject:** Developer's claim that Phase 0 is complete.
**Source of truth used:**
- `docs/plan/tasks-phase-00-thin-slice.md`
- `docs/plan/implementation-plan.md`
- `docs/design/architecture.md`
- `docs/plan/progress-phase-00.md` (developer's progress log)

## Verdict

**Phase 0 is NOT complete as claimed.** Build is green, but four of the eight architectural decisions captured in §2 of the architecture doc have been silently softened or skipped, the CI lint job will fail on every commit, the Designer never speaks to the LSP (skipping the whole point of the thin slice per decision D6), and the VS Code extension cannot find the LSP bundle at runtime. Several "complete" checkboxes in `progress-phase-00.md` do not match what is on disk.

This review divides findings into:
- **Section 1 — Architectural deviations** (must address; they erode the foundation other phases sit on)
- **Section 2 — Plan items marked done that aren't** (must address before declaring Phase 0 done)
- **Section 3 — Bugs and code-quality issues** (should address; many are 5-minute fixes)
- **Section 4 — Minor / cleanup**

The matching task list is in `tasks-review-001.md`.

---

## Section 1 — Architectural deviations

### 1.1 Grammar file is locked to TypeScript target

`packages/grammar/src/TTR.g4` now contains:

```antlr
options {
  language = TypeScript;
}
```

The architecture is explicit (D1, D2, §4.1): the same `.g4` file is the canonical source for *both* the TypeScript parser (Modeler) and the Kotlin parser (ai-platform), kept in sync by `sync-to-ai-platform.sh`. With the `language = TypeScript` option baked into the grammar, copying it into ai-platform produces a file the Java/Kotlin ANTLR4 toolchain will reject (or accept and ignore, depending on version) — either way the canonical source no longer means the same thing in both places.

The progress doc acknowledges adding this option (Section B note) but does not flag it as a deviation from the sync model.

**Fix direction:** remove `options { language = ... }` from the grammar; pass the language target to `antlr-ng` via the CLI (`-Dlanguage=TypeScript` or whatever the binary supports), or wrap the canonical grammar with a tiny TS-specific overlay file that adds the option. The canonical `.g4` must stay target-neutral.

### 1.2 Designer bypasses the LSP entirely

`packages/designer/src/App.tsx` parses `.ttr` files with this:

```ts
const match = line.match(/^\s*def\s+(\w+)\s+(\w+)/);
```

The plan (Section H) is unambiguous:

> Replace the RDF-driven model loader with an LSP-driven one: spawn `@modeler/lsp/dist/server-browser.js` as a Web Worker; speak LSP over `MessageChannel`… On file load, send `textDocument/didOpen`; subscribe to `publishDiagnostics`; call `modeler/getModelGraph` and render its nodes.

Decision D6 ("vertical thin slice end-to-end first") exists specifically so that the Designer ↔ LSP transport is debugged in Phase 0 — risk §4 of the task list ("Web Worker LSP transport has a quirk we haven't anticipated") is literally about doing this in Phase 0.

The progress doc moves this to Phase 3 unilaterally. That is a re-scoping of Phase 0, not a completion of it.

### 1.3 `server-browser.ts` uses CommonJS `require()` in an ESM browser bundle

`packages/lsp/src/server-browser.ts`, lines 34 and 76:

```ts
const { parseString } = require('@modeler/parser');
```

Three problems:

1. `require` is not defined in browser/Worker ESM. esbuild will emit `require(...)` as-is because `@modeler/parser` is marked external in the bundler invocation (`--external:@modeler/parser`). At runtime in a Worker this throws `ReferenceError: require is not defined`.
2. Even if `require` resolved, the browser cannot resolve the bare specifier `@modeler/parser` without an import map.
3. The grammar/parser depends on `antlr4ng`, which is also marked external in the browser bundle. The Worker has nowhere to load it from.

Net effect: the browser LSP cannot start. Combined with §1.2, no host actually exercises the Designer-side transport.

**Fix direction:** use static `import { parseString } from '@modeler/parser'` at top of file, drop `--external:@modeler/parser` and `--external:antlr4ng` from the browser bundle, and add a Vitest test (or Designer smoke test, see §2.5) that loads the worker bundle in a JSDOM/headless environment and verifies it initializes.

### 1.4 VS Code extension points at a nonexistent LSP bundle path

`packages/vscode-ext/src/extension.ts:9`:

```ts
const serverModule = vscode.Uri.joinPath(context.extensionUri, 'dist', 'server-stdio.js');
```

This resolves to `<vscode-ext>/dist/server-stdio.js`. The actual bundle is at `<lsp>/dist/server-stdio.js`. Nothing copies it. Pressing F5 (the acceptance criterion for Section G) will fail with an ENOENT before the language client starts.

**Fix direction (pick one):**

- Reference the file via `require.resolve('@modeler/lsp/dist/server-stdio.js')` (or the workspace path) at activation time, **or**
- Add a `prebuild`/`build` script step in `vscode-ext` that copies `node_modules/@modeler/lsp/dist/server-stdio.js` into the extension's own `dist/`.

For VSIX packaging later we will need the second option; for F5 local dev either works.

### 1.5 LSP server is built almost entirely on `as any`

`packages/lsp/src/server.ts`, `server-stdio.ts`, `server-browser.ts` all use:

```ts
const connection = (createConnection as any)();
const documents: any = new (TextDocuments as any)(undefined);
documents.onDidOpen((event: any) => { ... });
```

Per the architecture this package is the central component every host depends on. Starting with `any` everywhere means every Phase 2 capability (definitions, references, hover, semantic tokens) inherits zero type safety. Also: `.eslintrc.cjs` has `@typescript-eslint/no-explicit-any: 'error'` — so once lint is wired (§1.6), this will fail.

The `vscode-languageserver` API does work without the casts; the issue is mostly that the developer instantiated `TextDocuments` without supplying a `TextDocumentsConfiguration<TextDocument>`. The fix is to pass `TextDocument` from `vscode-languageserver-textdocument` (already a declared dependency):

```ts
import { TextDocument } from 'vscode-languageserver-textdocument';
const documents = new TextDocuments(TextDocument);
```

The progress doc lists this as "Known Issue 1" but has no removal target.

### 1.6 The CI workflow is broken (lint step)

`.github/workflows/ci.yml` runs `pnpm -r lint`. No package has a `lint` script. `pnpm -r` exits 1 with `ERR_PNPM_RECURSIVE_RUN_NO_SCRIPT`. ESLint, Prettier, and the plugins referenced in `.eslintrc.cjs` (`@typescript-eslint/recommended`, `prettier/recommended`) are **not installed anywhere in the workspace** (`find . -name eslint -type d` returns nothing).

So:
- The CI run that the progress doc claims is green has never actually run lint successfully.
- The acceptance criterion "CI green on the PR" is unverifiable until lint is either fixed or removed.

**Fix direction:** install `eslint`, `@typescript-eslint/{parser,eslint-plugin}`, `eslint-config-prettier`, `eslint-plugin-prettier`, `prettier` at the workspace root; add a `lint` script in each TS package (e.g. `"lint": "eslint src --ext .ts,.tsx"`). Verify locally that `pnpm -r lint` exits 0 before pushing.

---

## Section 2 — Plan items marked done in progress-phase-00.md that are not actually done

### 2.1 Section F: LSP tests

Plan required three tests in `packages/lsp/src/__tests__/`:

> - Server initializes and responds to `initialize`
> - `didOpen` of a malformed `.ttr` document publishes a diagnostic
> - `modeler/getModelGraph` after `didOpen` returns expected stub nodes

Actual content of `packages/lsp/src/__tests__/lsp.test.ts`:

```ts
describe('lsp', () => {
  it('placeholder test - LSP tests will be added when the server is functional', () => {
    expect(true).toBe(true);
  });
});
```

Progress doc lists Section F as fully `[x]`.

### 2.2 Section I: integration tests

Plan required three steps:

> 1. Parse every file in `samples/`, assert no errors
> 2. Boot an LSP server in-process, send `didOpen` for each sample, assert no diagnostics
> 3. Call `modeler/getModelGraph` for one representative sample, assert response shape

Only step 1 is implemented in `tests/integration/src/integration.test.ts`. No LSP in-process boot, no `getModelGraph` call. Progress lists Section I as fully `[x]`.

### 2.3 Section G: VS Code smoke test

Plan required `@vscode/test-electron` test asserting language detection and diagnostic. `@vscode/test-electron` is a declared devDependency in `vscode-ext/package.json` but no test file exists. Progress lists Section G as fully `[x]`.

### 2.4 Section G: language configuration

Plan required:

> Contribute language configuration: bracket pairs `()`, `[]`, `{}`; line comment `//`; block comment `/* */`; auto-close pairs; surround pairs.

There is no `language-configuration.json` file in `packages/vscode-ext/`. The `contributes.languages[]` entries do not have a `configuration` field pointing at one. The "configuration" block in `package.json` (line 48) is the extension *settings* schema (the `ttr.trace.server` setting), not the language configuration.

Without this file, brackets do not auto-close, `Cmd+/` does not toggle a comment, and `Cmd+]` does not indent — none of the basic editing affordances work.

### 2.5 Section H: Designer Playwright smoke test

Plan required:

> Add a Playwright smoke test: spin up the dev server, load `samples/v1-metadata/er.ttr` via the file picker, assert that >0 nodes appear.

No Playwright dependency in `packages/designer/package.json`, no test file. Progress lists Section H as fully `[x]`.

### 2.6 Section G: `.vscode/launch.json`

Plan required:

> Add a launch configuration (`.vscode/launch.json`) for "Run Extension".

Not present at repo root or in `packages/vscode-ext`. Progress lists this as `[x]`.

### 2.7 Section C: `parseFile` byte-offset acceptance

Plan AST spec (Section C, sub-bullet) requires:

> `SourceLocation { file, line, column, endLine, endColumn, offsetStart, offsetEnd }`

`walker.ts:48-49` and `walker.ts:212-213` hardcode `offsetStart: 0, offsetEnd: 0` everywhere. The edit synthesizer (architecture §4.4) explicitly relies on byte offsets for surgical text patches. This is a foundation bug, not a Phase 1+ concern.

**Fix direction:** in the syntax-error listener, ANTLR exposes `offendingSymbol.start` / `offendingSymbol.stop` (character indices). In `makeSourceLocation`, `ctx.start.start` and `ctx.stop.stop` are the character offsets. Both should be plumbed through.

---

## Section 3 — Bugs and code-quality issues

### 3.1 `endColumn` off-by-one in `makeSourceLocation`

`walker.ts:211`:

```ts
endColumn: end.column + 1,
```

`end.column` is the column where the *stop token* starts (0-indexed). Adding 1 lands the diagnostic's end-range one character into the stop token, not after it. To get the column after the token, you need `end.column + (end.stop - end.start + 1)` (the token's length).

LSP renders diagnostic squigglies using the range; the result today is that every diagnostic squiggly is wrong-length whenever the offending node spans more than one character.

### 3.2 Three near-duplicate copies of the LSP server

`server.ts` exports a `runServer()` factory. `server-stdio.ts` and `server-browser.ts` re-implement the same logic inline rather than calling `runServer()`. Three copies, three places to keep in sync. The simplest fix:

- Keep `server.ts` as the single source.
- `server-stdio.ts` and `server-browser.ts` become two-liners that import `runServer` and call it (passing the connection's transport-specific factory).

### 3.3 Unused imports

`ProposedFeatures` is imported in all three LSP server files and never referenced. ESLint would catch this once §1.6 is fixed.

### 3.4 `packages/parser/tsconfig.json` exclude is misleading

```json
"exclude": ["src/generated/**/*"]
```

The generated files are still compiled and emitted (TypeScript pulls them in transitively via imports from `walker.ts`). The exclude pattern reads as "these are not compiled" when they are. Either delete the exclude or convert to a project reference.

The ESLint config's override path is `generated/**/*` which is workspace-root-relative — it would never match `packages/parser/src/generated/**/*` once lint actually runs.

### 3.5 Duplicate `version` key in `vscode-ext/package.json`

Lines 3 and 8 both set `"version": "0.1.0"`. JSON parsers silently take the last; spec-wise this is undefined behavior. Remove the second occurrence.

### 3.6 `.gitignore` ignores all `*.ttrl`, not just root

Plan said (Section A): "`.ttrl` (only at the repo root for testing)". Current rule `*.ttrl` is global — would silently swallow real layout sidecars when Phase 3 lands (architecture §6 puts them at `<project-root>/.modeler/layout.ttrl`).

**Fix:** change to `/*.ttrl` if the intent is truly "root only," or remove entirely.

### 3.7 `grammar/index.ts` path is fragile

```ts
export const grammarFile = path.join(__dirname, '../src/TTR.g4');
```

When this file ships as `dist/index.js`, `__dirname` is `<pkg>/dist`, so `../src/TTR.g4` resolves correctly *only* as long as the package is consumed from its own working tree. The package `exports` map already exposes `./grammar` → `./src/TTR.g4`; downstream packages should consume that, not derive the path. Either delete `grammarFile` or wrap it in `fileURLToPath(import.meta.resolve('./src/TTR.g4'))`.

### 3.8 LSP diagnostic range uses inconsistent column conventions

In `server.ts`/`server-stdio.ts`/`server-browser.ts`:

```ts
start: { line: err.source.line - 1, character: err.source.column },
end:   { line: err.source.endLine - 1, character: err.source.endColumn },
```

`line` is 1-indexed in `SourceLocation` (matches ANTLR); `column` is 0-indexed (also ANTLR). LSP expects both 0-indexed. The line conversion is correct; the column passes through unchanged, which is also correct *if* the convention is documented. There is no JSDoc on `SourceLocation` saying which is which. Combined with §3.1, this is a quiet correctness trap.

**Fix:** add JSDoc to `SourceLocation` declaring the indexing convention; fix the endColumn computation (§3.1); add one regression test asserting that a known-bad input produces the expected LSP range.

### 3.9 `Definition` is a flat interface, not a discriminated union

Plan (Section C, sub-bullet) said:

> `Definition` as a discriminated union with `kind` discriminator (`'model' | ... | 'er2cncRole'`); each variant has `name: string` and `source: SourceLocation` only in Phase 0

`ast.ts:36-40` declares `Definition` as a single interface, not a union. TypeScript narrowing on `def.kind` therefore doesn't work — there is no per-variant type to widen into in Phase 2.A. Trivial fix: declare 17 single-variant interfaces and `export type Definition = ModelDef | TableDef | ...`. Or, since v0 carries no per-variant fields yet, leave a TODO comment and a Phase-2.A reminder — but explicitly call this a deviation, don't claim the bullet is done.

---

## Section 4 — Minor / cleanup

### 4.1 Generator emits `version` separator inconsistently

`packages/vscode-ext/scripts/generate-tm-grammar.ts` mentions tokens like `OBJECT`, `LIST`, `BOOLEAN`, `NUMBER`, `INTEGER`, `DOUBLE`, `CHAR`, `VARCHAR`, `DECIMAL`, `DATE`, `TIMESTAMP`, `PRIMARY`, `SECONDARY`, `ORDERED`, `BTREE`, `FULLTEXT`, `UNIQUE`, `NOT_NULL`. Many of those are not declared as keywords in the grammar (e.g. `PRIMARY`, `SECONDARY`, `BTREE`, `FULLTEXT` aren't in TTR.g4 as lexer rules — they appear as string-literal property values or aren't there at all). Cross-check the generator's keyword list against the grammar before declaring Phase 1's TextMate work done. Phase 0 may live with the discrepancy; flag it in `progress-phase-00.md`.

### 4.2 `reviews.md` was checked in unstaged

`git status` shows `AM reviews.md` — added and modified, not committed. Not Phase 0 related, just noting.

### 4.3 Stock vocabulary location

Architecture §4.3 says stock-vocab `.ttr` files live at `@modeler/semantics/src/stock/`. `samples/builtin/cnc-stock-roles.ttr` is in `samples/` instead. Out of scope for Phase 0 (semantics is a placeholder); flag for Phase 2.B.

### 4.4 Progress doc note about `"(createConnection as any)()"` workaround

The Known Issues section mentions this workaround without an owner or a target phase. Phase 1 is "Foundation tier complete." `as any` in the LSP foundation should be removed before Phase 1 ships, not later. Add it to Phase 1's task list explicitly.

---

## Section 5 — What is actually solid

To not be entirely negative:

- The monorepo scaffold (Section A) is clean and minimal; tsconfig.base.json, pnpm-workspace.yaml, vitest.config.ts are exactly as the plan specifies.
- The grammar/parser pipeline regenerates cleanly via `bash packages/grammar/scripts/generate-typescript-parser.sh`.
- All sample `.ttr` files parse without errors, including the 80–112 KB files in `v1-metadata/`. The parser tests in `packages/parser/src/__tests__/parser.test.ts` are well-shaped and the assertions are right.
- The `@modeler/grammar` package's sync scripts (`sync-to-ai-platform.sh`, `check-sync.sh`) are correct, modulo the §1.1 issue with the embedded `language = TypeScript` option.
- Build green: `pnpm -r build` works on a fresh tree; `pnpm -r test` is green.

These are real gains. The thin-slice idea fails at the integration boundary, not at the per-package level.

---

## Acceptance criteria status

From `tasks-phase-00-thin-slice.md` "Acceptance criteria for Phase 0 as a whole":

- [ ] All sections A–K complete — **NO** (see §2)
- [x] `pnpm -r build` clean
- [x] `pnpm -r test` green (but green because the LSP test is a no-op — §2.1)
- [ ] `pnpm -r lint` clean — **NO, fails outright** (§1.6)
- [ ] CI green on the PR — **NO** (lint step fails)
- [ ] Demo path verified by hand — **CANNOT BE: F5 in VS Code fails (§1.4), Designer doesn't use LSP (§1.2)**

The PR is not ready for merge. The fix list is in `tasks-review-001.md`.
