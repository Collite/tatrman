# Review 004 — Phase 2 (Core tier — semantics, navigation, hover)

**Reviewer:** Claude (Opus 4.7)
**Date:** 2026-05-14
**Subject:** Developer claims Phase 2 is done. Plan: `docs/plan/tasks-phase-02-core.md`. Progress log: `docs/plan/progress-phase-02.md`.
**Verdict:** **Not done.** Real Phase-2 substance shipped (1280-line walker, full per-kind AST, symbol table, manifest TOML parser, project loader, expanded diagnostic taxonomy, workspace symbol search). But the three navigation features that justify Phase 2's existence — **go-to-definition, find-references, and hover — are runtime-broken** (return `null`/`[]` for every query). Cross-reference validation isn't wired. Stock vocabulary is never loaded. The "VS Code smoke test" is the same placeholder pattern that review-003 explicitly banned. The progress doc carries ten `✅` marks for things that don't run. The end-user — a modeler authoring TTR in VS Code — will Cmd-click on `er.entity.artikl` and the editor will silently do nothing.

Companion task list: `tasks-review-004.md`.

This is the third phase in a row where `progress-*.md` ticks `[x]` for items that don't work. The pattern is now systemic — see §3.

---

## 0. Build / test / lint status

| Command | Result | Notes |
|---|---|---|
| `pnpm -r build` | ✅ 0 | clean (551 ms) |
| `pnpm -r test` | ✅ 0 | 55 tests across 5 packages (parser 19, semantics 16, lsp 4, vscode-ext 11, integration 5) |
| `pnpm -r lint` | ✅ 0 | clean |
| `pnpm -r typecheck` | ✅ 0 | clean |

But: **none of the new Phase-2 LSP methods has a test.** The lsp package's 4 tests are all Phase 0/1 (initialize, parse-error diagnostic, getModelGraph stub, .ttrl gate). vscode-ext's 11 tests are smoke (placeholder) + grammar generator. semantics' 16 tests cover manifest + symbol-table only. **Zero tests** for resolver, validator, hover, definition, references, semanticTokens, or `modeler/getProjectInfo`.

Working tree: **17 modified files + 16 new files, all uncommitted.** Phase 2 cannot be merged from this state.

---

## 1. Runtime verification: navigation features are broken

I exercised each Phase-2 LSP method by booting `createServerConnection` in-process with a `PassThrough` transport, sending `initialize` → `didOpen` of a small TTR fixture, and issuing the protocol requests. Results:

```
input: schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }

textDocument/definition @ artikl (line 2, col 12)  → null
textDocument/definition @ id     (line 3, col 32)  → null
textDocument/hover      @ artikl (line 2, col 12)  → null
textDocument/references @ artikl (line 2, col 12)  → []
workspace/symbol        query="art"                → [er.entity.artikl, er.entity.artikl.id]  ✓
modeler/getProjectInfo                             → default manifest (name:"", namespaces:{}, ...)
```

The symbol table is populated correctly (visible via `workspace/symbol`). The four feature handlers can't find anything because of two intertwined bugs in `packages/lsp/src/server.ts`.

### 1.1 `findNodeAtPosition` range check is wrong

`server.ts:54-72`:

```ts
function findNodeAtPosition(ast, position) {
  const line = position.line + 1;
  const char = position.character;

  for (const def of ast.definitions) {
    if (
      def.source.line <= line &&
      def.source.endLine >= line &&
      def.source.column <= char &&     // bug
      def.source.endColumn >= char     // bug
    ) {
      return { node: def, isReference: false };
    }
  }
  return null;
}
```

A definition that spans multiple lines (every realistic def) has:
- `def.source.line = 3, endLine = 5`
- `def.source.column = 0` (start of `def`)
- `def.source.endColumn = 1` (one past closing `}` on its own line)

For cursor `{line: 3, character: 12}` (clicking on `artikl`), the check becomes `0 <= 12 && 1 >= 12` — false. The function returns `null` for any cursor not in the first column. The handler then returns `null` to the client.

The correct range check is the standard "point in range" pattern: outside if `(line < startLine)` or `(line > endLine)` or `(line == startLine && char < startCol)` or `(line == endLine && char > endCol)`.

### 1.2 `findNodeAtPosition` only walks top-level definitions

