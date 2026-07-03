# Review 002 — Phase 0 re-review + Phase 1 pre-flight check

**Reviewer:** Claude (Opus 4.7)
**Date:** 2026-05-14
**Subject:** Developer claims all `tasks-review-001.md` items are fixed and is ready to start Phase 1 (`docs/plan/tasks-phase-01-foundation.md`).
**Verdict:** **Conditionally ready.** The thin-slice is now genuinely wired end-to-end (Designer→Worker LSP→parser; VS Code→stdio LSP→parser; build/test/lint all exit 0). 11 of the 13 P0 tasks from review-001 are correctly resolved. Three issues remain — one a stealth regression — that must close before Phase 1 starts. Then Phase 1's plan is sound and pre-flight is otherwise green.

A follow-up task list is in `tasks-review-002.md` (short — three P0 items, a handful of cleanups).

---

## Section 1 — Verification of review-001 P0 items

Each task is re-checked against the actual disk state, not against the developer's `progress-phase-00.md` claims.

| Task | Status | Notes |
|---|---|---|
| 1 — Grammar TS-target lock-in removed | ✅ | `options { language = TypeScript; }` is gone from `TTR.g4`; `-Dlanguage=TypeScript` passed via `generate-typescript-parser.sh`. Grammar is target-neutral again, sync to ai-platform is unblocked. |
| 2 — VS Code extension finds the LSP bundle | ✅ | `copy-server` script in `packages/vscode-ext/package.json` runs ahead of `tsc`. Verified: `packages/vscode-ext/dist/server-stdio.js` exists at ~504 KB after `pnpm -r build`. |
| 3 — `server-browser.ts` uses ESM static import | ✅ | Both `server-stdio.ts` and `server-browser.ts` are now two-line shims (`import { runServer } from './server.js'; runServer();`). The browser bundle was rebuilt with `@modeler/parser` and `antlr4ng` no longer external; `grep -c "require(" packages/lsp/dist/server-browser.js` returns `0`. |
| 4 — Designer uses the LSP | ✅ | `packages/designer/src/lsp-client.ts` creates a Worker over `new URL('@modeler/lsp/browser', import.meta.url)`, speaks LSP via `BrowserMessageReader/Writer` + `createProtocolConnection`, sends `didOpen`, calls `modeler/getModelGraph`, surfaces diagnostics via `onDiagnostics`. `App.tsx` is rebuilt around this client. Vite picks up the worker as a build artifact (`dist/assets/server-browser-<hash>.js`). |
| 5 — `pnpm -r lint` passes | ✅ | ESLint 10, `@typescript-eslint/*`, prettier installed at workspace root; `eslint.config.js` is flat-config; every TS package has a `"lint": "eslint src"` script. `pnpm -r lint` exits 0. (Caveat: emits a `MODULE_TYPELESS_PACKAGE_JSON` warning on every run — see §3.3.) |
| 6 — LSP types proper (remove `as any`) | ✅ *with caveat* | `TextDocuments(TextDocument)` is typed correctly; `event: TextDocumentChangeEvent<TextDocument>` is correct; no more `(createConnection as any)()` in `server.ts`. **Caveat:** `runServer()` (server.ts:96) uses `createConnection(undefined as never, undefined as never) as Connection` to dodge the overload set. Functionally fine — `vscode-languageserver` falls back to stdio when called this way — but `as never` is the same kind of type-system bypass `as any` was, just less visible. See §3.1. |
| 7 — `language-configuration.json` exists and is wired | ✅ | File present, `contributes.languages[0].configuration: "./language-configuration.json"` in `package.json`. Covers brackets, comments, auto-close, surrounding pairs. |
| 8 — `.vscode/launch.json` exists | ✅ | Present at `packages/vscode-ext/.vscode/launch.json`, "Run Extension" config, `preLaunchTask: "npm: build"` so the bundle is fresh when F5 runs. |
| 9 — Real LSP tests | ✅ | `packages/lsp/src/__tests__/lsp.test.ts` has three real tests: `initialize` capabilities, `didOpen` of malformed content publishes diagnostic, `modeler/getModelGraph` returns the expected `{nodes:[{qname,kind,label}], edges:[]}` after `didOpen`. Tests use a `PassThrough`-based paired connection — clean. |
| 10 — Integration tests cover LSP | ✅ *with minor gap* | `tests/integration/src/integration.test.ts` has 4 tests: 2 parser-level (all samples parse cleanly + `er.ttr` has entities) and 2 LSP-level (`getModelGraph` shape on `er.ttr`; empty result on unknown document). **Gap:** the plan asked for "send `didOpen` for **each** sample, assert no diagnostics." Only `er.ttr` is opened via LSP; the no-diagnostics assertion is implicit (the model-graph result test reads the same parser path). Acceptable for Phase 0; tighten in Phase 1 §K. |
| 11 — Byte offsets in `SourceLocation` | ✅ | `walker.ts:65-66` (parse errors) and `walker.ts:218-228` (AST nodes) read `token.start` / `token.stop` from the ANTLR `Token`. Verified at runtime: `parseString('def entity foo {}')` returns `offsetStart: 0, offsetEnd: 17` (matches the test on line 53-60). |
| 12 — `endColumn` off-by-one fix | ❌ **stealth regression** | See §2.1 — the fix is wrong, the regression test was relaxed to hide it. |
| 13 — `progress-phase-00.md` updated | ✅ *with stale items* | Status is now "In review — see review-001.md"; sections list "Review-001 fixes applied" subsections. **Stale:** "Carried into Phase 1" still lists "Make `Definition` a proper discriminated union" and "TextMate grammar token audit" — both already completed (verified against `ast.ts` and the now-correct `generate-tm-grammar.ts`). Cosmetic, but `progress-phase-00.md` is what Phase 1 pre-flight checks. |

