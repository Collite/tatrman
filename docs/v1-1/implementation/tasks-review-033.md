# Tasks — review-033 (Section C1)

Findings in [`review-033.md`](review-033.md). Do them in order. Each task says **exactly** which file/line to touch and how to verify. Do not skip the verify step.

> Test baseline before you start (all currently green): parser 80, semantics 107, lsp 52, integration 49 passed | 1 skipped, typecheck + lint clean. Keep it green.

---

## 1. [High] Resolve the `.ttrg`-with-no-graph-block `TODO(C1)` (F1)

The `TODO(C1)` at `packages/parser/src/walker.ts:223` is your work item. Its comment claims "the walker doesn't know the file extension" — that is **false**: `walkDocument(ctx, file, …)` already has `file` (the path/label, e.g. `'test.ttrg'`).

**Decision: implement it** (the preferred path below). Only do task 1-ALT instead if you have an explicit reason a bare-schema `.ttrg` must be legal — and if so, write that reason down.

### 1A — Implement (do this one)

- [ ] **1A.1** In `packages/parser/src/walker.ts`, inside `walkDocument`, add a branch: when `file.endsWith('.ttrg')` **and** `!graphCtx`, push a `WrongFileKind` Error. Suggested message: `"A '.ttrg' file must contain a 'graph { ... }' block."` Use `makeSourceLocation(ctx, file)` for `source`.
- [ ] **1A.2** Delete the `// TODO(C1): also emit WrongFileKind …` comment (lines 223–225). It's done now.
- [ ] **1A.3** In `packages/parser/src/__tests__/ttrg-parse.test.ts`, **replace** the test at line 55 (`'.ttrg with no graph block but no definitions does not emit WrongFileKind'`) with two tests that assert the Error:
  - `.ttrg` containing only `schema er namespace entity` → `result.errors` contains `DiagnosticCode.WrongFileKind` (severity `error`).
  - `.ttrg` containing only `def entity artikl { attributes: [def attribute id { type: int }] }` (defs, no graph) → also `WrongFileKind`.
  - Keep a positive case: a `.ttrg` **with** a graph block and no defs parses with **no** `WrongFileKind` (this already exists at line 82 for `.ttr`; add the `.ttrg` analogue).
- [ ] **1A.4** Add a broken fixture `samples/broken/v1.1/graph-missing.ttrg` whose body is a schema directive or a `def` (no `graph` block). Add its row to the B7 table in `tests/integration/src/integration.test.ts` (the `cases` array, ~line 146): `['graph-missing.ttrg', ['ttr/wrong-file-kind']]`. Add a row to `samples/broken/v1.1/README.md` describing it.
- [ ] **1A.5** Verify: `pnpm --filter @modeler/parser test` and `pnpm --filter @modeler/integration-tests test` both green; `grep -rn "TODO(C1)" packages/` returns nothing.

### 1-ALT — Descope (only if you deliberately chose not to implement)

- [ ] **1-ALT.1** Add a sentence to `docs/v1-1/plan/tasks/C1-ttrg-parsing.md` (under "Status & decisions") stating a bare `.ttrg` without a graph block is intentionally allowed, with the reason.
- [ ] **1-ALT.2** Add the same note to contracts §7 and bump the version per task 3 below.
- [ ] **1-ALT.3** Remove the `TODO(C1)` comment (lines 223–225) and **rename** the `ttrg-parse.test.ts:55` test to document the decision (e.g. `'.ttrg may hold a schema directive without a graph block (allowed)'`).

---

## 2. [High] De-duplicate edge construction; close the bare-id FK gap (F2)

Goal: `computeGraphEdges` and `buildProjectModelGraph` build edges through **one** shared helper, so they can't diverge and FKs are handled identically.

- [ ] **2.1** In `packages/lsp/src/model-graph.ts`, add a private helper:
  ```ts
  function buildEdgeForDef(
    def: Definition,
    schemaCode: string,
    namespace: string,
    knownQnames: Set<string>,
  ): ModelGraphEdge | null
  ```
  - For `def.kind === 'fk'`: resolve `from`/`to` via the existing `extractFkRef(def.from, …)` / `extractFkRef(def.to, …)` (this handles **both** bare `id` and `list` — that is the bug fix). Return the `fk` edge (cardinalities `null`) if both resolve, else `null`.
  - For `def.kind === 'relation'`: resolve `from`/`to` via `resolveRef` exactly as the current `buildProjectModelGraph` relation branch does; attach `extractCardinality(def.cardinality)`. Return the `relation` edge, else `null`.
  - For any other kind: return `null`.
  - Build `id`/`qname` with `buildQname(schemaCode, namespace, [def.name])`, and `sourceUri`/`sourceLocation` from `def.source` — copy the exact shape currently used.
