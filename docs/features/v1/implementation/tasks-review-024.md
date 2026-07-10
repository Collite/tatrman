# Tasks — Review 024

Section J doesn't actually function (smoke harness crashes at `__dirname` in ESM). Section K has factual errors that violate its own "worked examples compile" review check. Sections F/G/H/I close-out remain valid; J/K do not.

Work top-down. ⚠ tasks block Phase 3 close-out.

---

## J-1 ⚠ — Fix `__dirname` in the ESM test harness (CRITICAL)

`@modeler/vscode-ext` is ESM. `__dirname` doesn't exist in ESM modules. Three source files reference it; all three need the shim.

For each of:

- `packages/vscode-ext/src/test/runTests.ts`
- `packages/vscode-ext/src/test/suite/index.ts`
- `packages/vscode-ext/src/test/suite/extension.smoke.test.ts`

- [ ] Add at the top of the file (after the imports):
  ```ts
  import { fileURLToPath } from 'node:url';
  import * as path from 'node:path';  // probably already imported
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  ```
  Keep the existing `__dirname` references unchanged below — they now resolve to the shimmed const.

- [ ] After editing, run:
  ```bash
  pnpm --filter @modeler/vscode-ext build
  node packages/vscode-ext/dist/test/runTests.js
  ```
  Expected: harness boots, launches a VS Code window (or the Electron download begins on first run), runs five Mocha cases, exits 0 if all pass.

  If the Electron download is too slow for local iteration, set `VSCODE_TEST_DATA_DIR` and `VSCODE_TEST_DOWNLOAD_DIR` to cache locations and re-run.

**Verify (final):**
```bash
pnpm --filter @modeler/vscode-ext test:smoke
# expected: 5 ✓ marks, exit 0
```

The `feedback-progress-doc-skepticism` memory entry applies — only tick J.1–J.6 after this command produces 5 ✓ locally, not before.

---

## J-2 ⚠ — Untick J.1–J.6 in the progress doc until J-1 lands

- [ ] In `docs/plan/progress-phase-03.md`, change `- [x]` to `- [ ]` for J.1 through J.6.
- [ ] In `docs/plan/progress-phase-02.md` §M (line 95) revert the "Completed in Phase 3.J (2026-05-17)" to "Phase 3" until the J-1 fix is verified.
- [ ] In `docs/plan/progress-phase-02.md` line 126 (Deferred-to-later-phases table), revert the same way.
- [ ] In `README.md` line 54, drop `J (VS Code smoke test)` from the "Completed sections" list.

Re-tick all of the above once `pnpm --filter @modeler/vscode-ext test:smoke` reports 5 ✓ marks locally AND the new CI `vscode-smoke` job is green on a real PR run.

---

## J-3 ⚠ — Strengthen TC3 and fix TC4's revert (CRITICAL after J-1)

Once the harness boots, the assertions need to actually catch regressions.

### TC3 — go-to-definition

`extension.smoke.test.ts:37-49`:

- [ ] Replace the cursor positioning + assertion with:
  ```ts
  const editor = await vscode.window.showTextDocument(doc);
  const content = doc.getText();

  // Find an actual reference inside `nameAttribute: <id>` (cursor must land
  // INSIDE the identifier, not on the colon or the trailing space).
  const m = content.match(/nameAttribute:\s+(\w+)/);
  if (!m) throw new Error('no `nameAttribute: <id>` reference in er.ttr');
  const refStart = content.indexOf(m[1], m.index! + 'nameAttribute:'.length);
  const pos = doc.positionAt(refStart + 1);   // anywhere inside the identifier
  editor.selection = new vscode.Selection(pos, pos);

  const beforeLine = pos.line;
  await vscode.commands.executeCommand('editor.action.revealDefinition');
  // Wait briefly for the cursor jump to settle.
  await new Promise(r => setTimeout(r, 200));
  const afterLine = vscode.window.activeTextEditor!.selection.active.line;
  assert.notStrictEqual(afterLine, beforeLine, 'cursor should jump to def');
  ```
  This catches the case "go-to-def silently no-ops because cursor was outside the reference" that the current `>= 0` assertion misses.

### TC4 — unresolved reference

`extension.smoke.test.ts:51-71`:

- [ ] Don't `doc.save()`. Saving writes the corrupted contents into the source-of-truth `samples/v1-metadata/er.ttr` in the repo. Drop the `await doc.save();` line.
- [ ] Fix the revert to actually undo the insert. The current insert adds three line breaks of content; the current revert removes one line. Either:
  - **Preferred:** open a *throwaway copy* of `er.ttr` in `vscode.workspace.fs`'s temp area and operate on that. Sample-file pollution becomes impossible.
  - **Or:** capture the original text first and rewrite it byte-for-byte after the assertion:
    ```ts
    const original = doc.getText();
    // ... insert + assert ...
    const restore = new vscode.WorkspaceEdit();
    restore.replace(doc.uri, new vscode.Range(new vscode.Position(0, 0), new vscode.Position(doc.lineCount, 0)), original);
    await vscode.workspace.applyEdit(restore);
    // Note: do NOT save. The unsaved revert is sufficient for the test.
    ```

**Verify:**
```bash
git status -- samples/v1-metadata/er.ttr
# expected: clean, both before and after pnpm --filter @modeler/vscode-ext test:smoke
```

---

## J-4 ⚠ — Add pnpm setup to `.github/workflows/ci.yml` (HIGH)

Both jobs in ci.yml use `actions/setup-node@v4` with `cache: 'pnpm'` but no `pnpm/action-setup@v4`. `designer-deploy.yml:30-31` shows the right pattern.

- [ ] In `.github/workflows/ci.yml`, before `actions/setup-node@v4` in both `ci` and `vscode-smoke` jobs, add:
  ```yaml
  - name: Setup pnpm
    uses: pnpm/action-setup@v4
  ```
  No `with:` block — the action reads the version from root `package.json` `"packageManager": "pnpm@11.1.1"`.

- [ ] After the J-1 fix lands, push to a topic branch and confirm both `ci` and `vscode-smoke` jobs go green. Until then, J cannot be marked done.

**Verify (after push):** check the Actions tab. The "Install dependencies" step should show `pnpm install --frozen-lockfile` using pnpm 11.1.1. The `vscode-smoke` job should output 5 ✓ marks.

---

## J-5 ⚠ — Document the smoke harness in `packages/vscode-ext/README.md`

Plan J.5: *"Document the local-run command in `packages/vscode-ext/README.md`."* Not done.

- [ ] In `packages/vscode-ext/README.md`, add a new section right after "Building":
  ```markdown
  ## Smoke tests

  Boot a real VS Code window via `@vscode/test-electron`, open the
  `samples/v1-metadata/` workspace, and run five Mocha smoke cases:
  language detection, clean diagnostics, go-to-definition, unresolved-reference
  diagnostic, and workspace-symbol search.

  ```bash
  # Local (macOS / Linux / Windows):
  pnpm --filter @modeler/vscode-ext test:smoke

  # On a Linux CI runner with no display:
  xvfb-run -a pnpm --filter @modeler/vscode-ext test:smoke
  ```

  Harness lives in `src/test/`; assertions in `src/test/suite/extension.smoke.test.ts`.
  CI runs the suite on every PR via the `vscode-smoke` job in
  `.github/workflows/ci.yml`.
  ```

K.4 depends on this section existing.

---

## K-1 ⚠ — Make `packages/semantics/README.md` worked example actually compile (HIGH)

`semantics/README.md:62-85` imports/calls multiple names that don't exist on the public surface. The "Last verified to compile" stamp is false.

Replace lines 62-85 with an example that uses the *actual* public API. Verify against `packages/semantics/src/index.ts`'s exports.

- [ ] Open `packages/semantics/README.md`.
- [ ] Replace the worked example block with:
  ```ts
  import { parseString } from '@modeler/parser';
  import {
    ProjectSymbolTable,
    Resolver,
    Validator,
    ReferenceIndex,
    resolveManifest,
  } from '@modeler/semantics';

  // 1. Parse a TTR document.
  const result = parseString(
    `schema er namespace entity
  def entity artikl { nameAttribute: id_artiklu,
    attributes: [def attribute id_artiklu { type: int, isKey: true }] }`,
    'artikl.ttr',
  );
  if (!result.ast) throw new Error('parse failed');
  const ast = result.ast;

  // 2. Build a project-wide symbol table.
  const table = new ProjectSymbolTable();
  const schemaCode = ast.schemaDirective?.schemaCode ?? 'er';
  const namespace = ast.schemaDirective?.namespace ?? '';
  table.upsertDocument('artikl.ttr', ast, schemaCode, namespace);

  // 3. Resolve a reference. Use resolveReference(ref, context).
  const resolver = new Resolver(table);
  const ref = { path: 'id_artiklu', parts: ['id_artiklu'] };
  const res = resolver.resolveReference(ref, {
    schemaCode,
    namespace,
    enclosingQname: 'er.entity.artikl',
  });
  console.log(res.resolved); // true — id_artiklu is in the entity scope

  // 4. Validate. Validator wants a ResolvedManifest, not a raw config.
  const manifest = resolveManifest({ preferredLanguage: 'en' });
  const refIndex = new ReferenceIndex();
  refIndex.upsertDocument('artikl.ttr', ast, schemaCode, namespace, resolver);
  const validator = new Validator(table, resolver, manifest);
  const diags = validator.validateDocument('artikl.ttr', ast);
  console.log(diags); // []
  ```