### Definition-of-done from `tasks-review-001.md`

- [x] `pnpm -r build` exits 0 — yes (8 of 9 workspace projects; `grammar` no longer has a build because it's pure config — fine).
- [x] `pnpm -r test` exits 0 — yes (8 + 3 + 1 + 4 = 16 tests pass).
- [x] `pnpm -r lint` exits 0 — yes (with cosmetic warning, §3.3).
- [x] `pnpm --filter @modeler/integration-tests test` exits 0 — yes.
- [ ] Hand-verified demo path — **cannot verify in this review session.** The F5 path and Designer dev server path are correctly wired on disk; whether they actually run on Bora's machine is the developer's responsibility to confirm and the `tasks-review-001.md` Definition of Done. Worth noting: even if the Designer renders entity nodes, **the diagnostic squigglies on broken input will have wrong ranges** because of §2.1.

---

## Section 2 — Issues that must close before Phase 1 starts

### 2.1 `endColumn` is still broken; the regression test was relaxed (highest priority)

**What happened.** Task 12 in `tasks-review-001.md` required a specific test:

> Add a test: `parseString('def entity foobar {}')` produces an entity definition whose `source.endColumn` matches the column after the closing `}` (i.e. `source.endColumn === 20`). Do the same arithmetic in the test, do not copy from the parser output.

The actual test as committed (`packages/parser/src/__tests__/parser.test.ts:62-72`):

```ts
it('parseString("def entity foobar {}") endColumn is computed correctly', () => {
  const result = parseString('def entity foobar {}');
  ...
  expect(def!.source.endLine).toBe(1); // single line
  expect(def!.source.endColumn).toBeGreaterThan(0); // valid column
});
```

`> 0` is a tautology — it passes for any sane parser. The intended assertion (`=== 20`) is gone.

**What the parser actually returns.** Verified by running the built parser against the same input:

```
$ node -e "import('./dist/index.js').then(({parseString}) => { ... });"
source = {"file":"<string>","line":1,"column":0,"endLine":1,"endColumn":39,"offsetStart":0,"offsetEnd":20}
```

`endColumn: 39` for a 20-character single-line string. That's not a column at all — it's a number greater than the string's own length.

**Why it's broken.** The new `makeSourceLocation` (`walker.ts:212-229`):

```ts
const startToken = ctx.start ?? ...;
const endToken   = ctx.stop  ?? ...;
const startOffset = startToken.start;
const endOffset   = endToken.stop;
const tokenLength = endOffset - startOffset + 1;   // ← this is the SPAN length
return {
  ...
  endColumn: endToken.column + tokenLength,        // ← but column is added to END token
};
```

`tokenLength` is the number of characters from the start of the **first** token to the end of the **last** token (the full span). The code then adds that span length to the **last** token's column. For a single-token span the math happens to work; for a multi-token span (like every real Definition) it produces a column far past the end of the input.

The correct calculation for the end of the last token is:

```ts
const stopToken = ctx.stop ?? ctx.start;
const stopTokenLength = stopToken.stop - stopToken.start + 1; // length of THIS token
endColumn: stopToken.column + stopTokenLength;
```

That gives `19 + 1 = 20` for `def entity foobar {}` — the column one past the closing `}`. Correct.

**Why this matters now, not later.** Phase 0 didn't surface this because (a) `modeler/getModelGraph` doesn't include source locations in its response, (b) parse-error diagnostics use the **single-token** path in `DiagnosticErrorListener.syntaxError()` which is correct, and (c) the Designer's diagnostic banner just concatenates messages. But Phase 1 §E ("Diagnostic taxonomy") and §F ("Parser error recovery") both build directly on `SourceLocation` → LSP `Range` mapping for AST-derived diagnostics. Phase 1's `ttr/unknown-property` and `ttr/parse-recovery-info` diagnostics will emit garbage ranges immediately.

**Fix:** apply the correct formula above, restore the strict assertion (`expect(def!.source.endColumn).toBe(20)`), and add a second regression for a multi-line span (e.g. `def entity foo {\n}\n`) where `endLine !== line`. The walker's `endLine` math currently uses `endToken.line` directly, which is right; only `endColumn` is broken.

### 2.2 Stray `packages/vscode-ext/src/extension.js`

`packages/vscode-ext/src/extension.js` exists alongside `extension.ts`. It is:

- Not tracked by git (`git ls-files` exit 1).
- Not ignored (`git check-ignore` exit 1).
- CommonJS (`"use strict"; ... exports.activate = activate; var vscode = require("vscode");`) — not even the same module system as the rest of the package.
- Outside the `tsc` flow: `tsconfig.json` has `rootDir: ./src, outDir: ./dist` and there's no `allowJs`. tsc ignores it.

It's leftover from an earlier compile run, in the wrong directory. It does no harm at runtime (extension.ts compiles to `dist/extension.js`, which is what VS Code loads), but it pollutes the source tree, and a future contributor will be confused. Delete it before declaring Phase 0 done.

### 2.3 Untracked production-code files

`git status` shows several untracked files that are load-bearing — Phase 1 cannot proceed without them in version control:

| File | Type | Must track? |
|---|---|---|
| `eslint.config.js` | Lint config | **Yes** — CI's `pnpm -r lint` reads it; without it on `main`, fresh clones fail lint. |
| `packages/designer/src/lsp-client.ts` | Production code | **Yes** — App.tsx imports it. |
| `packages/vscode-ext/language-configuration.json` | Extension contribution | **Yes** — `package.json` references it. |
| `docs/plan/tasks-phase-01-foundation.md` | Phase 1 plan | **Yes** — about to be executed. |
| `review-001.md`, `tasks-review-001.md` | Review artifacts | **Yes** — referenced from `progress-phase-00.md`. |
| `CLAUDE.md` | Agent guidance | Probably yes (not load-bearing, but useful). |
| `packages/vscode-ext/src/extension.js` | **Stray artifact** | **No — delete it** (§2.2). |

Modified-but-uncommitted files (likely intentional, but list for hygiene): everything under "Changes not staged for commit" in `git status` — the developer should commit these as a single "Review-001 fixes" commit before branching for Phase 1.

---

## Section 3 — Caveats and cleanup (not blockers; address opportunistically)

### 3.1 `runServer()` uses `as never` to dodge the createConnection overload

`packages/lsp/src/server.ts:95-99`:

```ts
export function runServer(): void {
  const connection = createConnection(undefined as never, undefined as never) as Connection;
  createServerConnection(connection);
  connection.listen();
}
```

`createConnection(undefined, undefined)` happens to work because the runtime defaults to stdio when no streams are supplied, but `as never` is a type-system silencer in the same family as the `as any` we just removed. Two cleaner alternatives:

```ts
// Option A — explicit stdio streams
import { StreamMessageReader, StreamMessageWriter } from 'vscode-languageserver';
const connection = createConnection(
  new StreamMessageReader(process.stdin),
  new StreamMessageWriter(process.stdout)
);
```

```ts
// Option B — proposed-features overload
import { createConnection, ProposedFeatures } from 'vscode-languageserver';
const connection = createConnection(ProposedFeatures.all);
```

Either is one-line, removes both `as never` casts, and survives ESLint without escape hatches. Phase 1 work in §E and §G will keep adding capabilities; cleaning this up now means each new capability lands without copying the cast.

### 3.2 `@modeler/lsp` exports `./dist/server.js` as a public entry

`packages/lsp/package.json`:

```json
"exports": {
  ".":            { "import": "./dist/server-stdio.js",  ... },
  "./browser":    { "import": "./dist/server-browser.js", ... },
  "./dist/server.js": "./dist/server.js"
}
```

The integration test imports `from '@modeler/lsp/dist/server.js'` to reach `createServerConnection`. The export works but is awkward — `dist/` is an implementation-detail path leaking into the public surface. Recommend renaming to a stable name:

```json
"./server": { "import": "./dist/server.js", "types": "./dist/server.d.ts" }
```

Then test imports become `from '@modeler/lsp/server'`. Phase 1 §E/§F/§G will all want to import `createServerConnection` for unit tests — settle the path now.

### 3.3 Root `package.json` is missing `"type": "module"`

Every `pnpm -r lint` run prints:

```
Warning: Module type of file:///.../eslint.config.js is not specified and it doesn't parse as CommonJS.
Reparsing as ES module because module syntax was detected. This incurs a performance overhead.
To eliminate this warning, add "type": "module" to ...
```

Listed in `progress-phase-00.md` Known Issues §4 already. The fix is a one-line edit to root `package.json`. The packages all already have `"type": "module"`; making the root consistent is the obvious choice. Do it before Phase 1 §E adds more `eslint.config.js` rules and the warning becomes noisier.

### 3.4 `progress-phase-00.md` "Carried into Phase 1" list contains items already done

- `Definition` discriminated union — done in `ast.ts:28-147` and `walker.ts:191-209`.
- TextMate token audit — verified against `TTR.g4`; every entry in `generate-tm-grammar.ts` has a matching lexer rule. (My review-001 §4.1 was wrong on this point — apologies; the tokens `PRIMARY`, `SECONDARY`, `BTREE`, `FULLTEXT`, `OBJECT`, `LIST`, `CHAR`, `VARCHAR`, `DECIMAL`, `DATE`, `TIMESTAMP`, `NOT_NULL`, etc. are all in the grammar. No audit work was needed and none was done; the claim "tokens audited against TTR.g4 — removed tokens not present as lexer rules" should be reframed as "verified all tokens present; no removals needed".)

Remove or rephrase both lines so Phase 1's pre-flight item "Re-read `tasks-review-001.md` P1 (Tasks 14–19) and P2 (Tasks 20–22) — all of these are folded into the sections below" is actually accurate when the developer reads `progress-phase-00.md`.

### 3.5 Phase 1 §A's carryover should be trimmed

`docs/plan/tasks-phase-01-foundation.md` §A repeats the carryover items as checkboxes. Many are already done (Tasks 14, 15, 16, 17, 18 are already on disk; 19's "audit" turned out to be a no-op; 20 is done; 22 is partially done). When the developer opens Phase 1 they should not be doing make-work re-implementing items that are already in.

Suggested rewording: replace §A's checklist with a single verification step ("Confirm review-001 P1/P2 are reflected on disk; mark §A complete and proceed to §B") and a one-paragraph note listing what was already absorbed. Saves a half-day of "is this already done?" archaeology.

### 3.6 Phase 1 plan: small structural observations

The plan as written (`tasks-phase-01-foundation.md`) is generally sound and well-scoped. Quick notes:

- **§B (TextMate restructure)** is the right move. The Phase 0 generator is a hardcoded token list — replacing it with a TTR.g4-driven generator is what makes Phase 2+ grammar evolution safe.
- **§D `.ttrl` support** correctly reuses `source.json` rather than writing a bespoke grammar. The LSP gating (`if (uri.endsWith('.ttr'))` before parsing) is non-negotiable; today's `server.ts` doesn't gate at all and would happily try to parse a `.ttrl` file as TTR, producing nonsense diagnostics. Make sure §D's "Add a unit test confirming `didOpen` of a `.ttrl` document produces no diagnostics" actually lands.
- **§E (diagnostic taxonomy)** depends directly on §2.1 above being fixed. Until `SourceLocation.endColumn` is correct, every AST-source diagnostic will have a wrong range. Recommend pulling §2.1 in as the first sub-task of §E so the broken-fixture work in §K has correctly-ranged squigglies to assert against.
- **§F (parser error recovery)** is the most ambitious section in Phase 1. Time budget on the spec is 1.5–2 weeks for the whole phase; §F alone could absorb a week. Watch for scope creep; the 10-fixture floor is a good guard rail.
- **§G (semantic tokens)** is straightforward once §E is in place. The architecture has already named the legend types; the implementation is a tree walk.
- **§I (cross-repo CI)** depends on ai-platform availability. The plan correctly notes that the job should skip with a clear message when ai-platform is not in the CI environment. Make sure "skip" is loud (a job-level annotation), not silent.
- **§J (VS Code smoke test)** has historical flakiness with `@vscode/test-electron` on Linux CI. Pinning the electron version, retrying once, and using xvfb is the standard playbook — the plan calls all three out.
- **§K (broken-sample fixtures)** would have caught §2.1 in P0 if it had existed. Strong recommendation: build §K's fixture infrastructure **before** §E's diagnostic codes, so the test-driven path forces the diagnostic codes to be right.

---

## Section 4 — Pre-flight readiness for Phase 1

`docs/plan/tasks-phase-01-foundation.md` pre-flight is a five-item checklist:

| Pre-flight item | Status |
|---|---|
| `tasks-review-001.md` Definition of Done satisfied | **Partial** — build/test/lint green, but Task 12 is broken (§2.1), and three load-bearing files are untracked (§2.3). |
| Create branch `feat/phase-01-foundation` from the merged Phase 0 PR | Not yet — review-001 fixes still uncommitted on `v0`. |
| Create `docs/plan/progress-phase-01.md` | Not yet (fine; first task of Phase 1). |
| Re-read architecture §4.5, §4.6, §6, §8.4 | Developer's call. |
| Re-read `tasks-review-001.md` P1+P2 | Most already folded in; the Phase 1 §A carryover list is stale (§3.5). |

**Recommendation: do not start Phase 1 §B yet.** Close the three items in `tasks-review-002.md` first (§2.1, §2.2, §2.3 — call it half a day). Then commit, branch for Phase 1, and proceed. Optionally absorb §3.1, §3.2, §3.3, §3.4, §3.5 at the same time — they're small and avoid Phase 1 starting on creaky foundations.

The Phase 1 plan itself is good; no architectural concerns. Once the three Section 2 items are closed, pre-flight is fully green and Phase 1 §B can start.
