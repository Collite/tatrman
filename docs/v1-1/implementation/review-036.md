# Review 036 — Section C2 re-review (after `tasks-review-035`)

**Date:** 2026-05-21
**Scope:** re-review of C2 after the developer reported the `tasks-review-035` work done. Verified against runtime, including applying the generated edits. Companion: [`tasks-review-036.md`](tasks-review-036.md).
**Verdict:** **Changes requested — closer, but the write side still corrupts files.** Part A (C2-read) is now genuinely done and good. The object add/remove/create edits work and are content-tested. But two write-side builders produce **malformed or wrong output that would corrupt the user's `.ttrg`** — `buildSetLayoutEdit` (the core of C2.7) emits unbalanced/duplicated `layout` blocks, and `autoImport` emits `import er` (the schema code, not a package). Both are masked by tests that assert substrings instead of applying the edit and re-parsing. The `.ttrl` removal (D4) was marked "decided" but not implemented, so CC2/CC3 are also outstanding.

> Suite is green (edit 32, parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 64 | 1 skipped). As in review-035, green ≠ correct on the write side — see G1/G2/G4. (The designer glyph-renderer snapshots flake under the parallel `pnpm -r test` run but pass in isolation and on a re-run; pre-existing test-infra noise, not a C2 regression.)

---

## Resolved since review-035 (verified)

- **F2/F3 — object add/remove builders fixed.** `buildAddObjectEdit`/`buildRemoveObjectEdit` are now surgical (bracket-depth scan, insert before `]`, delete one entry + comma), the dropped-`[` and `[,s]` bugs are gone, and `graph-edits.test.ts` covers empty/single/multi/trailing-comma add and sole/first/middle/last remove with **content** assertions.
- **F5 — no more `version: null as unknown as number`** (uses `version: null`). No `as unknown as`/`as any` remain in C2 src.
- **F6 — node builder shared.** `buildNodeForDef` extracted in `model-graph.ts:536`, used by both `buildProjectModelGraph` and `getGraph`. Duplication gone.
- **F7 — `missingObjectCount` computed** in `listGraphs` via `qnameToDef`.
- **F8 — cached package graph.** `getPackageGraphFromCache(getPackageGraph())`; the `as unknown as ProjectSymbolTable` mock is gone.
- **F9 — default `schemaCode` unified to `'er'`** across `buildQnameToDef`, `computeGraphEdges`, and the `listGraphs` handler.
- **F12 — `getLayout` returns the real parsed viewport** (no longer hardcoded db/er).
- **F13 — Designer client complete.** All six new methods plus the updated `getLayout`/`setLayout`/`exportLayout` signatures are wrapped; types re-exported from `@modeler/lsp`.
- **D3 — decided (keep both) and de-duplicated.** `getModelGraph` (whole-schema) and `getGraph` (`.ttrg`-scoped) now share `buildNodeForDef`, so "two near-identical methods" is no longer a real cost. Documented in `section-C-plan.md`.
- **Integration tests** now assert edit **content** for add/autoImport/remove/create.

---

## Findings

### G1 [High] — `buildSetLayoutEdit` produces malformed TTR on both paths; untested

Applying the edit it returns (I built `@modeler/edit` and applied the single `TextEdit`):

**No existing `layout` block** — input `graph test { schema: er, objects: [er.entity.a] }`:
```
graph test { schema: er, objects: [er.entity.a]  {
    layout: { nodes: { er.entity.a: { x: 10, y: 20 } } }
  }
```
It replaces the graph's closing `}` with ` { layout… }` — producing a **stray `{`, no separating comma, and unbalanced braces**. This will not parse.

**Replacing an existing `layout` block:**
```
… layout:  { layout: { nodes: { er.entity.a: { x: 99, y: 88 } } } }
```
`findExistingLayoutBlock` returns the brace span but not the `layout:` keyword, and `serializeLayoutBlock` re-emits `layout: {…}`, so the result is a **doubled `layout: { layout: {…} }`**.

Root causes: the no-layout branch does `indexOf('}')` (first brace, not the graph's closing brace) then `lastIndexOf('}', that)` (the same brace) and replaces it; the replace branch double-emits the keyword. `buildSetLayoutEdit` has **no unit test** (the test file doesn't import it), which is why this shipped. The node keys themselves are correctly unquoted (D1) — but inside a corrupt block. `setLayout` therefore writes garbage into the `.ttrg`.

### G2 [High] — `autoImport` emits `import <schemaCode>`, not a real package import

