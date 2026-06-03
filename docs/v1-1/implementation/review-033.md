# Review 033 — Section C1 (`.ttrg` parsing, validation, edge inclusion)

**Date:** 2026-05-21
**Scope:** the developer reports `1.1.C.1` (`docs/v1-1/plan/tasks/C1-ttrg-parsing.md`) done. Reviewed the working-tree changes against that task list and against [contracts](../design/v1-1-contracts.md) §6/§7. Companion tasks: [`tasks-review-033.md`](tasks-review-033.md).
**Verdict:** **Changes requested.** The new logic is mostly correct and genuinely well-tested, but C1 is *not* done as claimed — a `TODO(C1)` for an explicit tests-first item is still in the source, a new test asserts the un-done behaviour as if intended, the contract amendment is malformed (version collision + no changelog entry), the planned positive fixture was never created, and the one genuinely-new function (`computeGraphEdges`) duplicates the v1 edge logic and silently narrows FK handling.

---

## What was verified green (runtime, not just claims)

Ran the full suite — all pass:

- `@modeler/parser` **80** (+10: `ttrg-parse.test.ts`), `@modeler/semantics` **107** (+1: schema-required), `@modeler/lsp` **52** (+7: `graph-resolve.test.ts`), `@modeler/integration-tests` **49 passed | 1 skipped** (the `graph-layout-stale-node` row is now un-skipped, leaving only `file-ordering` skipped).
- `pnpm -r typecheck` and `pnpm -r lint` clean.

Confirmed correct:

- **C1.2 / C1.4** correctly treated as already-shipped (codes + `publishDiagnostics` wiring present from B4). No spurious parallel validator was created — the developer extended `validateTtrgGraph`, as instructed.
- **C1.3** schema-required rule: emits `RequiredPropertyMissing` Error with a clear message; unit-tested (`diagnostics-v1.1.test.ts`). The schema enum (`db|er|map|query|cnc`, incl. `query`) is verified by `ttrg-parse.test.ts:97` to match contract §7.3.
- **C1.5** edge-inclusion rule is correct: edges appear iff the relation/fk **and** both endpoints are in `objects`. `graph-resolve.test.ts` covers omit-relation→0, omit-endpoint→0, FK-via-list, relation cardinality (`{ from: 1, to: n }` → `one`/`many`), empty objects→0. Good coverage.
- **D1** unquoted dotted-id layout keys parse and populate `graph.layout.nodes` (`ttrg-parse.test.ts:38`); the stale-node fixture was switched to unquoted keys and its B7 row un-skipped.
- **Cardinality** extraction widened to `string | id | number` (matches the developer's note about `{ from: 1 }` parsing as `number`). Verified.
- **`resolveGraphRef` dead-code removal** confirmed (no remaining references anywhere under `packages/`).

---

## Findings

### F1 [High] — `.ttrg` with no graph block: `TODO(C1)` left unresolved; new test codifies the gap as "intended"

`packages/parser/src/walker.ts:223` still contains:

```ts
// TODO(C1): also emit WrongFileKind when a .ttrg file has no graph block.
// The walker doesn't know the file extension; that check belongs in the LSP/file-loader
// layer once .ttrg parsing lands.
```

This is an explicit, named C1 work item left undone while C1 is reported done. Two compounding problems:

1. **C1's tests-first spec** lists: *"`.ttrg` file with no `graph` block → `ttr/wrong-file-kind` (Error)."* The developer's new test asserts the **opposite**:

   ```ts
   // ttrg-parse.test.ts:55
   it('.ttrg with no graph block but no definitions does not emit WrongFileKind', () => {
     const result = parseString(`schema er namespace entity`, 'test.ttrg');
     expect(result.errors).toHaveLength(0);
   });
   ```

   A `.ttrg` whose content is a schema directive (`schema er namespace entity`) is `.ttr` content in the wrong file — exactly what `wrong-file-kind` exists to catch. Codifying the gap as a passing test hides the missing work.

2. **The TODO's stated blocker is false.** It claims "the walker doesn't know the file extension," but `walkDocument(ctx, file, …)` receives `file` (line 210) — that *is* the label/path (`'test.ttrg'` in tests, the real path under `parseFile`). The existing graph-and-defs `WrongFileKind` branch (line 226) fires from this same function. The extension is available right there.

**Resolution required (pick one, don't leave it ambiguous):**

- **Preferred — implement it.** In `walkDocument`, when `file` ends in `.ttrg` and there is no `graphCtx`, emit `WrongFileKind`. Then flip `ttrg-parse.test.ts:55` to assert the Error, add a `.ttrg`-with-defs-no-graph case, remove the `TODO(C1)`, and add a B7 broken fixture (`graph-missing.ttrg`) so the integration guardrail covers it.
- **Or — formally descope.** If a bare-schema `.ttrg` is genuinely acceptable, say so in the C1 task list *and* a contracts §7 note, remove the `TODO(C1)`, and rename the test to document the decision (e.g. `… is allowed: a .ttrg may hold a schema directive without a graph`). A dangling `TODO(C1)` plus an inverted test is the one outcome that's not acceptable.

### F2 [High] — `computeGraphEdges` duplicates the v1 edge logic and silently narrows FK handling

`packages/lsp/src/model-graph.ts` (new `computeGraphEdges`, ~line 468) re-implements edge construction that already exists in `buildProjectModelGraph` (the FK/relation loop at ~line 631). The plan explicitly said to reuse it: *"reuse the v1 relation/fk-edge logic … (don't reinvent it)"* and *"Reuse the v1 endpoint-extraction from `model-graph.ts`."* Three concrete consequences:

- **FK gap.** The new FK branch only handles `def.from.kind === 'list'`:

  ```ts
  const fromRef = def.from?.kind === 'list' && def.from.items[0]?.kind === 'id' ? … : null;
  ```

  But the grammar is `fromProperty : FROM propSep? value` and `value : literal | id | list | …` — a FK may be written with a **bare id** (`from: a.id`, no brackets). The v1 helper `extractFkRef` (line 676) handles **both** `id` and `list`; `computeGraphEdges` would silently drop the edge for the bare-id form. Not currently caught by a test (all FK fixtures use lists), so it's a latent divergence between the two edge builders.
- **Verbatim duplication.** The two ~13-line `edges.push({ id, qname, kind, fromNode, toNode, fromCardinality, toCardinality, sourceUri, sourceLocation })` blocks are copy-pasted between the two functions — they will drift.
- **Redundant guard + inconsistent membership test.** After resolving endpoints, the code re-checks `objectSet.has(fromQname) && objectSet.has(toQname)` — but it passed `objectSet` *as the candidate set* to `resolveRef`, which can only return a member of that set, so the guard is dead. And it uses `graph.objects.includes(defQname)` (linear array scan) where `objectSet.has(defQname)` was already built two lines up.

**Fix:** extract one shared helper, e.g. `buildEdgeForDef(def, schemaCode, namespace, knownQnames): ModelGraphEdge | null`, that uses `extractFkRef` for FKs and the existing relation block, and have **both** `buildProjectModelGraph` and `computeGraphEdges` call it. `computeGraphEdges` then reduces to: for each def whose qname ∈ `objectSet`, call `buildEdgeForDef(def, …, objectSet)` and keep non-null results — the "both endpoints in objects" rule falls out for free (resolution is constrained to `objectSet`), and the redundant guard and `.includes` both disappear.

### F3 [Medium] — Contract amendment (C1.7) is malformed: version collision + missing changelog entry

`docs/v1-1/design/v1-1-contracts.md`:

- Status header was bumped to **`v3, 2026-05-21`**, but §12 Changelog already has a **`v3, 2026-05-19`** entry (the `displayMode` relaxation). Two different "v3"s now exist. (Separately, that prior v3 change never updated the status header — a pre-existing slip that this change inherited.)
- **No changelog entry** was added for the §7.1 quoted→unquoted layout-key change, which C1.7 explicitly required.

**Fix:** set the status header to **`v4, 2026-05-21`** and add a §12 entry:
`- **v4, 2026-05-21** — §7.1 \`layout.nodes\` keys changed from quoted strings to unquoted dotted-ids (grammar \`key : id\` accepts only unquoted ids; \`setLayout\` will emit unquoted — see C2.7).`

The §7.1 example edit itself (removing the quotes) is correct.

### F4 [Medium] — Positive `.ttrg` fixture (C1.6) was never created

C1.6 asked for a hand-authored positive fixture + companion `.ttr`s (suggested `samples/v1.1-mini/graphs/artikl_overview.ttrg`). There is no `samples/v1.1-mini/`; the only `.ttrg` files in the tree are the broken ones under `samples/broken/v1.1/`. The edge functionality *is* unit-tested via inline `parseString` in `graph-resolve.test.ts`, so this is a fixture / manual-smoke / 1.1.G-migration gap, not a functional one — but the checkbox is unmet. Either create the fixture (and have `ttrg-parse.test.ts`'s "all .ttrg files parse" sweep cover it) or descope it explicitly in the task list with a one-line reason.

### F5 [Low] — `graph-layout-stale-node.ttrg`: README disagrees with the test, and the fixture no longer isolates one defect

- `samples/broken/v1.1/README.md` now advertises **three** codes for this fixture (`graph-layout-stale-node`, `graph-object-not-found`, `graph-name-mismatch`), but the B7 guardrail (`integration.test.ts:158`, the source of truth) asserts exactly **two**: `graph-layout-stale-node`, `graph-name-mismatch`. `graph-object-not-found` does **not** fire, because `er.entity.artikl` resolves via `pkg_a/sub/missing-package-declaration.ttr`. The README is wrong.
- The fixture now also trips `graph-name-mismatch` (graph is named `artikl`, file stem is `graph-layout-stale-node`), so it no longer cleanly isolates a single defect the way the other broken fixtures do.

**Fix (minimum):** correct the README to list the two codes the test actually asserts. **Optional (cleaner):** rename the graph to match the stem (`graph graph_layout_stale_node { … }`) so only `graph-layout-stale-node` fires, and update the B7 row to the single code — restoring one-defect-per-fixture.

### F6 [Low / note only] — `computeGraphEdges` signature deviation + D2 resolution path

C1.5 specified `computeGraphEdges(graph, projectSymbols: ProjectSymbolTable)`; the developer shipped `computeGraphEdges(graph, asts: Document[])`. This is **acceptable** — it's consistent with `buildProjectModelGraph`, which is also `asts`-based — but two follow-throughs:

- C2.2 must therefore wire `asts`, not the symbol table, into this call.
- Per **D2**: endpoints resolve through `resolveRef` (fully-qualified tail-drop / namespace-relative against `objectSet`), **not** the six-step `Resolver`. That's fine because `.ttrg` `objects` are fully-qualified (D2's expected finding), but it should be documented: a bare or wildcard-imported object in a `.ttrg` will **not** resolve here. Add a one-line comment on `computeGraphEdges` stating the fully-qualified-only assumption.

---

## Recommendation

C1 is close and the test design is good, but it cannot be signed off while a `TODO(C1)` for a listed test case is still in the tree (F1) and the new edge function diverges from the existing one (F2). Address F1–F4; F5 is a quick doc fix; F6 is a comment + a note for C2. Re-review after fixes — should be a fast pass.