- [ ] In the lead-in API section (lines 7-58), fix:
  - `table.allOfKind('entity')` → `table.all().filter(s => s.kind === 'entity')`. Or, if you genuinely want a method, add it to `ProjectSymbolTable` and export it (separate task).
  - `resolver.resolve(ref, ...)` → `resolver.resolveReference(ref, context)`.
  - `loadProject` reference: move it to the "Node / Browser Split" section and say it's only available via `@modeler/semantics/node-only`.
  - Drop `loadManifest` (or replace with `resolveManifest` / `parseManifest` as appropriate).

- [ ] Add a vitest unit test under `packages/semantics/src/__tests__/readme-example.test.ts` that imports the snippet's identifiers and instantiates the chain — this makes future drift a build failure:
  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString } from '@modeler/parser';
  import {
    ProjectSymbolTable, Resolver, Validator,
    ReferenceIndex, resolveManifest,
  } from '../index';

  describe('README example smoke', () => {
    it('imports compile and produce expected output', () => {
      const result = parseString(
        `schema er namespace entity\ndef entity artikl { nameAttribute: id_artiklu, attributes: [def attribute id_artiklu { type: int, isKey: true }] }`,
        'artikl.ttr',
      );
      expect(result.ast).not.toBeNull();
      const table = new ProjectSymbolTable();
      table.upsertDocument('artikl.ttr', result.ast!, 'er', 'entity');
      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'id_artiklu', parts: ['id_artiklu'] },
        { schemaCode: 'er', namespace: 'entity', enclosingQname: 'er.entity.artikl' },
      );
      expect(res.resolved).toBe(true);
    });
  });
  ```

- [ ] Update the "Last verified to compile" date at the bottom to today's date after the test is green.

**Verify:**
```bash
pnpm --filter @modeler/semantics test
# expected: new readme-example case green
pnpm --filter @modeler/semantics typecheck
# expected: green
```

---

## K-2 ⚠ — Fix `lsp/README.md` `modeler/listSymbols` section (HIGH)

`lsp/README.md:135-156` documents a request/response shape that doesn't match the implementation.

- [ ] Open `packages/lsp/README.md`.
- [ ] Replace the `modeler/listSymbols` subsection with:
  ```markdown
  ### `modeler/listSymbols`

  Lists all symbols in the workspace, optionally filtered by kind. Used by the
  Designer for kind-aware searches without per-symbol round-trips.

  **Request:**
  ```ts
  {
    kinds?: string[];   // e.g. ['entity', 'relation', 'er2dbEntity']
    limit?: number;     // default 500
  }
  ```

  **Response:**
  ```ts
  Array<{
    qname: string;
    kind: string;       // the TTR def kind, not an LSP SymbolKind
    name: string;
  }>
  ```

  Example:
  ```ts
  const relations = await client.sendRequest('modeler/listSymbols', {
    kinds: ['relation'],
    limit: 100,
  });
  console.log(relations.map(r => r.qname));
  ```
  ```
- [ ] (Optional) If you actually want a `query` filter, add it to the handler in `packages/lsp/src/server.ts:400`:
  ```ts
  connection.onRequest('modeler/listSymbols', (params: { kinds?: string[]; limit?: number; query?: string }) => {
    const limit = params.limit ?? 500;
    const allowed = params.kinds ? new Set(params.kinds) : null;
    const q = params.query?.toLowerCase();
    return projectSymbols.all()
      .filter((s) => !allowed || allowed.has(s.kind))
      .filter((s) => !q || s.qname.toLowerCase().includes(q) || s.name.toLowerCase().includes(q))
      .slice(0, limit)
      .map((s) => ({ qname: s.qname, kind: s.kind, name: s.name }));
  });
  ```
  And add a coverage line in `tests/integration/src/symbol-indexing-extended.test.ts`. Only do this if there's a real consumer for `query`; otherwise just fix the docs.

**Verify:**
```bash
pnpm -r typecheck
pnpm -r test
```

---

## K-3 — Refresh stale recovery-info wording in `diagnostics.md` (MEDIUM)

`docs/design/diagnostics.md:40` still illustrates `"recovered at '{'"`. Implementation emits `"parser resumed after syntax error at '{'"` (review-022 I-6).

- [ ] In `docs/design/diagnostics.md`, find line 40 and update the message string to match the live implementation. While you're at it, grep for `recovered at` anywhere else in `docs/` and update those too.

**Verify:**
```bash
grep -rn "recovered at" docs/
# expected: no matches (or only in historical / changelog contexts)
```

---

## K-4 — Fill the K.4 deltas in top-level `README.md` (MEDIUM)

- [ ] Add a "Designer" section under "Phase Status" with:
  - One sentence on what the Designer is.
  - The deployed URL (the GitHub Pages URL pattern: `https://<owner>.github.io/<repo>/` — fill in once the deploy has run at least once; see designer-deploy.yml).
  - A screenshot. Capture one from `pnpm --filter @modeler/designer dev` (port 5173) with `samples/v1-metadata/` loaded; save to `docs/img/designer.png`; reference via Markdown.