The function loops `for (const def of ast.definitions)` and never descends into `def.attributes`, `def.columns`, etc. Clicking on an inline attribute name will never hit a node. Even after fixing §1.1, hover/definition on nested defs would still fail.

### 1.3 The handlers don't follow references at all

Even if `findNodeAtPosition` were fixed, `onDefinition` (lines 200-220) does this:

```ts
const def = found.node as Definition;
const qname = `${ast.schemaDirective?.schemaCode ?? 'db'}.${ast.schemaDirective?.namespace ?? ''}.${def.name}`;
const symbol = projectSymbols.get(qname);
return { uri: symbol.documentUri, range: sourceLocationToRange(symbol.source) };
```

It finds the def under the cursor, builds the def's own qname, and looks that up in the symbol table — which returns the def itself. Go-to-definition is supposed to follow a **reference** (`nameAttribute: er.entity.foo` → jump to `foo`'s def), not return the enclosing def's own location. The Resolver class (`packages/semantics/src/resolver.ts`, verified working in isolation) is never called by any LSP handler.

`onReferences` (lines 222-249) has the same defect plus a self-contradicting filter:

```ts
for (const entry of projectSymbols.all()) {
  if (entry.qname === symbol.qname && entry.documentUri === uri) {
    refs.push(...)
  }
}
```

For a unique qname, this loop matches exactly one entry — the symbol's own definition. No reverse reference index exists despite the plan §H.1 ("Reference index"). Find-references returns at most one location, and only if you're already on the definition.

`onHover` (lines 251-282) is fed by the same broken `findNodeAtPosition`. Hover returns `null` everywhere.

### 1.4 `modeler/getProjectInfo` never loads `modeler.toml`

`server.ts:40`: `let manifest: ResolvedManifest = resolveManifest(undefined, '');` — a default-everything manifest is constructed at server boot. **Nothing else assigns to it.** The handler at line 172 calls `loadProjectFromOpenDocuments(..., manifest)` passing this empty manifest through. The runtime check above confirms: with `samples/v1-metadata/modeler.toml` present (declaring `name = "df-erp-metadata"`, `namespaces = { db = "dbo", ... }`, etc.), the LSP returns `name: ""`, `namespaces: {}`. The `findProjectRoot` / `loadProject` helpers in `@modeler/semantics/node-only` exist and are tested in `semantics.test.ts`, but **the LSP never calls them**.

A consequence: `manifest.lint.requireDescriptions` (used by the validator at server.ts:106 via `this.manifest`) will always be `false`. The "warnings become errors" `strict` mode never engages.

### 1.5 Stock vocabulary is never loaded — and would fail to load even if invoked

Two bugs make `loadStockVocabularies` permanently inert:

1. **Path mismatch.** `stock-loader.ts:6-7`:
   ```ts
   const PKG_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
   const STOCK_DIR = join(PKG_ROOT, 'stock');
   ```
   `import.meta.url` resolves to `<pkg>/dist/stock-loader.js`. `PKG_ROOT` → `<pkg>`. `STOCK_DIR` → `<pkg>/stock`. The actual file is at `<pkg>/src/stock/cnc-roles.ttr`. `STOCK_DIR` should be `join(PKG_ROOT, 'src', 'stock')`. The `try/catch` swallows the ENOENT, returning an empty map.

2. **Stock file content is invalid TTR.** `packages/semantics/src/stock/cnc-roles.ttr` uses:
   ```
   @schema(cnc)
   role fact { ... }
   ```
   That's not the grammar in `packages/grammar/src/TTR.g4`. The grammar requires `schema cnc namespace role` (or some valid namespace) and `def role fact { ... }`. If the path bug were fixed, parsing would still fail with syntax errors, the empty `ast` field would be skipped, and the function would return an empty map anyway.

3. **`loadStockVocabularies` is exported via `node-only` but never invoked anywhere in the workspace.** `grep -rn loadStockVocabularies packages/ tests/` returns three matches: the function definition, two re-exports. Zero call sites.

Result: Cmd-clicking on `fact`/`dimension`/`structural`/`master`/`transaction`/`bridge` in a user's `.ttr` file will produce no resolution and no hover — even though the architecture (§4.3) is explicit that these should pre-load.

### 1.6 `parseQname` mis-detects the namespace

