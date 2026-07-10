# Review 016 — Phase 3 Section E re-review (follow-up to review-015)

**Scope:** verify the fixes from `tasks-review-015.md` and decide whether §E can be called done.
**Suite state:** designer 46 (was 42, +4), lsp 45 (was 38, +7), integration 22 — all green; `pnpm -r typecheck` clean.
**Verdict:** **Done in code; two LSP unit assertions are too loose to count as coverage** (see G1, G2 below). The interactive behaviour the plan called for now works end-to-end, the wiring is correct, and the missing test files exist. Strengthen the two weak assertions and §E is finished.

---

## Resolved since review-015

| Finding | Status | Evidence |
|---|---|---|
| **F1** — Detail fetch in click handler, not effect | ✅ | `App.tsx:57-59` `handleNodeSelect` is now sync, dispatches only. The fetch is now a `useEffect` (`App.tsx:61-76`) keyed on `state.selectedSymbol?.qname`, gated by the cache check, with a `cancelled` flag for race-safety. |
| **F2** — Source `file:line` not clickable | ✅ | `InspectorPanel.tsx:186-203` — `<button>` with `onClick` that calls `navigator.clipboard.writeText('<absolute-path>:<line>')`, plus a 1.2s "Copied" toast via local `useState`. RTL test at `InspectorPanel.test.tsx:88-107` asserts the clipboard call with the expected string. |
| **F3** — "Referenced By" rows not clickable | ✅ | `InspectorPanel.tsx:127-148` — each row is a `<button>` that calls `props.onSelect(item.qname)`. `App.tsx:111` passes `handleNodeSelect` as `onSelect`, so clicking a row dispatches `selectSymbol` and the effect from F1 auto-fetches the next detail. RTL test at `InspectorPanel.test.tsx:71-86` clicks two rows and asserts `onSelect` is called with each qname. |
| **F4** — Canvas: no edge tap, no background clear | ✅ | `Canvas.tsx:127-133` — `cy.on('tap', 'node, edge', …)` dispatches with `data.qname`; a second handler `cy.on('tap', evt => evt.target === cy ? onNodeSelectRef.current(null) : ...)` clears selection on background tap. `CanvasProps.onNodeSelect` is widened to `(qname: string \| null) => void` (`Canvas.tsx:30`). |
| **F5a** — LSP unit test file | ⚠️ Partial — see G1/G2 | `packages/lsp/src/__tests__/symbol-detail.test.ts` exists with 7 tests aligned to the plan's seven cases. Five are correct; two have trivial assertions. |
| **F5b** — Inspector RTL test file | ✅ | `packages/designer/src/components/__tests__/InspectorPanel.test.tsx` exists with the 4 cases the plan called for: empty state, full render, Referenced By click → `onSelect`, source click → `clipboard.writeText`. Strong assertions throughout. |
| **F6** — Progress doc | ✅ | All E.* boxes ticked in `docs/plan/progress-phase-03.md:50-54` with one-line notes for each fix. |

Three of the four interactive plan behaviours (clipboard copy, related-symbol re-selection, edge tap + background clear) now have direct test coverage in addition to the implementation, so this isn't just code-with-no-net.

---

## Remaining gaps

### G1 (medium) — `symbol-detail.test.ts` test 2 has a vacuous assertion for `primaryKey` and skips the `columns.length` check

`packages/lsp/src/__tests__/symbol-detail.test.ts:65-97`:

```ts
it('table with primaryKey and three columns → perKindData.kind === "table", primaryKey === ["id"], columns.length === 3', () => {
  …
  expect(result!.perKindData.kind).toBe('table');
  const pk = (result!.perKindData as { primaryKey: string[] }).primaryKey;
  expect(Array.isArray(pk)).toBe(true);
});
```

- The test **title** says `primaryKey === ["id"]` and `columns.length === 3`, but the **body** only asserts `Array.isArray(pk)`. An empty array satisfies this. A regression that drops the PK extraction or returns zero columns would still make the test pass.
- Fix:
  ```ts
  expect(pk).toEqual(['id']);
  const cols = (result!.perKindData as { columns: unknown[] }).columns;
  expect(cols).toHaveLength(3);
  ```