- [ ] **2.2** Rewrite the FK/relation loop inside `buildProjectModelGraph` (~line 631) to call `buildEdgeForDef(def, schemaCode, namespace, knownQnames)` and push the result when non-null. Behaviour must be identical — the v1 model-graph tests must still pass unchanged.
- [ ] **2.3** Rewrite `computeGraphEdges` to:
  ```ts
  const objectSet = new Set(graph.objects ?? []);
  if (objectSet.size === 0) return [];
  const edges: ModelGraphEdge[] = [];
  for (const ast of asts) {
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'er';
    const namespace = ast.schemaDirective?.namespace ?? '';
    for (const def of ast.definitions) {
      const defQname = buildQname(schemaCode, namespace, [def.name]);
      if (!objectSet.has(defQname)) continue;
      const edge = buildEdgeForDef(def, schemaCode, namespace, objectSet);
      if (edge) edges.push(edge);
    }
  }
  return edges;
  ```
  Note: passing `objectSet` as `knownQnames` makes the "both endpoints in `objects`" rule fall out automatically — **remove** the now-redundant `objectSet.has(fromQname) && objectSet.has(toQname)` guard, and use `objectSet.has(defQname)` (not `graph.objects.includes(defQname)`).
- [ ] **2.4** Add a one-line comment on `computeGraphEdges` documenting the fully-qualified-objects assumption (see task 6).
- [ ] **2.5** Verify: `pnpm --filter @modeler/lsp test` green (the 7 `graph-resolve` cases + the v1 `model-graph` cases). Add one new `graph-resolve.test.ts` case proving the **bare-id FK** form now produces an edge:
  ```
  def fk fk_a_b { from: a.id, to: b.a_id }   // note: bare ids, no brackets
  ```
  with `objects: ['db.dbo.a', 'db.dbo.b', 'db.dbo.fk_a_b']` → 1 fk edge.

---

## 3. [Medium] Fix the contract version + add the changelog entry (F3)

`docs/v1-1/design/v1-1-contracts.md`:

- [ ] **3.1** The status header currently reads `**Status:** v3, 2026-05-21` — but §12 already has a `v3, 2026-05-19` entry. Change the header to `**Status:** v4, 2026-05-21`.
- [ ] **3.2** Add a new entry at the **top** of the §12 Changelog list:
  ```
  - **v4, 2026-05-21** — §7.1 `layout.nodes` keys changed from quoted strings to unquoted dotted-ids (grammar `key : id` accepts only unquoted ids; `setLayout` will emit unquoted — see C2.7).
  ```
- [ ] **3.3** (If you took path 1-ALT) also add a §7 note + mention it in this same changelog entry.
- [ ] **3.4** Verify: the header version and the newest changelog entry version match (`v4`), and there is exactly one entry per version number.

---

## 4. [Medium] Create the positive `.ttrg` fixture, or descope it (F4)

C1.6 asked for a hand-authored positive fixture; it does not exist.

- [ ] **4.1** Create `samples/v1.1-mini/graphs/artikl_overview.ttrg` (a valid `graph` block: `schema`, a non-empty `objects` list, and a `layout` with **unquoted** dotted-id node keys) plus the companion `.ttr` file(s) defining those objects (with `package` declarations so the qnames resolve). Header comment: `// hand-authored fixture for 1.1.C; eventual home is the migrated samples in 1.1.G`.
- [ ] **4.2** Confirm `ttrg-parse.test.ts`'s "all existing .ttrg fixture files parse without errors" sweep (line 115) picks it up and stays green.
- [ ] **4.2-ALT** If you choose **not** to create it now, add a one-line note to `docs/v1-1/plan/tasks/C1-ttrg-parsing.md` C1.6 saying the positive fixture is deferred to 1.1.G and that `graph-resolve.test.ts` covers the edge logic in the meantime. (Do one of 4.1+4.2 **or** 4.2-ALT, not neither.)

---

## 5. [Low] Fix the stale-node fixture README (F5)

- [ ] **5.1** In `samples/broken/v1.1/README.md`, change the `graph-layout-stale-node.ttrg` row's advertised codes from the current three to the two the B7 test actually asserts: `ttr/graph-layout-stale-node`, `ttr/graph-name-mismatch`. Remove the `graph-object-not-found` claim (it doesn't fire — `er.entity.artikl` resolves via `pkg_a/sub/missing-package-declaration.ttr`).
- [ ] **5.2** *(Optional, cleaner — only if you want one-defect isolation)* rename the graph in `graph-layout-stale-node.ttrg` to match the file stem (`graph graph_layout_stale_node { … }`) so `graph-name-mismatch` no longer fires, then change the B7 row at `integration.test.ts:158` to the single code `['ttr/graph-layout-stale-node']` and update the README accordingly.
- [ ] **5.3** Verify: `pnpm --filter @modeler/integration-tests test` green.

---

## 6. [Low] Document the `computeGraphEdges` resolution assumption (F6)

- [ ] **6.1** Add a one-line comment above `computeGraphEdges` in `packages/lsp/src/model-graph.ts`: endpoints resolve against the graph's `objects` set via `resolveRef` (fully-qualified / namespace-relative), **not** the six-step `Resolver` — so a bare or wildcard-imported object in a `.ttrg` will not resolve here; `.ttrg` `objects` are expected fully-qualified (contract §7.1, decision D2).
- [ ] **6.2** No code change for the signature — `(graph, asts)` is accepted (consistent with `buildProjectModelGraph`). Just remember in C2.2 to pass `asts`, not the `ProjectSymbolTable`.

---

## Final verification (run after all tasks)

```bash
pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test
grep -rn "TODO(C1)" packages/   # must return nothing (if you took path 1A)
```

Expected: all suites green; integration shows the new `graph-missing.ttrg` row passing; no `TODO(C1)` left in source.