`packages/semantics/src/qname.ts:25`:

```ts
if (['db', 'er', 'map', 'query', 'cnc'].includes(rest[0])) {
  namespace = rest[0];
  parts = rest.slice(1);
} else {
  parts = rest;
}
```

The namespace component is checked against the **schemaCode** set, not against arbitrary identifiers. For `er.entity.artikl` (the canonical shape from the architecture), `rest[0] = 'entity'` — not in the schemaCode list — so `namespace = ''`, `parts = ['entity', 'artikl']`. Wrong. The function is not currently called by anything load-bearing (the LSP builds qnames via string concatenation), but `parseQname` is exported from `@modeler/semantics` and the next phase that consumes it will trip.

### 1.7 The `Resolver` field on `Validator` is dead

`validator.ts:18`: `private resolver: Resolver` is stored but never invoked in `validateDocument` or `validateProject`. The plan §E.3 ("Cross-reference checks") explicitly requires it:

> The validator runs on the resolved AST and produces structured diagnostics.

Today the validator only checks per-document structural things (entity-attribute existence within the same entity, table-pk-column existence within the same table, type presence on column/attribute). No reference is ever resolved by the validator. `ttr/unresolved-reference` is defined in the enum and documented in `diagnostics.md` but **no code emits it**.

### 1.8 `validateProject` is never called

`Validator.validateProject` emits `ttr/duplicate-definition` diagnostics by iterating `projectSymbols.duplicates()`. The LSP `publishDiagnostics` calls only `validator.validateDocument` (server.ts:96). The duplicate-detection capability exists but never reaches the client.

---

## 2. Plan items marked ✅ that don't match the code

### 2.1 §B.4 — "Sample manifest … Integration test consumes it"