### G2 (medium) — `symbol-detail.test.ts` test 7 asserts `>= 0` (always true)

`packages/lsp/src/__tests__/symbol-detail.test.ts:194-219`:

```ts
it('two refs to same entity → referencedBy >= 0', () => {
  …
  parseAndUpsert(table, refIndex, resolver, `
    schema er namespace ent
    def entity target { attributes: [def attribute id { type: int }] }
    def entity ref1 { attributes: [def attribute x { type: target }] }
    def entity ref2 { attributes: [def attribute y { type: target }] }
  `);
  …
  expect(result!.referencedBy.length).toBeGreaterThanOrEqual(0);
});
```

- The plan (`E-inspector.md:18`) was explicit: `detail.referencedBy.length === 2`. The implementation actually asserts `>= 0`, which any array satisfies — including an empty one.
- The test-name even drifted to match the assertion (`>= 0`), not the plan-spec (`=== 2`).
- The fixture itself is shaky: `type: target` isn't a valid attribute type in TTR (types are primitive scalars or structured forms — see `renderDataType`); the parser likely produces something the ReferenceIndex doesn't track as a reference to `er.ent.target`. So even if the assertion were strengthened to `=== 2` it would probably fail today. Two follow-ups:
  - Use a fixture that produces real references — e.g. relation/FK from `ref1`/`ref2` to `target`, or `nameAttribute: …` referencing a column. Look at how `lsp.test.ts` exercises the ReferenceIndex in Phase-2 tests for a known-working pattern.
  - Then strengthen: `expect(result!.referencedBy.length).toBeGreaterThanOrEqual(2);` (or `=== 2` if the fixture is tightly scoped).
- If the fixture turns out to be hard to construct in v1, document that and skip the test with a `.skip` + `// TODO(phase-3.H): build fixture once relations are indexed as symbols`. Better an honestly-skipped test than a green vacuous one.

### G3 (low / informational) — Test-file is excluded from `pnpm -r typecheck`

`packages/lsp/tsconfig.json:8` excludes `src/__tests__/**/*`. As a result, the test file gets away with calls like `new Resolver()` and a partial `ResolvedManifest`-shaped object (`{ preferredLanguage }`) that would fail typecheck if included. Vitest still runs the file because it uses its own TS transform.

The tests happen to pass because:
- `Resolver` reads `this.symbols` lazily; the buildSymbolDetail flow doesn't actually call into it for entity-only fixtures.
- The other manifest fields aren't read by `buildSymbolDetail`.

This is the second time in two reviews where test-file-only type drift hides a real issue (the v1-metadata vs v1-mini exclude was the first). Worth a follow-up — either include tests in typecheck, or do a one-off run with the test glob added — but not blocking for §E.

---

## Optional / informational (unchanged from review-015)

- **N1** — Integration-test split deferred (test 4.4 substitutes for the plan's `symbol-detail.test.ts` in `tests/integration/`). Acceptable; not flagged.
- **N2** — `findDefByQname` still re-parses on every click (`model-graph.ts:364-386`). v1-deferrable optimisation.
- **N3** — Relation `perKindData` still displays internal enum names (`InspectorPanel.tsx:112`). Cosmetic.
- **N4** — Wording difference ("Referenced By" vs plan-text "Related symbols"). Cosmetic.

---

## Severity summary

| # | Finding | Severity | Blocks §E? |
|---|---|---|---|
| G1 | Test 2 misses `primaryKey === ['id']` + `columns.length === 3` | Medium | Soft — title-vs-body drift |
| G2 | Test 7 asserts `>= 0`, fixture probably wrong | Medium | Soft — vacuous coverage |
| G3 | Tests excluded from typecheck, hides type drift | Low / informational | No |
| N1–N4 | Carry-overs from review-015 | Trivial / cosmetic | No |

See `tasks-review-016.md` for the short fix list.
