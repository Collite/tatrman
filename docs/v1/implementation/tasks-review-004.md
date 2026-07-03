# Review 004 — Task list

Companion to `review-004.md`. Phase 2 ships once the **P0** items below close. **P1** items are quality fixes that should land in the same Phase-2 PR. **P2** items can be folded into Phase 3.

For each task:
- Do exactly what the steps say.
- Run the verification command and confirm it exits 0 / matches the expected output.
- Tick the box only when the verification passes AND you've manually re-read the result. **Stop ticking based on "I implemented X" alone — implementations have regressed three phases in a row.**

When a task asks you to add an LSP test, place it in `tests/integration/src/integration.test.ts` (the `PassThrough`-paired-connection harness is already there). Don't duplicate it in `packages/lsp/__tests__/`. Each new test should boot the server, send the request, and assert a non-trivial response.

---

## P0 — Block "Phase 2 done"

### Task 1 — Fix `findNodeAtPosition` so navigation handlers work at all

**Why:** `packages/lsp/src/server.ts:54-72` does a flat `column <= char && endColumn >= char` check; for multi-line defs (every realistic case) this fails as soon as the cursor isn't in column 0. Combined with only iterating top-level defs, this is why `textDocument/definition`, `textDocument/references`, and `textDocument/hover` all return `null`/`[]` at runtime. See `review-004.md` §1.1 and §1.2.

- [ ] Open `packages/lsp/src/server.ts`. Replace `findNodeAtPosition` with a proper range check that descends into nested defs and also into reference properties. Suggested shape:
      ```ts
      type FoundNode =
        | { kind: 'def'; def: Definition }
        | { kind: 'ref'; ref: Reference; from: Definition };

      function isPositionInRange(line: number, char: number, loc: SourceLocation): boolean {
        if (line < loc.line || line > loc.endLine) return false;
        if (line === loc.line && char < loc.column) return false;
        if (line === loc.endLine && char > loc.endColumn) return false;
        return true;
      }

      function findNodeAtPosition(ast: Document, position: { line: number; character: number }): FoundNode | null {
        const line = position.line + 1;
        const char = position.character;

        // walk top-level + nested defs and references, prefer the deepest hit
        let best: FoundNode | null = null;
        let bestArea = Number.POSITIVE_INFINITY;

        function visitDef(def: Definition) {
          if (!isPositionInRange(line, char, def.source)) return;
          const area = (def.source.endLine - def.source.line) * 1000 + (def.source.endColumn - def.source.column);
          if (area < bestArea) {
            best = { kind: 'def', def };
            bestArea = area;
          }

          // recurse into nested attribute / column / parameter / index / etc.
          const children: Definition[] = [];
          if ('attributes' in def && def.attributes) children.push(...def.attributes);
          if ('columns' in def && def.columns) children.push(...def.columns);
          if ('resultColumns' in def && def.resultColumns) children.push(...def.resultColumns);
          if ('parameters' in def && def.parameters) children.push(...def.parameters);
          if ('indices' in def && def.indices) children.push(...def.indices);
          if ('constraints' in def && def.constraints) children.push(...def.constraints);
          for (const child of children) visitDef(child);

          // walk reference-valued properties on the def
          for (const ref of collectReferences(def)) {
            if (isPositionInRange(line, char, ref.source)) {
              const refArea = (ref.source.endLine - ref.source.line) * 1000 + (ref.source.endColumn - ref.source.column);
              if (refArea < bestArea) {
                best = { kind: 'ref', ref, from: def };
                bestArea = refArea;
              }
            }
          }
        }

        for (const def of ast.definitions) visitDef(def);
        return best;
      }
      ```
      Add a small helper `collectReferences(def: Definition): Reference[]` that returns the `Reference` AST nodes attached to the def's known reference-valued properties (`nameAttribute`, `codeAttribute`, `dataType` when it's a reference, `from`/`to` on relations, the targets of `er2db_*`, etc.).
