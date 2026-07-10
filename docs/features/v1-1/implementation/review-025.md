# Review 025 — Section A (Grammar additions)

**Branch:** `v1-1` (commit `a4d337e` + uncommitted working-tree changes).
**Scope reviewed:** the v1.1 grammar additions described in [`docs/v1-1/plan/tasks/A-grammar.md`](../plan/tasks/A-grammar.md) and the contracts in [`docs/v1-1/design/v1-1-contracts.md §1`](../design/v1-1-contracts.md#1-grammar-tokens-and-parser-rules-added).
**Files in scope:**

- `packages/grammar/src/TTR.g4` (modified)
- `packages/grammar/package.json` (modified)
- `packages/vscode-ext/scripts/generate-tm-grammar.ts` (modified)
- `packages/vscode-ext/scripts/generate-tm-grammar.js` (regenerated)
- `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` (regenerated)
- `STATUS.md` (status flip to "under review")

**Verification runs:**

| Command                                  | Result   | Notes                                                                          |
| ---------------------------------------- | -------- | ------------------------------------------------------------------------------ |
| `pnpm -r typecheck`                      | ✅ pass  | All 8 packages clean.                                                          |
| `pnpm --filter @modeler/parser test`     | ✅ pass  | 37/37. Existing parser suites stay green.                                      |
| `pnpm --filter @modeler/integration-tests test` | ✅ pass  | 29/29. v1 sample re-parsing implicitly covered through the LSP scenarios. |
| `pnpm --filter @modeler/vscode-ext test` | ❌ **fail** | The TextMate-generator unit test crashes on module load (see Finding 1).     |

The headline number is therefore **one regression** introduced by Section A, plus several missing artifacts the task list explicitly required.

---

## Verdict

Section A is **not yet ready to move out of "under review"**. The core grammar work is solid — tokens, rules, `document` update, `idPart` extension, version bump, header comment — all match the contract. But three task-list checkboxes that look ticked were not actually delivered, and one supporting change introduced a runtime regression in an existing test suite.

Specifically:

- **Tests-first was skipped.** Neither of the two test files the mini-plan calls for has been added or extended. The acceptance criterion "all v1 samples still parse without errors" is therefore only indirectly verified (through the unchanged `parser.test.ts` fixtures), and the new constructs are not covered by any unit test at all.
- **The TextMate generator was restructured in a way that broke import-time use.** The previous `import.meta.url` derivation of the script directory was replaced with `path.resolve(process.argv[1])`, which is wrong whenever the file is loaded by a test runner instead of executed directly via `node …generate-tm-grammar.js`. The compiled `.js` also still does file I/O at module top level, so the failure surfaces as an `ENOENT` at import.
- **The committed `.js` artifact changed kind.** It flipped from hand-maintained ESM to tsc-emitted CommonJS, driven by the new `build-generator` npm script. That is internally consistent (the package is `"type": "commonjs"`), but it should be called out in the commit and the script should not be importable for its side effects.

Everything else in the grammar diff is faithful to the contract. The remaining work is small and well-bounded; see the follow-up task list `tasks-review-025.md`.

---

## Findings

### 1. (Blocker) `generate-tm-grammar.test.ts` is broken at module-load time

**Symptom (from `pnpm --filter @modeler/vscode-ext test`):**

```
Error: ENOENT: no such file or directory, open
  '/Users/bora/Dev/modeler/node_modules/.pnpm/tinypool@1.1.1/node_modules/packages/grammar/src/TTR.g4'
 ❯ scripts/generate-tm-grammar.js:270:32
```

**Why it broke.** `packages/vscode-ext/scripts/generate-tm-grammar.ts` used to derive its own location via:

```ts
const __dirname = path.dirname(fileURLToPath(import.meta.url));
```

That was replaced with:

```ts
const scriptPath = path.resolve(process.argv[1]);
const scriptDir = path.dirname(scriptPath);
const monorepoRoot = path.resolve(scriptDir, '..', '..', '..');
```

When the file is invoked as a script (`node scripts/generate-tm-grammar.js`) `process.argv[1]` does point at the script — so `pnpm run regen-tmgrammar` still works. But `scripts/__tests__/generate-tm-grammar.test.ts` imports `parseGrammar` / `tokenToScope` from the same module, and under vitest `process.argv[1]` is the vitest binary inside tinypool's worker pool, which is what produced the path you see in the error.

The original ESM derivation worked under both modes because `import.meta.url` is bound to the source module regardless of the runtime entrypoint.

**Compounding issue.** The module has a top-level side effect — line 289 of the `.ts` (and line 270 of the emitted `.js`):

```ts
const g4Content = fs.readFileSync(GRAMMAR_PATH, 'utf-8');
const tokens    = parseGrammar(g4Content);
const grammar   = buildGrammar(tokens);
fs.writeFileSync(OUTPUT_PATH, …);
```

Even with the path bug fixed, importing this module from any test would still do filesystem I/O. Production-style scripts should guard the side effect behind an `if (process.argv[1] === fileURLToPath(import.meta.url))` (ESM) or `if (require.main === module)` (CJS) check, or move it under an exported `main()` and call that only when run as a binary.

**Required action.** Restore `import.meta.url`-based resolution (or its CJS equivalent), and wrap the script body in a `main()` invoked only when the file is the entrypoint. Both `.ts` and the regenerated `.js` need this. Re-run `pnpm --filter @modeler/vscode-ext test` and confirm green.

### 2. (Missing artifact) `packages/parser/src/__tests__/grammar-v2.test.ts`

Task A's "Tests-first" section is explicit:

> `packages/parser/src/__tests__/grammar-v2.test.ts` — new test file. Cases: [four enumerated assertions about `package`, `import`, `graph`, and v1-sample re-parse].

The file does not exist (`ls packages/parser/src/__tests__/` shows the four pre-existing files only). The Done-when criterion "Every checkbox above is ticked" is therefore not met, even though the grammar itself parses the constructs correctly (verified manually below).

**Required action.** Add `grammar-v2.test.ts` with exactly the four cases the task lists. They are all `parseString(...)` smoke tests against the new productions — no fixtures, no I/O, no AST shape changes assumed (A.8 notes the AST walker doesn't know the new productions yet, so the assertions need to stay on the *parse* level: `errors.length === 0` plus, where applicable, walking `parseTree` for the rule-name nodes).

### 3. (Missing artifact) Extension to the TextMate-generator test

Task A calls for:

> `packages/grammar/scripts/__tests__/textmate-output.test.ts` (extend existing) — assert the generator emits scopes for `keyword.control.package.ttr`, `keyword.control.import.ttr`, `keyword.declaration.graph.ttr`.

The actual file lives at `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts` (the task references a wrong path; the intent is clear). Its `EXPECTED_SCOPES` list (lines 11–25) still contains only the v1 scope names. The three new v1.1 scopes are not asserted anywhere.

**Required action.** After Finding 1 is fixed (so the test file is loadable again), add `keyword.control.package.ttr`, `keyword.control.import.ttr`, `keyword.declaration.graph.ttr` to `EXPECTED_SCOPES`. The existing "every expected scope has at least one token" assertion will then cover the new scope mappings without any further plumbing.

### 4. (Minor) Generated `.js` flipped from ESM to CommonJS without explicit call-out

The previous `packages/vscode-ext/scripts/generate-tm-grammar.js` was hand-written ESM (`import fs from 'fs'`, `export function`). The new file is tsc-emitted CommonJS (`require`, `Object.defineProperty(exports, …)`). This is *consistent* with `packages/vscode-ext/package.json` setting `"type": "commonjs"`, and the new `build-generator` npm script is a strict improvement over hand-editing both `.ts` and `.js`.

But the change is invisible from the commit history and inconsistent with the rest of the monorepo's "all packages are ESM" convention noted in `CLAUDE.md`. Two small follow-ups make this safe:

1. Add a one-line comment near the top of the emitted `.js` (or the `.ts`) noting that the `.js` is `tsc`-generated CommonJS and shouldn't be hand-edited.
2. Make sure the section commit message ("Section A: …") names this restructure explicitly, so future readers grepping `git log` for tooling changes find it.

This is not a blocker; it's hygiene.

### 5. (Informational, non-deviation) AST is intentionally untouched

The mini-plan's last Done-when line says:

> No AST changes yet — that's 1.1.B.1. The parser produces parse trees for the new constructs, but the AST walker doesn't know about them; the `Document` interface is unchanged. This is intentional and expected.

That is exactly what the code shows: `packages/parser/src/ast.ts` and the walker are untouched. No deviation here — flagging only so the reviewer of B1 can pick up the baton.

### 6. (Informational) Grammar diff matches the contract exactly

For the record, I cross-walked every line of the TTR.g4 diff against `v1-1-contracts.md §1.1`–`§1.3`:

- Lexer tokens (`PACKAGE`, `IMPORT`, `GRAPH`, `OBJECTS`, `LAYOUT`, `STAR`) — present, in the prescribed positions (after `NAMESPACE`; `STAR` after `DOT`). ✅
- Parser rules (`packageDecl`, `importDecl`, `graphBlock`, `graphProperty`, `graphSchemaProperty`, `graphObjectsProperty`, `graphLayoutProperty`, `qualifiedName`) — present, signatures byte-for-byte identical to the contract. ✅
- `document` rule updated to `packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`. ✅
- `idPart` extended with the five new keyword tokens. ✅
- Grammar header bumped to v2; `packages/grammar/package.json` version → `2.0.0`. ✅

The `qualifiedName : id ;` rule reads oddly at first glance (why introduce a one-line alias?), but it is what the contract specifies and it future-proofs the rule so a tighter definition can replace it without changing every reference site. Keep as-is.

---

## What "done" looks like after the follow-ups

Section A is complete when `tasks-review-025.md` is fully checked off **and**:

```bash
pnpm --filter @modeler/parser test           # 38/38 (37 + the four new grammar-v2 cases)
pnpm --filter @modeler/vscode-ext test       # all green, EXPECTED_SCOPES includes the three new scopes
pnpm -r typecheck                            # clean
pnpm -r build                                # clean
```

At that point STATUS.md can flip `A grammar` from "under review" to `[x]` and B1 unblocks.