`samples/v1-metadata/modeler.toml` exists. **No test in `tests/integration/`, `packages/semantics/__tests__`, or `packages/lsp/__tests__` opens or asserts against it.** Also: the manifest uses `require-descriptions` (kebab-case, matching the architecture doc), but `parseManifest` returns the TOML object verbatim and `resolveManifest` reads `m.lint.requireDescriptions` (camelCase). The key is never read. Two bugs paper over each other (LSP doesn't load the manifest; if it did, the key would be ignored).

### 2.2 §C.4 — "Stock vocabulary ✅"

Covered above. Not loaded, not parsed, not in the symbol table.

### 2.3 §D — "Reference resolver ✅" / §D.4 "Tests ✅"

The Resolver class exists and works in isolation (I verified by hand — it correctly resolves `er.entity.artikl` and `artikl`-in-context). But:
- It's not called by the LSP for navigation (§1.3).
- It's not called by the validator for diagnostics (§1.7).
- There is **no `resolver.test.ts`**. §D.4 "Tests ✅" is false.

### 2.4 §E — "Validator ✅" / §E.6 "Tests ✅"

The Validator emits diagnostics, and four of those are actually wired through to LSP (RequiredPropertyMissing, EntityAttributeNotFound, PrimaryKeyColumnNotFound for `validateDocument`). But:
- §E.3 (Cross-reference checks) is not implemented; `UnresolvedReference` never fires.
- §E.4 (Duplicate-definition checks) is implemented in `validateProject` but never invoked.
- §E.5 (Empty-block warnings) — progress doc shows no checkbox.
- §E.6 — **No `validator.test.ts`**.

### 2.5 §F — "Diagnostic-code expansion ✅"

The enum has the new codes. But three of the six new codes never fire:
- `UnresolvedReference` — dead.
- `InvalidType` — defined; no emission path; defended by neither validator nor parser.
- `DuplicateDefinition` — `validateProject` emits it but is never called.

A diagnostic code that doesn't fire is documentation, not behavior.

### 2.6 §G — "go-to-definition ✅"

`definitionProvider: true` is declared in capabilities. `onDefinition` is registered. **At runtime it returns `null` for every cursor position** (§1.1, §1.2, §1.3). Confirmed via in-process LSP harness. There is no test exercising this path.

### 2.7 §H — "find-references ✅"

`referencesProvider: true` is declared. `onReferences` is registered. **At runtime it returns `[]` for every cursor.** No reverse reference index exists (§1.3). No test.

### 2.8 §I — "hover ✅" / §I.3 "Tests ✅"

`hoverProvider: true` is declared. `onHover` is registered. **At runtime returns `null` for every cursor.** Same `findNodeAtPosition` bug. §I.3 "Tests ✅" is false — no hover tests anywhere in the workspace.

### 2.9 §J — "workspace symbols"

Progress doc has it as `- [ ] Add fuzzysort dependency (pending)` and `Basic workspace symbols implemented (substring matching, no fuzzysort yet)`. **The code actually uses `fuzzysort` (server.ts:308); the dep is installed in `packages/lsp/package.json`.** The progress doc is wrong about both halves of the status. The handler works correctly at runtime (verified). The progress doc just hasn't caught up.

### 2.10 §K — "Semantic tokens ✅"

Handler exists (server.ts:334-358), capability declared. **Token producer is wrong:** it emits one token per top-level definition spanning the entire def from `def.source.column` to `def.source.endColumn`. For multi-line defs (every realistic case), that's a token covering the entire def body — not just the name. The plan §K specified "for each `Definition.name`, emit a `class` token with `declaration` modifier"; the implementation marks the whole def. The progress doc claims "Tests: synthetic AST tokens pass" — there is no semantic-tokens test in `packages/lsp/src/__tests__/lsp.test.ts` or anywhere else.

### 2.11 §M — "VS Code smoke test ✅"

`packages/vscode-ext/src/__tests__/smoke.test.ts` exists with 5 "tests". Body:

```ts
it('server transport kind is stdio', () => {
  const TransportKind = { stdio: 0 };
  expect(TransportKind.stdio).toBe(0);
});

it('document selector targets .ttr files', () => {
  const documentSelector = [{ scheme: 'file', language: 'ttr' }];
  expect(documentSelector[0].language).toBe('ttr');
});
```

**No `@vscode/test-electron`. No `vscode` import. No extension activation. No file open. No diagnostic check.** Every assertion checks a literal that the test itself defines two lines above. This is **exactly** the anti-pattern review-003 P1 Task 17 banned ("never commit a test file whose body is `expect(true|truthy).toBe(...)` or equivalent"). Now committed as 5 passing tests under a `smoke` filename.

### 2.12 §L — "parse-recovery-info emission"

Deferred again. This is the second phase to defer this. Documented honestly in the progress doc this time. Acceptable, but if it's deferred indefinitely, just remove the code from the LSP severity-mapping branch (`server.ts` no longer has it after the Phase-1 cleanup, so this part's actually fine — only the docs still mention it as "carryover").

### 2.13 §N — Documentation

Progress doc admits §N is incomplete:
- `packages/semantics/README.md` — not written.
- `packages/lsp/README.md` — not updated for v2 surface.
- `docs/design/architecture.md` §10 question 6 — not closed.
- `progress-phase-02.md` itself ticked as `In progress`.

Yet the developer claims "Phase 2 is done."

---

## 3. The systemic problem: progress docs ahead of code

This is the third phase in a row with the same pattern:

| Phase | False `[x]` items | Caught in |
|---|---|---|
| 0 | LSP test stub, integration tests, smoke test, language config, launch.json | review-001 |
| 1 | §G semantic tokens, §F recovery-info, §J smoke test, `ttr/unknown-property`/`recovery-info` codes | review-003 |
| 2 | §G definition, §H references, §I hover, §I.3 tests, §D.4 resolver tests, §E.6 validator tests, §C.4 stock vocab, §B.4 manifest test, §K semantic-tokens tests, §M smoke test (again, identical pattern) | review-004 (this doc) |

Three reviews running, the same anti-pattern: scaffolds + placeholders + a progress doc that ticks faster than the implementation. The `smoke.test.ts` regression is the most striking — review-003 P1 Task 17 explicitly forbade `expect(literal).toBe(literal)` placeholders, and Phase 2 shipped exactly that.