- [ ] Verify by running the harness below:
      ```bash
      cd /Users/bora/Dev/modeler/packages/lsp
      node --input-type=module -e "
        import * as lsp from 'vscode-languageserver/node.js';
        import { PassThrough } from 'stream';
        import { createServerConnection } from './dist/server.js';
        const ct=new PassThrough({objectMode:true}); const st=new PassThrough({objectMode:true});
        const c=lsp.createConnection(new lsp.StreamMessageReader(ct),new lsp.StreamMessageWriter(st));
        const s=lsp.createConnection(new lsp.StreamMessageReader(st),new lsp.StreamMessageWriter(ct));
        c.listen(); s.listen(); createServerConnection(s);
        await c.sendRequest('initialize',{processId:null,rootUri:null,capabilities:{}});
        c.sendNotification('initialized',{});
        c.sendNotification('textDocument/didOpen',{textDocument:{uri:'file:///t.ttr',languageId:'ttr',version:1,text:'schema er namespace entity\\n\\ndef entity artikl { attributes: [def attribute id { type: int }] }\\n'}});
        await new Promise(r=>setTimeout(r,150));
        const def=await c.sendRequest('textDocument/definition',{textDocument:{uri:'file:///t.ttr'},position:{line:2,character:12}});
        console.log('definition for artikl:', JSON.stringify(def));
        process.exit(0);
      "
      ```
      Must print a non-null result with a `uri` and a `range` covering the `def entity artikl …` block. If it prints `null`, the fix isn't in.

### Task 2 — Make `onDefinition` and `onHover` actually follow references

**Why:** Even with Task 1, the handlers currently take the def under the cursor and look up *its own* qname — returning the def's own location. To match the architecture's contract ("Cmd-click on `er.entity.artikl` → jump to its def"), the handler must distinguish "cursor on reference" from "cursor on declaration" and use the `Resolver`. See `review-004.md` §1.3.

- [ ] In `onDefinition`, branch on `found.kind`:
      ```ts
      const found = findNodeAtPosition(ast, params.position);
      if (!found) return null;
      if (found.kind === 'ref') {
        const ctx = { schemaCode: ast.schemaDirective?.schemaCode ?? 'db', namespace: ast.schemaDirective?.namespace ?? '' };
        const res = resolver.resolveReference({ path: found.ref.path, parts: found.ref.parts }, ctx);
        if (!res.resolved) return null;
        return { uri: res.symbol.documentUri, range: sourceLocationToRange(res.symbol.source) };
      }
      // found.kind === 'def': go to the def's canonical location
      const qname = `${ast.schemaDirective?.schemaCode ?? 'db'}.${ast.schemaDirective?.namespace ?? ''}.${found.def.name}`;
      const symbol = projectSymbols.get(qname);
      if (!symbol) return null;
      return { uri: symbol.documentUri, range: sourceLocationToRange(symbol.source) };
      ```
- [ ] In `onHover`, do the same branching: if `found.kind === 'ref'`, resolve and hover the resolved target; if `found.kind === 'def'`, hover the def itself.
- [ ] **Verify by adding an integration test** (see Task 4). Manual harness check:
      ```bash
      # … same harness as Task 1, then:
      # cursor on the `id` inside `nameAttribute: id`
      const def = await c.sendRequest('textDocument/definition', { textDocument: { uri: 'file:///t.ttr' }, position: { line: 4, character: 19 } });
      ```
      Where line 4 is `  nameAttribute: id` (0-indexed line 3 in LSP). Must return the location of `def attribute id { ... }`, not the location of `nameAttribute: id`.

### Task 3 — Build a real reference index for `onReferences`

**Why:** `onReferences` currently iterates the *symbol* table looking for symbols with the same qname as the def under the cursor — by construction this returns at most the def itself. The plan §H.1 required a "Reference index"; it doesn't exist. See `review-004.md` §1.3.

- [ ] In `@modeler/semantics`, add `ReferenceIndex`:
      ```ts
      // packages/semantics/src/reference-index.ts
      import type { Document, Reference, SourceLocation } from '@modeler/parser';

      export interface ReferenceLocation {
        documentUri: string;
        source: SourceLocation;
        targetPath: string;  // the literal text the reference wrote
      }

      export class ReferenceIndex {
        private byDocument: Map<string, ReferenceLocation[]> = new Map();
        private byTargetQname: Map<string, ReferenceLocation[]> = new Map();

        upsertDocument(uri: string, ast: Document, schemaCode: string, namespace: string, resolver: Resolver): void {
          this.removeDocument(uri);
          const locations: ReferenceLocation[] = [];
          for (const ref of collectAllReferences(ast)) {
            const res = resolver.resolveReference({ path: ref.path, parts: ref.parts }, { schemaCode, namespace });
            if (!res.resolved) continue;
            const loc = { documentUri: uri, source: ref.source, targetPath: res.symbol.qname };
            locations.push(loc);
            const list = this.byTargetQname.get(res.symbol.qname) ?? [];
            list.push(loc);
            this.byTargetQname.set(res.symbol.qname, list);
          }
          this.byDocument.set(uri, locations);
        }

        removeDocument(uri: string): void {
          const old = this.byDocument.get(uri) ?? [];
          for (const loc of old) {
            const list = this.byTargetQname.get(loc.targetPath);
            if (!list) continue;
            const filtered = list.filter(l => l.documentUri !== uri);
            if (filtered.length === 0) this.byTargetQname.delete(loc.targetPath);
            else this.byTargetQname.set(loc.targetPath, filtered);
          }
          this.byDocument.delete(uri);
        }

        findByQname(qname: string): ReferenceLocation[] {
          return this.byTargetQname.get(qname) ?? [];
        }
      }
      ```
      Provide a `collectAllReferences(ast: Document): Reference[]` helper that walks every def and every reference-valued property.