`extractPackageFromQname` takes the first dot-segment of the qname. For `er.entity.foo` that is `er` — the **schema code**, not a package — so the edit inserts `import er`, which is nonsensical TTR (unpackaged objects need no import; `er` is never an importable package). For a packaged qname like `billing.invoicing.er.entity.foo` it would emit `import billing` (wrong — the package is `billing.invoicing`). The package is **not** derivable from the qname string alone (the qname is `[<package>.]<schema>.<nsOrKind>.<defName>`; you can't tell where the package ends without the symbol table). Both the unit test (`graph-edits.test.ts:48`) and the integration test (line 226) **assert `import er`**, locking in the bug.

C2's B2.1 explicitly warned: *"Use the package-resolution rules from semantics; don't hand-roll qname→package parsing."* This needs the caller (the LSP handler, which has the resolver/symbol table) to determine the package — and emit **no** import for unpackaged objects — rather than the pure `@modeler/edit` builder guessing from the string.

### G3 [High] — D4 "remove `.ttrl`" was decided but not implemented; CC2/CC3 outstanding

`tasks-review-035` and `section-C-plan.md` record **"DECIDED: remove the `.ttrl` sidecar … the `getLayout`/`setLayout`/`exportLayout` `.ttrl` branches are removed."** They are **not** removed — `server.ts` still reads/writes `<root>/.modeler/layout.ttrl` (lines ~420–460) and still has `layoutStore`. Consequently CC2 (the architecture-doc / CLAUDE.md invariant reversal) and CC3 (contract bump) are untouched. Either implement the removal (and do CC2/CC3) or change the decision — but right now the decision doc and the code contradict each other.

### G4 [Med] — write-side tests assert substrings, not validity (why G1 slipped through)

The `setLayout` integration test only does `newText.toContain('er.entity.artikl' / 'x: 320' / 'y: 180')` — all true even though the surrounding block is malformed. No test **applies** the edit and **re-parses** the result. B5.4 (round-trip: `setLayout` → apply → `getLayout` returns the same positions, file still parses) was not done. The add/remove/create tests have the same shape — they pass only because those builders happen to be correct.

### G5 [Med] — `createGraph` handler: dead code + non-standard error shape

```ts
const isKnownDirectory = [...documents.keys()].some(uri => uri.startsWith(parentDir + '/'));
if (!isKnownDirectory && parentDir !== '') {
  const hasWorkspaceFolder = false;     // constant
  if (!hasWorkspaceFolder) { return { documentChanges: [], error: '…' }; }
}
```
`hasWorkspaceFolder` is a hardcoded `false` wrapped in `if (!false)` — confused dead code. And returning `{ documentChanges: [], error }` bolts a non-standard `error` field onto a `WorkspaceEdit`-shaped response; the client has to special-case it.

### G6 [Med] — union return types for `setLayout` / `createGraph`

`modeler/setLayout` returns `{ ok: boolean; reason?: string } | WorkspaceEdit` depending on whether `graphUri` was passed, and `createGraph` returns a `WorkspaceEdit`-or-`{…error}`. The client types `setLayout` as `{ ok } | WorkspaceEdit`. A single LSP method returning two unrelated shapes is awkward to consume and isn't what contract §8 specifies. Pick one shape per method (e.g. always return a `WorkspaceEdit` for the graphUri path; the host applies it).

### G7 [Low] — `buildRemoveObjectText` matches by `indexOf(qname)` (substring)

Removing `er.entity.a` would find the first substring occurrence, which could be inside `er.entity.ab` or a later duplicate. Match on token boundaries (the comma/bracket-delimited entry), not a raw substring.

---

## Recommendation

Part A (read) is done — no further work there. Part B (write) is **not** done: `setLayout` and `autoImport` produce invalid output that would corrupt a user's file, and the tests that "cover" them only check substrings. Before C2 can be signed off:

1. **Fix `buildSetLayoutEdit`** (G1) to insert `, layout: { … }` before the graph's true closing brace, and to replace the existing `layout: { … }` span **including** the keyword — and add a unit test that applies the edit and re-parses.
2. **Fix `autoImport`** (G2): resolve the real package in the LSP handler (or pass it in); emit no import for unpackaged objects; correct both tests.
3. **Add the `setLayout`/`getLayout` round-trip integration test** (G4) and make the add/remove/create tests apply-and-reparse, not substring-match.
4. **Resolve G3**: implement the `.ttrl` removal + CC2 + CC3, or formally revise the D4 decision so doc and code agree.
5. Clean up G5/G6/G7.

`tasks-review-036.md` lists these as concrete steps.
