# Review 024 — Phase 3 Sections J (VS Code smoke test) and K (Documentation)

**Scope:** Developer claims J.1–J.6 and K.1–K.6 are done. Reviewing against `docs/plan/phase-03/J-vscode-smoke.md` and `docs/plan/phase-03/K-documentation.md`.

## TL;DR

**Section J does not function.** The smoke test cannot run: the compiled test harness (`packages/vscode-ext/dist/test/runTests.js` and friends) uses `__dirname`, but `@modeler/vscode-ext` is an ESM package, so `node ./dist/test/runTests.js` throws `ReferenceError: __dirname is not defined in ES module scope` before anything is exercised. Confirmed by direct execution. The plan's "DONE when" gate says *"All five smoke test cases pass locally"* — they cannot. The boxes were ticked without running the harness.

**Section K is ~70% done but the worked examples and the LSP method reference contain factual errors that the "Last verified to compile" date claims to have checked.** Specifically:

* `packages/semantics/README.md`'s worked example imports a non-existent `loadManifest`, calls non-existent `ProjectSymbolTable.allOfKind`, calls a `Resolver.resolve(...)` that doesn't exist (real method is `resolveReference(ref, context)`), and constructs `Validator` with the wrong manifest shape. The "Last verified to compile: 2026-05-17" stamp is false (verified non-compiling).
* `packages/lsp/README.md`'s `modeler/listSymbols` documents a request shape (`{ query?, kinds? }`) and response fields (`sourceUri`, `sourceLine`) that don't match the actual handler (`{ kinds?, limit? }` returning `{ qname, kind, name }`).
* `docs/design/diagnostics.md`'s `ttr/parse-recovery-info` example still says `"recovered at '{'"` — the implementation was changed to `"parser resumed after syntax error at '{'"` in review-022 (I-6); doc and code disagree.
* `README.md`'s K.4 requirements are partly met: deployed Designer URL missing, screenshot missing, `@modeler/grammar` and `@modeler/vscode-ext` not linked in the package READMEs list, "pnpm 9+" prerequisite is stale (repo pins `pnpm@11.1.1`).
* `packages/designer/README.md` is unchanged from review-018 — no screenshot, no deploy URL, no "embed via `<script>`" placeholder section. Progress doc claims "K.3 was already complete; confirmed up-to-date" — three K.3 bullets are missing.
* `packages/vscode-ext/README.md` is unchanged from Phase 0 — J.5 said to document the local smoke-test command there. Not done.

Plus the `.github/workflows/ci.yml` jobs (`ci` *and* new `vscode-smoke`) don't install pnpm before `pnpm install --frozen-lockfile`. `designer-deploy.yml` correctly uses `pnpm/action-setup@v4`; ci.yml does not. The pre-existing job has been quiet because `actions/setup-node@v4` with `cache: 'pnpm'` *sometimes* enables Corepack, but it's brittle and certainly does not guarantee pnpm 11.1.1.

Sections J and K both need rework before Phase 3 can be marked done.

---

## Section J — VS Code smoke test

### J-1 ⚠ CRITICAL. Smoke test cannot run (`__dirname` in ESM)

`packages/vscode-ext` declares `"type": "module"`. TypeScript compiles `__dirname` references into emitted JavaScript that references the global `__dirname`. In ESM that global doesn't exist. Repro:

```bash
$ node packages/vscode-ext/dist/test/runTests.js
ReferenceError: __dirname is not defined
    at main (file:///.../packages/vscode-ext/dist/test/runTests.js:4:51)
    at file:///.../packages/vscode-ext/dist/test/runTests.js:13:1
```

Same shape in `dist/test/suite/index.js:12` (`path.resolve(__dirname, '.')`) and `dist/test/suite/extension.smoke.test.js:5` (`path.resolve(__dirname, '../../../../../samples/v1-metadata')`). Three call sites.

The plan's `Verify by running` calls out `pnpm --filter @modeler/vscode-ext test:smoke`. That command currently:

1. Runs `pnpm run build` (succeeds).
2. Runs `node ./dist/test/runTests.js` (immediately crashes on `__dirname`).

Fixes (pick one):

**Preferred — ESM shim at each call site.** Standard pattern; works without changing module format. In each of the three .ts files:
```ts
import { fileURLToPath } from 'node:url';
import * as path from 'node:path';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
```

**Alternative — emit CJS for the test entry only.** Add `packages/vscode-ext/src/test/tsconfig.test.json` overriding `"module": "CommonJS"`, and adjust the build to compile the test tree separately. Heavier; not recommended because the surrounding extension code is already ESM and pulls together cleanly.

**Verification gap:** there is no test in the suite that checks "the harness boots" — only the five smoke cases that run inside the harness. The first time a developer runs `test:smoke` after this fix should be wired into CI before claiming J is done. See J-3 below.

### J-2 ⚠ CRITICAL. Plan's "DONE when" gate not met

`J-vscode-smoke.md` "DONE when":
* [ ] *All five smoke test cases pass locally.* — Cannot have passed; the harness doesn't start.
* [ ] *CI `vscode-smoke` job runs and passes at least once on a PR.* — Cannot have passed for the same reason; plus ci.yml has the pnpm-setup gap (J-4).

Untick J.1–J.6 in `progress-phase-03.md` until J-1 is fixed *and* the test:smoke command produces 5 ✓ marks locally *and* the CI job's first run is green.

### J-3 (HIGH) TC3 and TC4 assertions are too weak / wrong

`extension.smoke.test.ts:37-49` (TC3):

```ts
const idx = content.indexOf('nameAttribute:');
const pos = doc.positionAt(idx + 'nameAttribute:'.length);
editor.selection = new vscode.Selection(pos, pos);
await vscode.commands.executeCommand('editor.action.revealDefinition');
const revealed = vscode.window.activeTextEditor;
assert.ok(revealed, 'active editor after go-to-def');
assert.ok(revealed!.selection.start.line >= 0);
```

Two issues:

* **Cursor lands outside the reference.** `idx + 'nameAttribute:'.length` is the position right after the colon, on the space between `nameAttribute:` and `id_artiklu`. Go-to-definition from there usually finds nothing. The plan says "position cursor inside an attribute reference (e.g. `nameAttribute: id_artiklu`)". Move past the space — find the actual referent name and put the cursor inside it:
  ```ts
  const m = content.match(/nameAttribute:\s+(\w+)/);
  if (!m) throw new Error('no nameAttribute reference');
  const refIdx = content.indexOf(m[1], m.index! + 'nameAttribute:'.length);
  const pos = doc.positionAt(refIdx + 1);  // anywhere inside the identifier
  ```
* **The assertion `selection.start.line >= 0` passes trivially** — every position has `line >= 0`. The test silently passes even when go-to-def is broken. The plan's wording was "assert `vscode.window.activeTextEditor!.selection.active.line` matches the def's line". Capture the original cursor line *before* the command, then assert that the post-command line differs (and ideally that the URI or line matches the known def location):
  ```ts
  const before = editor.selection.active.line;
  await vscode.commands.executeCommand('editor.action.revealDefinition');
  const after = vscode.window.activeTextEditor!.selection.active.line;
  assert.notStrictEqual(after, before, 'cursor should jump to def');
  ```

`extension.smoke.test.ts:51-71` (TC4):

```ts
const insertLine = Math.min(5, doc.lineCount - 1);
const insertPos = new vscode.Position(insertLine, 0);
edit.insert(doc.uri, insertPos, '\n  nameAttribute: nonexistent_attr_xyz\n');
// ...
const revertEdit = new vscode.WorkspaceEdit();
revertEdit.delete(doc.uri, new vscode.Range(insertPos, insertPos.translate(1, 0)));
await vscode.workspace.applyEdit(revertEdit);
await doc.save();
```

* **The insert adds three line breaks' worth of content, the delete removes one line.** Net: two extra lines remain. Then `doc.save()` writes the corrupted contents to the real `samples/v1-metadata/er.ttr` on disk — modifying a versioned sample file. Repeated CI runs will keep growing the file (or, more likely, fail in confusing ways on the second run).
* **Don't save the doc at all.** The test's revert should restore the in-memory doc; never write to disk during a smoke test. Drop the `await doc.save();` line entirely and make the revert match the exact insertion span (count the lines you inserted, including the trailing newline).
* Better still: open a *throwaway copy* of `er.ttr` in a temp directory and operate on that. The current pattern is one accidentally-saved revert away from polluting the repo.

These are both observable as silent passes if the bug at J-1 is fixed but the assertions stay weak.

### J-4 (HIGH) `ci.yml` missing pnpm setup

`.github/workflows/ci.yml:13-18, 30-35`:

```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'pnpm'

- run: pnpm install --frozen-lockfile
```

No `pnpm/action-setup@v4` step before. The pre-existing `ci` job has been silent because GitHub Actions runners ship with corepack and *sometimes* honour the root `package.json` `packageManager: "pnpm@11.1.1"` declaration. That's accidental, not guaranteed. Compare to `designer-deploy.yml:30-31`:

```yaml
- name: Setup pnpm
  uses: pnpm/action-setup@v4
```

Add the same step to both `ci` and the new `vscode-smoke` job. Drop the explicit version; the action reads `packageManager` from the root `package.json`.

While you're in there: the `vscode-smoke` job's `pnpm install --frozen-lockfile` runs against the root workspace, then `pnpm --filter @modeler/vscode-ext build` and `pnpm --filter @modeler/vscode-ext test:smoke`. The `build` step transitively builds `@modeler/lsp`, but only if pnpm resolves workspace deps; verify after the pnpm setup is fixed.

### J-5 (MEDIUM) vscode-ext README not updated

Plan J.5: *"Document the local-run command in `packages/vscode-ext/README.md`."* The file is unchanged from Phase 0 — no `test:smoke` section, no `xvfb-run` mention.

Add a "Smoke tests" section near "Building":

```markdown
## Smoke tests

Boot a real VS Code window via `@vscode/test-electron`, open the
`samples/v1-metadata/` workspace, and run TC1–TC5 (language detection,
diagnostics, go-to-def, unresolved-reference, workspace symbols).

```bash
# Local (macOS / Linux / Windows):
pnpm --filter @modeler/vscode-ext test:smoke

# On a Linux CI runner that has no display:
xvfb-run -a pnpm --filter @modeler/vscode-ext test:smoke
```

The harness lives in `src/test/`; assertions are in `suite/extension.smoke.test.ts`.
```

K.4 also depends on this section existing (it lists the vscode-ext README touch as a required artifact).

### J-6 (LOW) `samples/v1-metadata` opens as workspace — but document opening uses absolute paths

`extension.smoke.test.ts` opens documents with `path.join(samplesDir, 'er.ttr')` where `samplesDir` is computed from `__dirname` (broken anyway, but assuming the J-1 fix). That works only if the harness still has access to the absolute path *and* the launched VS Code instance can open files outside the workspace folder.

VS Code can, but a cleaner pattern is `vscode.workspace.workspaceFolders![0].uri.fsPath` — the test then doesn't care where the harness was run from. Optional refactor after J-1 lands.

---

## Section K — Documentation

### K-1 ⚠ HIGH. `semantics/README.md` worked example doesn't compile

`packages/semantics/README.md:62-85`:

```ts
import { ProjectSymbolTable, Resolver, Validator, loadManifest } from '@modeler/semantics';
// ...
const byKind = table.allOfKind('entity');
// ...
const resolved = resolver.resolve(ref, 'er', '', 'er.entity.artikl');
// ...
const validator = new Validator(table, resolver, { lint: { strict: false, requireDescriptions: false } });
```

All four issues verified against `packages/semantics/src/index.ts` and the source:

| Claim in README | Reality |
|---|---|
| `loadManifest` exported from `@modeler/semantics` | Not exported. Actual exports: `parseManifest`, `resolveManifest`. |
| `ProjectSymbolTable.allOfKind(kind)` | Method does not exist on the class. |
| `resolver.resolve(ref, schemaCode, namespace, enclosingQname)` | Method does not exist. Actual: `resolver.resolveReference(ref, context)` where `context: { schemaCode, namespace, enclosingQname? }`. |
| `new Validator(table, resolver, { lint: { ... } })` | The third arg is `ResolvedManifest` (`{ preferredLanguage: string }`). The shape shown is invalid. |

Plus `loadProject` is documented at line 47 as importable from `@modeler/semantics` — actually only exported from `@modeler/semantics/node-only`. The "Node / Browser Split" subsection at line 89 should mention this concretely; right now it lists `loadStockVocabularies` but not `loadProject`.

The "Last verified to compile: 2026-05-17" stamp at line 87 is false. Verified by creating a one-line file that imports `loadManifest`:

```
src/__tests__/_readme_check.ts(2,51): error TS2305:
  Module '"@modeler/semantics"' has no exported member 'loadManifest'.
```

Fix by writing the example against the real API. Then add a vitest test under `packages/semantics/src/__tests__/readme-example.test.ts` that imports the snippet's identifiers — exists for the sole purpose of failing the build if the example drifts.

### K-2 ⚠ HIGH. `lsp/README.md` `modeler/listSymbols` docs are wrong

`packages/lsp/README.md:135-156`:

```ts
**Request:** { query?: string; kinds?: string[]; }
**Response:** Array<{ name: string; kind: string; qname: string; sourceUri: string; sourceLine: number; }>
```

Actual handler (`packages/lsp/src/server.ts:400-407`):

```ts
connection.onRequest('modeler/listSymbols', (params: { kinds?: string[]; limit?: number }) => {
  const limit = params.limit ?? 500;
  const allowed = params.kinds ? new Set(params.kinds) : null;
  return projectSymbols.all()
    .filter((s) => !allowed || allowed.has(s.kind))
    .slice(0, limit)
    .map((s) => ({ qname: s.qname, kind: s.kind, name: s.name }));
});
```

* **No `query` parameter** in the request. README invents one.
* **Has `limit`** in the request. README omits it.
* **No `sourceUri` / `sourceLine` fields** in the response. README invents both.

A consumer following the README would write code that compiles but silently no-ops on `query`, never sets `limit`, and crashes at runtime when accessing `result.sourceLine`. Either fix the README or extend the handler to match — the README's signature is in fact more useful (the `query` filter would save N+1 round-trips for kind-aware fuzzy search) so consider extending the handler if a real consumer wants it.

### K-3 ⚠ MEDIUM. `diagnostics.md` recovery-info example stale

`docs/design/diagnostics.md:40`:

> ANTLR recovers by synthesizing a placeholder name; one `ttr/parse-error` (unexpected end of input) and one `ttr/parse-recovery-info` ("recovered at '{'") are both emitted.

Implementation was changed in review-022 I-6 to emit `"parser resumed after syntax error at '{'"` (or `"parser skipped token to continue at '{'"` for `recoverInline`). The doc still illustrates the old wording. Two consequences:

* New users reading the doc will be confused when their Problems panel shows different text.
* Any test or grep keyed on `"recovered at"` won't match production behaviour.

Update the example text to match the live messages. Same applies to anywhere `recovered at` appears in the docs.

### K-4 (MEDIUM) Top-level `README.md` deviations

Plan K.4:

> Add a Designer section with the deployed URL and a thumbnail / screenshot.
> Link the three package READMEs.
> Add a short "Phase status" line: Phase 3 shipping, with a link to `docs/plan/tasks-phase-03-designer.md`.

Status:

| Item | Done? |
|---|---|
| Designer section + deploy URL + screenshot | **Partial** — Designer mentioned in passing in "Phase Status" but no deployed URL, no screenshot. |
| Link to package READMEs | **Partial** — lists `parser`, `semantics`, `lsp`, `designer`. Missing `@modeler/grammar`, `@modeler/vscode-ext`. |
| Phase status line | Done. |

Plus:

* Line 69 says `pnpm 9+` as prerequisite. Repo pins `pnpm@11.1.1` via `packageManager`. Either drop the version constraint or update to `pnpm 11+`.
* Section "Graphical Designer" (lines 14-32) still describes the *intent* to fork Ontology Playground — past tense now. Either rewrite to describe what shipped, or remove and link to the package README.

### K-5 (MEDIUM) `designer/README.md` K.3 items missing

Plan K.3:

> What the Designer is + a screenshot.
> How to run dev mode.
> How to load a project (FSA + upload), what the schema and display-mode toggles do, where layout is persisted (Node vs. browser).
> Demo URL + the `?demo=v1-metadata` query parameter.
> Deployment notes.
> "Embed via `<script>`" placeholder section pointing at v1.x.

Status:

| Item | Done? |
|---|---|
| Screenshot | **Missing.** |
| Dev mode | ✓ |
| Loading a project | ✓ |
| Schema / display-mode toggles | **Missing.** README says toggles exist but doesn't say what they do. |
| Layout persistence Node vs browser | Partial — node positions noted, but doesn't say "Node mode writes `.modeler/layout.ttrl`; browser mode keeps state in memory and offers Export Layout download". |
| Demo URL | **Missing the actual URL.** Only `?demo=v1-metadata` query param. |
| Deployment notes | ✓ |
| Embed via `<script>` placeholder section | **Missing as a section.** One throwaway line at the bottom mentions it. |

Progress doc line 118 claims K.3 "was already complete; confirmed up-to-date" — at least four bullets aren't actually there. The text from review-018's tightening pass is still good; what's needed is *new* content for the missing items.

### K-6 (LOW) `vscode-ext/README.md` not updated

Covered under J-5; same file, same omission. Phase 3 ships an extension with smoke tests and the README still describes Phase 0.

### K-7 (LOW) Progress doc test totals

`progress-phase-03.md:136`: `226 tests total (37 parser, 48 semantics, 45 lsp, 61 designer, 6 vscode-ext, 29 integration)`. Matches `pnpm -r test` output exactly. ✓

Note that the five Mocha smoke cases are not counted there because they run via the separate `test:smoke` script. That's correct as long as J actually works — but right now those five are zero-passing-zero-failing because the harness doesn't boot.

---

## End-to-end verification

```bash
pnpm -r build                                # green
pnpm -r test                                 # 226 passed (vitest only)
pnpm -r lint                                 # green
pnpm -r typecheck                            # green

pnpm --filter @modeler/vscode-ext test:smoke
# fails: ReferenceError: __dirname is not defined in ES module scope
```

The green bar masks Section J's failure because:
* `pnpm -r test` runs vitest, not the Mocha smoke harness.
* `pnpm -r build` succeeds — TypeScript happily emits `__dirname` references; the runtime explosion only happens when Node executes the file.

Same "intent vs truth" pattern flagged across reviews 017, 018, 020, 022. Don't tick J.1–J.6 until `test:smoke` produces five ✓ marks locally — both there and in `progress-phase-02.md` §M.

---

## Verdict

* **Section J: not done.** J-1 (broken harness) and J-2 (untested gate) are blocking. J-3/J-4/J-5/J-6 are cleanups required to make the suite trustworthy.
* **Section K: not done.** K-1 (semantics example doesn't compile) and K-2 (lsp listSymbols doc wrong) are factual errors that explicitly violate the K plan's review checks ("Worked examples … compile when copy-pasted"). K-3/K-4/K-5/K-6 are deltas against the K checklist.

Task list with concrete steps: `tasks-review-024.md`.