- [ ] In the "Package READMEs" list, add lines for `@modeler/grammar` and `@modeler/vscode-ext`. The K.4 wording said "three package READMEs"; in practice there are five worth linking (grammar, parser, semantics, lsp, designer, vscode-ext = six). List them all so newcomers can find each.

- [ ] Change line 69 `pnpm 9+` to `pnpm 11+`, or drop the constraint and reference `packageManager` in root `package.json`.

- [ ] Lines 14-32 ("Graphical Designer" section) describe the *intent* to fork Ontology Playground in past tense. Phase 3 has shipped; either:
  - Rewrite to describe what's actually built ("Read-only graphical designer; renders `db` and `er` schemas; React + Cytoscape.js; deployed via GitHub Pages — see `packages/designer/README.md`."), or
  - Delete the whole brainstorm dump and link to the package README.

---

## K-5 — Fill the K.3 deltas in `packages/designer/README.md` (MEDIUM)

Progress doc claims K.3 "was already complete; confirmed up-to-date" — four bullets are missing.

- [ ] Add a screenshot (same file as K-4 above; reference both READMEs to one image).
- [ ] Describe what the schema toggle (`db` / `er`) does: switches which schema's graph is rendered; cached so re-toggling is instant.
- [ ] Describe what the display-mode toggle (`just-names` / `with-types` / `with-constraints`) does and which is the default per schema (`with-types` for db, `just-names` for er).
- [ ] Mention layout persistence Node vs browser explicitly: "Node mode (VS Code extension): layouts are written to `<project>/.modeler/layout.ttrl` and survive workspace reload. Browser mode (GitHub Pages): layouts are kept in memory only; use **Export Layout** to download a `.ttrl` you can re-import later."
- [ ] Add the actual deploy URL near the `?demo=v1-metadata` mention, once the deploy has run at least once.
- [ ] Add a dedicated "Embed via `<script>`" subsection at the bottom (the current single-line mention in "Architecture notes" doesn't satisfy K.3's "placeholder section pointing at v1.x"):
  ```markdown
  ## Embedding the Designer (v1.x — not yet)

  Future v1.x ships a `<script>`-tag embed for hosting the Designer inside
  third-party docs sites. The mechanism is described in
  `docs/design/architecture.md` §10 and `docs/plan/implementation-plan.md` §11.
  Out of scope for Phase 3.
  ```

---

## K-6 — Update `packages/vscode-ext/README.md` (MEDIUM)

Already covered under J-5 (the smoke-tests section). The plan lists vscode-ext README as a deliverable of K, separately too — once J-5 lands, K-6 is done.

---

## Final gate

After every box above is ticked:

```bash
# Vitest / build / lint / typecheck:
pnpm -r build
pnpm -r test
pnpm -r lint
pnpm -r typecheck

# Smoke harness (was broken):
pnpm --filter @modeler/vscode-ext test:smoke
# expected: 5 ✓ marks

# READMEs compile-check: the new readme-example.test.ts in semantics catches drift.

# Repo cleanliness:
git status -- samples/v1-metadata/
# expected: clean, including after a smoke-test run
```

Then update the test totals at `progress-phase-03.md`'s Test Results block (vitest count unchanged at 226; add a separate line for the 5 Mocha smoke cases). Tick J.1–J.6 and K.1–K.6 only after the smoke command produces 5 ✓ locally AND the CI `vscode-smoke` job is green at least once.

Per [MEMORY → feedback-progress-doc-skepticism]: `[x]` is the runner's observation, not the developer's intent.