- [ ] Export `ReferenceIndex` from `@modeler/semantics/index.ts`.
- [ ] In `server.ts`:
      - Construct `const refIndex = new ReferenceIndex();` next to `projectSymbols`.
      - In `updateSymbolTable`, also call `refIndex.upsertDocument(uri, ast, schemaCode, namespace, resolver);` **after** `projectSymbols.upsertDocument` so the resolver sees fresh symbols.
      - In `onReferences`: branch on `found.kind`. For `ref`: resolve the reference, then `refIndex.findByQname(symbol.qname)` plus the symbol's own location if `params.context.includeDeclaration`. For `def`: build the qname for the def, then `refIndex.findByQname(qname)`.
- [ ] On `documents.onDidClose`, also call `refIndex.removeDocument(event.document.uri)`.

### Task 4 — Add real integration tests for §G/§H/§I/§K/`getProjectInfo`

**Why:** Three phases of placeholder tests. The harness in `tests/integration/src/integration.test.ts` already pairs LSP transports via `PassThrough`. Reuse it; ~10 lines per feature. See `review-004.md` §3.

- [ ] Open `tests/integration/src/integration.test.ts`. Add a new describe block:
      ```ts
      describe('Phase 2 LSP features', () => {
        let client: lsp.Connection;
        let server: lsp.Connection;
        const uri = 'file:///fixture.ttr';
        const text = `schema er namespace entity

      def entity artikl {
        attributes: [def attribute id { type: int }]
        nameAttribute: id
      }
      `;

        beforeAll(async () => {
          const pair = createPairedConnection();
          client = pair.client; server = pair.server;
          createServerConnection(server);
          await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
          client.sendNotification('initialized', {});
          client.sendNotification('textDocument/didOpen', {
            textDocument: { uri, languageId: 'ttr', version: 1, text }
          });
          await sleep(100);
        });

        afterAll(() => { client.dispose(); server.dispose(); });

        it('textDocument/definition on entity name returns its own def location', async () => {
          // line 2 col 12 = 'artikl' in 'def entity artikl {'
          const res = await client.sendRequest('textDocument/definition', {
            textDocument: { uri }, position: { line: 2, character: 12 },
          }) as lsp.Location | null;
          expect(res).not.toBeNull();
          expect(res!.uri).toBe(uri);
          expect(res!.range.start.line).toBe(2);
        });

        it('textDocument/definition on a bare-id reference follows to the attribute def', async () => {
          // line 4 col 19 = 'id' inside 'nameAttribute: id'
          const res = await client.sendRequest('textDocument/definition', {
            textDocument: { uri }, position: { line: 4, character: 19 },
          }) as lsp.Location | null;
          expect(res).not.toBeNull();
          // points back into the same file at the attribute def
          expect(res!.uri).toBe(uri);
          // attribute def is on line 3 (inline)
          expect(res!.range.start.line).toBe(3);
        });

        it('textDocument/hover on entity name returns a non-empty markdown', async () => {
          const res = await client.sendRequest('textDocument/hover', {
            textDocument: { uri }, position: { line: 2, character: 12 },
          }) as lsp.Hover | null;
          expect(res).not.toBeNull();
          expect((res!.contents as { kind: string; value: string }).value).toContain('er.entity.artikl');
        });

        it('textDocument/references on entity name finds the nameAttribute reference', async () => {
          const res = await client.sendRequest('textDocument/references', {
            textDocument: { uri }, position: { line: 2, character: 12 },
            context: { includeDeclaration: true },
          }) as lsp.Location[];
          expect(res.length).toBeGreaterThanOrEqual(1);
        });

        it('workspace/symbol query="art" finds er.entity.artikl', async () => {
          const res = await client.sendRequest('workspace/symbol', { query: 'art' }) as lsp.SymbolInformation[];
          const names = res.map(s => s.name);
          expect(names).toContain('er.entity.artikl');
        });

        it('textDocument/semanticTokens/full returns at least one token', async () => {
          const res = await client.sendRequest('textDocument/semanticTokens/full', { textDocument: { uri } }) as number[] | { data: number[] };
          const data = Array.isArray(res) ? res : res.data;
          expect(data.length).toBeGreaterThan(0);
          // every token is 5 numbers: deltaLine, deltaStart, length, tokenType, tokenModifiers
          expect(data.length % 5).toBe(0);
        });

        it('modeler/getProjectInfo loads modeler.toml when project root has one', async () => {
          const sampleUri = `file://${path.resolve(samplesDir, 'v1-metadata/er.ttr')}`;
          const info = await client.sendRequest('modeler/getProjectInfo', {
            textDocument: { uri: sampleUri },
          }) as { name: string; namespaces: Record<string,string>; ttrFileCount: number };
          // see samples/v1-metadata/modeler.toml: name = "df-erp-metadata"
          expect(info.name).toBe('df-erp-metadata');
          expect(info.namespaces.db).toBe('dbo');
        });
      });
      ```
      The last test requires Task 7 (manifest wiring) to pass.
- [ ] Run `pnpm --filter @modeler/integration-tests test`. Every new test must pass.

### Task 5 — Replace the placeholder `smoke.test.ts`

**Why:** Review-003 P1 Task 17 explicitly forbade `expect(literal).toBe(literal)` placeholder tests. Phase 2 shipped exactly that under the name `smoke.test.ts`. See `review-004.md` §2.11.

Pick **one** of A or B and do it fully. Half-done is worse than either.

#### Option A — Real `@vscode/test-electron` smoke

- [ ] Delete the current `packages/vscode-ext/src/__tests__/smoke.test.ts`.
- [ ] Create `packages/vscode-ext/scripts/run-smoke.js` that uses `@vscode/test-electron`'s `runTests` correctly (extension path = `packages/vscode-ext`, test path = `dist/__tests__/extension-test.js`, `version: 'stable'`). See review-003 task 5 Option A for the exact shape.
- [ ] Create `packages/vscode-ext/src/__tests__/extension-test.ts` (compiled to JS for the EDH runner). Inside the EDH it can `import * as vscode` and:
      1. Open `samples/v1-metadata/er.ttr`.
      2. Assert `doc.languageId === 'ttr'`.
      3. Insert a deliberately-broken edit, wait for `vscode.languages.getDiagnostics(uri)` to be non-empty, assert at least one diagnostic with `code === 'ttr/parse-error'`.
- [ ] Wire `test:smoke` script and a CI job that runs under xvfb.

#### Option B — Remove the placeholder and mark §M deferred

- [ ] Delete `packages/vscode-ext/src/__tests__/smoke.test.ts`.
- [ ] Remove the test:smoke script if it exists.
- [ ] In `progress-phase-02.md` §M, replace the `✅` claim with: `**Status:** Deferred — see review-004 §2.11. Placeholder tests removed; real `@vscode/test-electron` smoke test moves to Phase 3.`

### Task 6 — Wire stock vocabulary loading

**Why:** The `loadStockVocabularies` function has a wrong path and points at a file containing invalid TTR; it's also never invoked. See `review-004.md` §1.5.

- [ ] Open `packages/semantics/src/stock-loader.ts`. Change:
      ```ts
      const STOCK_DIR = join(PKG_ROOT, 'stock');
      ```
      to:
      ```ts
      const STOCK_DIR = join(PKG_ROOT, 'src', 'stock');
      ```
      (`import.meta.url` is in `dist/`, `..` → package root, `src/stock` → actual location.)
- [ ] Open `packages/semantics/src/stock/cnc-roles.ttr`. Replace contents with valid TTR per the grammar:
      ```
      schema cnc namespace role

      def role fact {
        description: "Fact table - contains quantitative data for analysis"
        tags: ["aggregate", "measure", "query"]
      }

      def role dimension {
        description: "Dimension table - contains descriptive attributes for analysis context"
        tags: ["attribute", "context", "describe"]
      }

      // ... repeat for structural, master, transaction, bridge
      ```
- [ ] Update `packages/semantics/tsconfig.json` (or the build config) so that `src/stock/*.ttr` is copied to `dist/stock/*.ttr` on build, and change the loader path to `join(PKG_ROOT, 'dist', 'stock')` instead, so the bundle works after install. Alternative: keep `src/stock/` and ship it as part of the published package via `package.json` `files` field. Pick whichever is simpler; document the choice.
- [ ] In `server.ts`, on `connection.onInitialize`, after constructing `projectSymbols`, call (this needs to be in the node-only entry path, not the browser one):
      ```ts
      // server-stdio.ts boots this — server-browser.ts will pre-populate
      // via a postMessage if needed (Phase 3)
      ```
      Since `loadStockVocabularies` is in `@modeler/semantics/node-only`, put the wiring in `server-stdio.ts` (or a node-only branch of server.ts). Add the parsed stock docs to `projectSymbols` via `upsertDocument` with URI `stock://cnc-roles.ttr`, schemaCode `'cnc'`, namespace `'role'`.
- [ ] Verify:
      ```bash
      # In the integration test, assert that resolveBareId('fact', { schemaCode: 'er', namespace: 'entity' }) resolves.
      # Or via the harness: resolver should now return the fact symbol via cnc.role.fact.
      ```

### Task 7 — Wire `findProjectRoot` + `loadProject` into the LSP

**Why:** The LSP stores a one-time `manifest = resolveManifest(undefined, '')` and never loads `modeler.toml`. `modeler/getProjectInfo` returns defaults. `lint.requireDescriptions` and `lint.strict` never apply. See `review-004.md` §1.4.

- [ ] Move `manifest` and `validator` out of the closure so they can be reassigned. In `createServerConnection`, change:
      ```ts
      let manifest: ResolvedManifest = resolveManifest(undefined, '');
      let validator = new Validator(projectSymbols, resolver, manifest);
      ```
      Leave them as `let`. Add a private helper `async function reloadManifest(rootUri: string)`:
      ```ts
      async function reloadManifest(rootUri: string): Promise<void> {
        const { findProjectRoot, loadProject } = await import('@modeler/semantics/node-only');
        const rootPath = rootUri.startsWith('file://') ? rootUri.slice(7) : rootUri;
        // for didOpen, use the document path:
        const root = await findProjectRoot(rootPath, rootPath);
        const project = await loadProject(root);
        manifest = project.manifest;
        validator = new Validator(projectSymbols, resolver, manifest);
      }
      ```
      Put this **only in the node-only entry** (so it's not invoked in the browser worker, which has no `fs`).
- [ ] In `server-stdio.ts` (post-Task 6 refactor), pass `reloadManifest` or a `manifestLoader` callback into `createServerConnection`. Suggested:
      ```ts
      // server-stdio.ts
      createServerConnection(connection, {
        loadManifest: async (rootUri) => {
          const { findProjectRoot, loadProject } = await import('@modeler/semantics/node-only');
          // ...
        },
      });
      ```
      And in `server.ts`, `createServerConnection(connection: Connection, opts?: { loadManifest?: (rootUri: string) => Promise<ResolvedManifest> })`. Default `loadManifest` returns the empty resolver.
- [ ] On `onInitialize`, if `params.rootUri` is present, call `loadManifest(params.rootUri)`. On `onDidOpen` of a `.ttr`, if a manifest hasn't been loaded yet for this document's tree, call `loadManifest`.
- [ ] In `manifest.ts`, **also accept kebab-case TOML keys**. The architecture and the sample manifest both use `require-descriptions`; the type uses `requireDescriptions`. Normalize in `parseManifest`:
      ```ts
      export function parseManifest(content: string): ProjectManifest {
        const raw = parseToml(content) as any;
        if (raw.lint) {
          if ('require-descriptions' in raw.lint && !('requireDescriptions' in raw.lint)) {
            raw.lint.requireDescriptions = raw.lint['require-descriptions'];
          }
        }
        return raw as ProjectManifest;
      }
      ```
      Or — preferred — change `ProjectManifest.lint` to accept the kebab-case keys directly and normalize them in `resolveManifest`.
- [ ] Verify by the integration test in Task 4 (`modeler/getProjectInfo loads modeler.toml when project root has one` must pass with `name === 'df-erp-metadata'`).

### Task 8 — Wire `validateProject` and reference validation

**Why:** `validateProject` (duplicate-definition diagnostics) is implemented but never called by the LSP. Cross-reference validation (`UnresolvedReference`) is documented and enumerated but not implemented at all. See `review-004.md` §1.7, §1.8, §2.4, §2.5.

- [ ] In `Validator`, add a `validateReferences(uri, ast, contextDocument)` method that walks every `Reference` in the document (use the `collectAllReferences` helper from Task 3), calls `this.resolver.resolveReference(...)`, and for each unresolved one pushes:
      ```ts
      diagnostics.push({
        code: DiagnosticCode.UnresolvedReference,
        severity: this.manifest.lint.strict ? 'error' : 'warning',
        message: `Unresolved reference: ${ref.path} (tried ${result.tried.join(', ')})`,
        source: ref.source,
      });
      ```
- [ ] In `server.ts`, modify `publishDiagnostics` to also call `validator.validateReferences(uri, result.ast, { schemaCode, namespace })` and merge those diagnostics in.
- [ ] In `server.ts`, after each `updateSymbolTable`, also call `validator.validateProject()` and publish its diagnostics across all open documents (project-level diagnostics need to be re-published on every change because adding a doc can create or remove duplicates).
- [ ] Add integration tests in `tests/integration/src/integration.test.ts`:
      - A document with an unresolvable reference must produce a `ttr/unresolved-reference` diagnostic.
      - Two documents defining the same qname must each get a `ttr/duplicate-definition` diagnostic.
- [ ] Add unit tests in `packages/semantics/src/__tests__/`:
      - `resolver.test.ts` — at least 4 tests: dotted resolve hit, dotted resolve miss with `tried` populated, bare-id resolve via enclosing scope, bare-id resolve via stock vocab.
      - `validator.test.ts` — at least 6 tests covering each emitted DiagnosticCode (RequiredPropertyMissing on empty entity, EntityAttributeNotFound on bad nameAttribute, PrimaryKeyColumnNotFound, UnresolvedReference, DuplicateDefinition, validator respects `lint.strict`).

### Task 9 — Fix semantic-tokens emission

**Why:** Current code emits one token per def using `def.source.endColumn - def.source.column` as the token length — which on multi-line defs is the entire body's character count, not the name length. See `review-004.md` §2.10.

- [ ] **Preferred:** in `walker.ts`, add a `nameLocation: SourceLocation` field to every def variant in `ast.ts`. Populate it from the `id` token's location during the walk. Then in `server.ts`'s semantic-tokens handler use `def.nameLocation.line`, `def.nameLocation.column`, and `def.nameLocation.endColumn - def.nameLocation.column` as the token's line, start, and length.
- [ ] **Fallback (no AST change):** in the semantic-tokens handler, parse the `def <kind> <name>` text fragment from the document's text using the def's `source.line`/`source.column`/`name` to compute a single-line range for the name. Less robust if a def's first line has unusual whitespace, but it works for v1.
- [ ] Add the assertion in Task 4's `textDocument/semanticTokens/full` test to also verify the length of each token is `name.length` (not the def's span width). For a def `def entity artikl {…}`, tokens for `artikl` should have `length === 6`.

### Task 10 — Fix `parseQname` namespace detection

**Why:** Currently uses the schemaCode list to gate namespace detection — broken for any real qname. See `review-004.md` §1.6.

- [ ] Open `packages/semantics/src/qname.ts`. Replace `parseQname` with:
      ```ts
      export function parseQname(text: string): Qname | null {
        const segments = text.split('.');
        if (segments.length < 2) return null;
        const schemaCode = segments[0];
        if (!['db', 'er', 'map', 'query', 'cnc'].includes(schemaCode)) return null;
        // namespace is segments[1] when present; remaining segments are parts.
        // Convention: qname is always <schemaCode>.<namespace>.<...parts>
        // For schemas where namespace is omitted, parts start at segments[1].
        return {
          schemaCode,
          namespace: segments[1] ?? '',
          parts: segments.slice(2),
        };
      }
      ```
- [ ] Add tests in `packages/semantics/src/__tests__/qname.test.ts` (new file):
      - `parseQname('er.entity.artikl')` → `{ schemaCode: 'er', namespace: 'entity', parts: ['artikl'] }`.
      - `parseQname('db.dbo.QZBOZI_DF')` → `{ schemaCode: 'db', namespace: 'dbo', parts: ['QZBOZI_DF'] }`.
      - `parseQname('cnc.role.fact')` → `{ schemaCode: 'cnc', namespace: 'role', parts: ['fact'] }`.
      - `parseQname('not-a-schema.x.y')` → `null`.
      - Round-trip: `qnameToString(parseQname(s)) === s` for the canonical inputs above.
- [ ] Run `pnpm --filter @modeler/semantics test`. All pass.

### Task 11 — Promote `@modeler/parser` to `dependencies` in `@modeler/semantics`

**Why:** `stock-loader.ts` imports `parseString` (runtime) and `validator.ts` imports `DiagnosticCode` (runtime enum) from `@modeler/parser`. Currently in devDependencies — hoisting saves it locally, but a published install breaks. See `review-004.md` §4.1.

- [ ] Open `packages/semantics/package.json`. Remove `@modeler/parser` from `devDependencies`. Add to `dependencies`:
      ```json
      "dependencies": {
        "@modeler/parser": "workspace:*",
        "smol-toml": "^1.6.1"
      }
      ```
- [ ] Run `pnpm install`. Then `pnpm -r build && test`. All exit 0.

### Task 12 — Fix or remove `exports.node-only.require`

**Why:** Declares a `.cjs` file that doesn't exist. See `review-004.md` §4.2.

Pick **one** of A or B.

#### Option A — Remove the CJS export

- [ ] In `packages/semantics/package.json`, change the `node-only` exports entry to:
      ```json
      "./node-only": {
        "import": "./dist/node-only.js",
        "types": "./dist/node-only.d.ts"
      }
      ```

#### Option B — Build a CJS variant

- [ ] Add a tsc build that emits `.cjs` (separate `tsconfig.cjs.json`). More invasive; only worth it if you need CJS consumers (none today). Recommend Option A.

### Task 13 — Progress-doc accuracy pass

**Why:** Pattern of `[x]` on broken features. See `review-004.md` §3. Don't tick a box until you've run an end-to-end command that demonstrates the feature works.

- [ ] Open `docs/plan/progress-phase-02.md`. For each `✅`, run a command from this review or `tasks-review-004.md` that exercises the feature. If it doesn't return the expected result, change `✅` to `[ ]` with a note pointing at the task that fixes it.
- [ ] Specifically reflip these to `[ ]` unless they pass the verifications above:
  - §B.3 modeler/getProjectInfo
  - §B.4 sample manifest integration test
  - §C.4 stock vocabulary
  - §D Reference resolver (entire section — wired status, not "library exists")
  - §D.4 Tests
  - §E.3 Cross-reference checks
  - §E.4 Duplicate-definition checks (validateProject called)
  - §E.6 Tests
  - §G go-to-definition
  - §H find-references
  - §I hover
  - §I.3 Tests
  - §K semantic tokens (token producer correctness + tests)
  - §M VS Code smoke test
- [ ] Update the Test Results block at line 134 with the actual `pnpm -r test` counts after all P0 tasks land. Don't include numbers from `node-only` sub-build artifacts.

### Task 14 — Commit Phase 2

**Why:** 17 modified + 16 new files all uncommitted. Phase 2 can't be reviewed-as-merged from this state.

- [ ] After Tasks 1-13 land, stage explicitly (don't `git add -A`):
      - `git add packages/parser/src/{ast,index,walker,diagnostics}.ts`
      - `git add packages/parser/src/__tests__/parser.test.ts`
      - `git add packages/semantics/` (recursive, but check `git status` after)
      - `git add packages/lsp/src/server.ts packages/lsp/src/server-stdio.ts packages/lsp/src/server-browser.ts packages/lsp/package.json packages/lsp/src/__tests__/`
      - `git add packages/vscode-ext/src/__tests__/ packages/vscode-ext/vitest.config.ts`
      - `git add packages/designer/src/lsp-client.ts`
      - `git add pnpm-lock.yaml`
      - `git add samples/v1-metadata/modeler.toml`
      - `git add docs/plan/progress-phase-02.md docs/plan/tasks-phase-02-core.md`
      - `git add docs/design/diagnostics.md docs/plan/progress-phase-01.md docs/plan/implementation-plan.md`
      - `git add review-004.md tasks-review-004.md`
- [ ] Commit with a message that distinguishes "implemented" from "wired":
      ```
      Phase 2: Core tier (AST, semantics, navigation, hover)

      - §A AST completion: 17 def types, full walker (1280 LOC)
      - §B project model: smol-toml, findProjectRoot/loadProject, getProjectInfo wired
      - §C symbol table: per-doc + project-wide, incremental, stock-vocab loaded
      - §D resolver: dotted + bare-id paths, wired into LSP nav + validator
      - §E validator: per-doc structural + cross-reference + duplicate detection
      - §G/H/I navigation: go-to-definition, find-references, hover with reference index
      - §J workspace/symbol: fuzzysort-backed
      - §K semantic tokens: name-only emission
      - §L parse-recovery-info: deferred again — see review-004
      - §M smoke test: [chosen option from Task 5]

      See review-004 for the closure history.
      ```
- [ ] Run `git status`. Working tree must be clean.

**Final verification gate:**
```bash
git clean -fdx -e node_modules
pnpm install --frozen-lockfile
pnpm -r build && pnpm -r test && pnpm -r lint && pnpm -r typecheck
pnpm --filter @modeler/integration-tests test
# Plus: every test added by Tasks 4, 6 (stock vocab resolution), 7 (manifest), 8 (validator) passes.
```

---

## P1 — Land in the same Phase 2 PR (quality fixes)

### Task 15 — Use `path` joining where Node-only code uses string concat

- [ ] `packages/parser/src/index.ts:75, 78`: `dir + '/' + entry.name` → `path.join(dir, entry.name)`.
- [ ] `packages/semantics/src/manifest.ts:26`: `projectRoot.split('/').pop()` → `path.basename(projectRoot)`. Note: manifest.ts is the browser-safe module; if `path` isn't available there, accept the limitation and document.

### Task 16 — Remove `--external:fuzzysort` from the browser bundle or document the requirement

The Designer build re-bundles fuzzysort via Vite, so it works there. But if anyone else consumes `@modeler/lsp/browser` directly, they need fuzzysort in scope. Either drop the external (the bundle gets ~50 KB bigger) or add a note in `packages/lsp/README.md` that the browser bundle requires fuzzysort to be available at runtime.

### Task 17 — Fix `ProjectSymbolTable.findByName` to not dedupe-by-qname

Currently `findByName` calls `this.all()` which deduplicates by qname. If a qname is defined in two documents, only one entry is returned. Iterate `byQname.values()` (which gives `SymbolEntry[]` per qname) instead and concat.

### Task 18 — Index `relations`, `queries`, `roles`, etc. in the symbol table

`DocumentSymbolTable.addEntry` only nests attributes (entity), columns (table/view), and resultColumns (procedure). Add: `relations` (entity has `relations`? Actually relations are top-level defs), `indices`/`constraints`/`fks` for tables, parameters for procedures and queries, role-defs, er2db_*-defs. Most are already top-level so the only loss today is the implicit "by qname" not covering nested children. Verify by listing every Definition kind and ensuring the table has an entry path for each.

### Task 19 — Establish the "no placeholder tests" rule as a CI check (not just docs)

Review-003 added the rule to `progress-phase-01.md`. Phase 2 violated it. Promote to a CI check: a script that greps each `*.test.ts` for the patterns `expect(true).toBe(true)`, `expect([a-zA-Z_]+).toBeTruthy()` where the argument is a local literal, and fails the build. Coarse but stops the regression.

---

## P2 — Defer to Phase 3 if not done in Phase 2

### Task 20 — Move LSP tests into the integration test package

`packages/lsp/src/__tests__/lsp.test.ts` exists with 4 tests. The integration test package already has the same `PassThrough` harness duplicated. Consolidate: delete the LSP-package tests, move all LSP-method tests into `tests/integration/`. Single source of truth for LSP behavior testing.

### Task 21 — Golden-fixture tests for the AST walker

Plan §A.11 said "Tests + golden fixtures + parseDirectory". `parseDirectory` exists but there are no AST shape assertions. Add `packages/parser/src/__tests__/golden/` with one `.ttr` + one `.expected.json` per def kind; assert AST equals expected. Catches walker regressions.

### Task 22 — Reference index for the architecture's stated `er.entity.artikl.id_artiklu` shape

The current Reference (post Task 3) records the source location of the reference text. The architecture says references should also carry a "what was tried in resolution" trail for diagnostics. Wire `tried: string[]` from `ResolutionResult` into the `ttr/unresolved-reference` diagnostic's `data` field so quick-fix actions in Phase 3 can use it.

---

## Definition of done for Phase 2

- [ ] Tasks 1–14 (P0) checked.
- [ ] All Phase 2 LSP methods have ≥1 integration test in `tests/integration/`.
- [ ] On the harness in Task 1, `textDocument/definition`, `textDocument/hover`, and `textDocument/references` all return non-null/non-empty results for cursor positions inside the test fixture.
- [ ] `modeler/getProjectInfo` returns the values from `samples/v1-metadata/modeler.toml` (verified by the test added in Task 4).
- [ ] `loadStockVocabularies` is called by `server-stdio.ts` on initialize, and `resolver.resolveBareId('fact', ...)` resolves successfully via `cnc.role.fact`.
- [ ] `ttr/unresolved-reference` and `ttr/duplicate-definition` are emitted by the LSP and visible in client-received diagnostics.
- [ ] No `*.test.ts` file contains `expect(true).toBe(true)` or `expect(<local literal>).toBe(<same literal>)`.
- [ ] `progress-phase-02.md` `✅` marks all backed by demonstrable runtime behavior.
- [ ] `git status --short` empty.

P1 / P2 items roll into Phase 3's first commit.