Two structural recommendations (not specific code tasks — they go in the project's conventions):

1. **Tick `[x]` only after a reviewer-runnable command demonstrates the feature works end-to-end.** For LSP methods, the command is "boot the server, send the request, assert the response." If you can't write that command, the box stays `[ ]`.

2. **Place the LSP feature tests under `tests/integration/` not `packages/lsp/__tests__/`.** Integration tests already paired `createServerConnection` against a `PassThrough` transport — the harness is built. Reuse it. The pattern below is ~10 lines per feature:
   ```ts
   it('go-to-definition jumps to entity def', async () => {
     await client.sendNotification('textDocument/didOpen', { … });
     const res = await client.sendRequest('textDocument/definition', { … });
     expect(res).toEqual({ uri: …, range: { … } });
   });
   ```
   If §G/§H/§I had even one such test, the broken handlers would have been caught before "claimed done."

---

## 4. Smaller defects (real, but lower priority than §1)

### 4.1 `@modeler/parser` in `devDependencies`

`packages/semantics/package.json:24` lists `@modeler/parser` as devDependency. `stock-loader.ts` and `validator.ts` import `parseString` and `DiagnosticCode` (runtime values) from it. pnpm workspaces hoist devDeps so the import works locally, but anywhere the semantics package is installed standalone (a future publish), runtime imports of a missing devDep fail.

### 4.2 `exports.node-only.require` points at a non-existent `.cjs` file

`semantics/package.json:13` declares `"require": "./dist/node-only.cjs"`. tsc emits only `.js` (ESM) under `type: "module"`. A CJS consumer reaching `require('@modeler/semantics/node-only')` gets ENOENT. Either remove the `require` field or add a CJS build step.

### 4.3 `cnc-roles.ttr` is unparseable

`packages/semantics/src/stock/cnc-roles.ttr` uses `@schema(cnc)` and bare `role fact { … }`. The grammar wants `schema cnc namespace <ns>` and `def role <name> { … }`. Even after fixing the loader path (§1.5), parsing this file produces syntax errors, the ast is undefined, and the loader silently skips it. Either rewrite the file to match the grammar, or — if this file's syntax was inherited from somewhere else and is intentional — fix the loader to translate it.

### 4.4 `--external:fuzzysort` in browser bundle

`packages/lsp/package.json` bundles `server-browser.ts` with `--external:fuzzysort`. The browser worker imports `fuzzysort` as a bare specifier. Vite re-resolves it on the Designer build (so the Designer ships fuzzysort correctly), but any other browser consumer that uses the LSP's browser bundle directly will fail to load. Either drop the external or document the assumption that the LSP browser bundle requires a re-bundling step downstream.

### 4.5 Path joining in `parseDirectory`

`packages/parser/src/index.ts:75, 78` uses string concatenation `dir + '/' + entry.name`. Wrong on Windows. Use `path.join`. Low priority while no Windows CI exists, but `node:path` is already a Node built-in here.

### 4.6 `findNodeAtPosition` ignores `references`

Even with §1.1 fixed, hover/definition needs to know whether the cursor is on a **reference** (then resolve it) or on the **definition itself** (then show its info or list its references). The current handler always treats whatever it finds as a definition. The architecture's `Reference` AST nodes (now present in `ast.ts`) carry source locations — the position resolver should walk references *inside* each definition's properties and prefer a more deeply-nested match.

### 4.7 ProjectSymbolTable.findByName loses duplicates

`project-symbols.ts:61-69` calls `this.all()` (which already deduplicates by qname) and filters by name. If two documents register the same qname, only one is returned. Not load-bearing today since duplicates produce diagnostics — but a future feature that wants "all locations where qname X is defined" needs `byQname.get(qname)`, not `findByName`.

### 4.8 `manifest.ts` uses `String.prototype.split('/')` for project-root name

`manifest.ts:26`: `projectRoot.split('/').pop()`. Wrong on Windows (uses `\`). Equivalent to `path.basename(projectRoot)`. Use `path.basename`.

---

## 5. What is actually solid in Phase 2

Worth saying explicitly; this phase shipped real work:

- **§A (AST completion)** — 1280-line walker, 17 def types fully populated, value forms, common properties, inline def lists, dataType/reference detection. The parser side is genuinely Phase-2 complete; the new walker functions are independently testable even if §A.11 doesn't include golden fixtures.
- **§B.1 (Manifest types and TOML parser)** — `smol-toml` integration works; `resolveManifest` applies sensible defaults.
- **§B.2 (Project root resolution)** — `findProjectRoot` + `loadProject` correctly walk filesystem; tested in `semantics.test.ts`. The browser/node split (`project.ts` / `project-node.ts`) is the right architectural call.
- **§C.1-§C.3 (Qname structure + per-document symbol table + project-wide aggregator)** — verified runtime: parsing `def entity artikl { attributes: [...] }` produces the expected `er.entity.artikl` + `er.entity.artikl.<attr>` qnames. 13 symbol-table tests covering entity/attribute/table/column/view/column/procedure/resultColumn. Real work.
- **§C.5 (Incremental rebuild)** — `upsertDocument` / `removeDocument` correctly maintain the index; verified via tests.
- **§D.1-§D.3 (Resolver core)** — works in isolation (I verified by hand). Just not wired into the LSP.
- **§J (workspace/symbol)** — runtime-verified working. fuzzysort is properly installed. Progress doc lags reality (false negative this time).
- **Module exports split** — `@modeler/semantics` for browser-safe code, `@modeler/semantics/node-only` for fs/path-dependent code. Correct architectural separation.
- **Diagnostics taxonomy expansion in `docs/design/diagnostics.md`** — accurate documentation of every code's intent (even if some don't fire yet).

The deficit is the **wire-up from these solid building blocks into the LSP**. The semantics package is mostly ready; the LSP server doesn't use what's there.

---

## 6. What must close before "Phase 2 done"

In rough priority order; full task list in `tasks-review-004.md`:

1. **Fix `findNodeAtPosition`.** Use a correct point-in-multi-line-range check. Walk into `def.attributes`, `def.columns`, etc. for nested defs. This single fix unblocks §G/§H/§I.
2. **Wire `Resolver` into LSP navigation.** `onDefinition` must (a) detect if the cursor is on a `Reference` AST node inside a def's properties, (b) call `resolver.resolveReference()`, (c) return the resolved symbol's location. Same for `onHover`.
3. **Add real LSP tests for §G/§H/§I/§K/`getProjectInfo` in `tests/integration/`.** Reuse the `PassThrough` harness already there. Each test 10-15 lines. Without these, the same regressions will recur.
4. **Replace the placeholder `smoke.test.ts`.** Delete it, or write a real `@vscode/test-electron` test that opens a `.ttr` file and asserts language detection + diagnostic delivery. The third placeholder of this kind across three phases.
5. **Wire stock vocabulary loading.** Fix path (`<pkg>/stock/` → `<pkg>/src/stock/`). Rewrite `cnc-roles.ttr` in valid TTR (`schema cnc namespace role` + `def role fact { ... }`). Have the LSP call `loadStockVocabularies` at `onInitialize` and add the resulting symbols to `projectSymbols` under a `stock://` URI.
6. **Wire `findProjectRoot` + `loadProject` into the LSP.** On `initialize`, find the project root, load `modeler.toml` if present, replace `manifest` and `validator`. On `onDidChangeWatchedFiles` of `modeler.toml`, reload.
7. **Wire `validateProject` and cross-reference validation.** Run after each document update so `ttr/duplicate-definition` and `ttr/unresolved-reference` actually reach the client. Adapt `Validator.validateDocument` to iterate `Reference` AST nodes and resolve each.
8. **Fix the implementation of semantic tokens.** Emit one token per `Definition.name`, not per def span. The name's source location is `def.source` minus the `def <kind>` prefix, or — more cleanly — the parser should add a `nameLocation: SourceLocation` field to each def variant in `ast.ts` (it already has `source` for the whole def).
9. **Fix `parseQname` namespace detection.** Don't gate `namespace` on the schemaCode list; the namespace is `rest[0]` whenever `rest.length >= 2`.
10. **Move `@modeler/parser` to `dependencies` in `@modeler/semantics`.** Runtime imports require runtime deps.
11. **Fix `exports.node-only.require` or remove it.**
12. **Progress-doc accuracy pass.** Walk every `✅` in `progress-phase-02.md`, verify against runtime, flip false ones to `[ ]`.
13. **Commit Phase 2 work** (currently 17 modified + 16 new files all uncommitted).

P1/P2 items (kebab-case manifest key normalization, Windows path joins, etc.) are in the task list but don't block Phase 2 closure.

The Phase-2 plan is ~80% built but ~30% wired. The unwired 50% is the entire end-user value proposition. Until §1 and §2 close, Phase 2 hasn't delivered what its name says.
