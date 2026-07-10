# Review 014 — Phase 3 Section D (third pass)

**Scope:** verify the fixes from `tasks-review-013.md` and confirm whether §D can now be called done.
**State of the suite:** `pnpm --filter @modeler/designer test` 42 passed; `@modeler/lsp` 38 passed; `@modeler/integration-tests` 22 passed; `pnpm -r lint` clean; `pnpm -r typecheck` clean.
**Verdict:** **Code is ready for D.5.** All eight runtime/structural findings from review-013 are resolved; only one test-quality gap remains (one of the new Canvas-er tests has no assertion). The single thing standing between §D and "done" is the manual visual review (D.5), which the progress doc still honestly leaves at `[ ]`.

---

## Resolved since review-013

| Finding | Status | Evidence |
|---|---|---|
| **N1** — Overlay handler never attaches (async-cy race) | ✅ | `Canvas.tsx:42,127,246` — `cyReady` state, set after `cyRef.current = cy` inside the `.then()`, deps `[cyReady]`. New test at `Canvas-er.test.tsx:76-82` asserts `handlers['render zoom pan']` becomes defined after `waitFor`. |
| **N2** — Wrong model→screen transform | ✅ | `Canvas.tsx:195-200` — calls `edge.sourceEndpoint()` / `edge.targetEndpoint()` directly; manual `pan`/`zoom` math removed. |
| **N3** — Node-center + 16 px offset instead of edge endpoints | ✅ | Same lines as N2; comment at 194 explicitly notes the values are already in screen coords at the node boundary. The 16 px offset block is gone. |
| **N4** — Duplicate `<line>` in `glyphFor('one-or-many')` | ✅ | `glyph-renderer.ts:16` now emits four distinct lines: one perpendicular tick `(0,-16)→(0,-22)` plus a three-line crow's-foot `(0,0)→(-7,-16)/(0,-16)/(7,-16)`. Snapshot updated. |
| **N5** — Fan-length inconsistency between `many` and `one-or-many` | ✅ | Both glyphs now use a 16 px fan. Comment block at `glyph-renderer.ts:1-3` documents the convention so future edits stay aligned. |
| **N6** — `adapter-er.test.ts` not exercising localization; contract↔plan fallback inconsistency | ✅ | Adapter file deleted (the LSP layer is the right place for these assertions). `D-er-rendering.md:22-23` amended to align with contract §4 — fallback to bare `name`, not to `en`. `model-graph.test.ts:71-81` locks in four explicit cases (`cs`→`Artikl`, `de`→`foo`, `en`→`Item`, `fr`→`foo`). |
| **N7** — Canvas-er.test.tsx didn't exercise the glyph pipeline | ⚠️ partial — see G1 below | `Canvas-er.test.tsx:76-82` now catches the N1 regression (handler registration). But the third test (`glyphFor with each edge cardinalities`, lines 84-93) ships with **no assertion at all** — pure setup. |
| **N8** — Integration test 4.5b title overclaimed | ✅ | `lsp-phase-03-custom-methods.test.ts:221-224` now asserts `result.nodes.find(... 'er.entity.artikl').label === 'artikl'`, locking in the no-displayLabel fallback. |
| Bonus | ✅ | `package.json:13` adds a root `"dev": "pnpm --filter @modeler/designer dev"` alias, so `pnpm run dev` now boots Vite from the repo root. |

Contract changelog (`phase-03-contracts.md:601-604`) and plan deviations (`D-er-rendering.md:22-23`) are now in sync with the implementation. The repository state is internally consistent — that was not true two reviews ago.

---

## Remaining gaps

### G1 (medium) — One of the new Canvas-er tests has no assertion

`packages/designer/src/components/__tests__/Canvas-er.test.tsx:84-93`:

```ts
it('calls glyphFor with each edge cardinalities when the render handler fires', () => {
  act(() => {
    vi.useFakeTimers();
    render(<Canvas graph={null} displayMode="just-names" onNodeSelect={vi.fn()} />);
  });
  act(() => {
    vi.runAllTimers();
  });
  vi.useRealTimers();
});
```

There is no `expect(...)` call. The mocked `glyphForMock` declared at line 10 is never inspected. The test passes regardless of whether `glyphFor` was called, called with the wrong arguments, or never called at all.

The plan (D-er-rendering.md:27) said:

> mock the glyph renderer; assert it's called per edge with the right cardinalities.

That assertion still doesn't exist. Add (inside or after the timer flush):

```ts
const handler = handlers['render zoom pan']?.[0];
if (handler) handler({});       // fire the cytoscape event manually
await new Promise(r => requestAnimationFrame(r));  // let the rAF flush
expect(glyphForMock).toHaveBeenCalledWith('one');
expect(glyphForMock).toHaveBeenCalledWith('many');
```

(The mocked `edges()` iterator at lines 32-40 already yields one edge with `fromCardinality: 'one'`, `toCardinality: 'many'`, so the assertions land cleanly.)

### G2 (low) — No `codeAttribute` test parallel to the `nameAttribute` test

`model-graph.test.ts:83-91` covers `isNameAttribute` only. `isCodeAttribute` is implemented at `model-graph.ts:489-498` but has no direct test. Add a one-liner sibling test:

```ts
it('entity with codeAttribute marks the row', () => {
  const content = `schema er namespace entity def entity foo {
    attributes: [def attribute id { type: int }, def attribute sku { type: text }],
    codeAttribute: sku
  }`;
  const node = buildModelGraph(parseString(content, 'file:///x.ttr').ast!, 'er').nodes[0];
  expect(node.rows.find(r => r.name === 'sku')!.isCodeAttribute).toBe(true);
  expect(node.rows.find(r => r.name === 'id')!.isCodeAttribute).toBe(false);
});
```

### G3 (low / informational) — Canvas-er test's module-scoped `handlers` state

`Canvas-er.test.tsx:8` declares `const handlers: Record<string, …> = {}` at module scope and clears it in `beforeEach` via a `delete`-loop. This works but is fragile under test parallelism (vitest defaults to file-level isolation, so today it's fine, but if anyone enables `pool: 'threads'` for the designer suite it would leak state across files importing the same mock). Consider moving the map into a `beforeAll`-scoped closure or using `vi.hoisted(() => …)`.

### G4 — D.5 visual review still `[ ]` in `progress-phase-03.md`

Correctly so. The dev server now works (`pnpm run dev`), all of N1/N2/N3 are fixed, glyph geometry is consistent — D.5 should now be runnable. Once it passes, tick the box and §D is done.

---

## Severity summary

| # | Finding | Severity |
|---|---|---|
| G1 | `Canvas-er.test.tsx` third test has no assertion | Medium — test-quality only, doesn't affect runtime |
| G2 | No `codeAttribute` test | Low |
| G3 | Module-scoped `handlers` map in Canvas-er test | Low / informational |
| G4 | D.5 visual review still `[ ]` | — (now legitimately runnable) |

See `tasks-review-014.md` for the actionable list.
