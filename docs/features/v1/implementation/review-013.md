# Review 013 — Phase 3 Section D re-review (follow-up to review-012)

**Scope:** verify that the fixes from `tasks-review-012.md` landed correctly and that §D can now be called done.
**State of the suite:** `pnpm --filter @modeler/designer test` 43 passed; `@modeler/lsp` 38 passed; `@modeler/integration-tests` 22 passed.
**Verdict:** **Still not done.** Six of the seven high-severity findings from review-012 are resolved at the contract/data layer, but the SVG overlay added in `Canvas.tsx` has two runtime defects that prevent any glyph from rendering in the actual demo. D.5 visual review remains unrunnable until those are fixed.

---

## Resolved since review-012

| Item | Status | Evidence |
|---|---|---|
| §1 — Plan/contract amendment for glyph approach | ✅ | `phase-03-contracts.md` v4 changelog selects overlay approach A; `Canvas.tsx:177-251` implements it; centered-label code removed from `adapter.ts` |
| §2 — `ModelGraphNode.label` localization | ✅ | `model-graph.ts:419,423,505` thread `preferredLang` and call `getDisplayLabel`; `server.ts:335` passes `manifest.preferredLanguage`; covered by `model-graph.test.ts:71-81` |
| §3 — `"0..*"` cardinality | ✅ | `model-graph.ts:71-73` adds `'0..*'` → `'many'`; `model-graph-cardinality.test.ts` adds the assertion; integration test 4.5b proves it end-to-end against `samples/v1-metadata/er.ttr` |
| §4 — `glyphFor` signature and tests | ✅ | `glyph-renderer.ts` rewritten to return `<g class="glyph-<name>">…</g>` strings, includes `glyphFor(null) === ''`; `glyph-renderer.test.ts` has element-count + snapshot tests for all five cases |
| §5 — `★` / `#` attribute markers | ✅ | `model-graph.ts:29-30,488-498` adds the two row flags and computes them; `adapter.ts:17-20` prepends the glyphs; `model-graph.test.ts:83-91` covers `nameAttribute` |
| §6 / §8 — duplicate edge style, dead `GlyphResult.offset`, integration test for relation cardinality | ✅ | Canvas style block deduplicated; `GlyphResult` removed; `lsp-phase-03-custom-methods.test.ts:188-221` exercises the real `er.ttr` through `getModelGraph` |
| §7 — D.5 still `[ ]` in `progress-phase-03.md` | ✅ honest | But see N1/N2/N3 below — D.5 cannot currently pass |

The three new amendments (v3, v4, v5) in `phase-03-contracts.md` are concise, justified, and link back to review-012. Good shape.

---

## New / outstanding findings

### N1 — Overlay is never wired up: `cyRef.current` is `null` when the overlay `useEffect` runs (HIGH, blocks D.5)

`packages/designer/src/components/Canvas.tsx`:

- The first `useEffect` (55-135) starts `cytoscapeReadyPromise.then(...)`. `cyRef.current = cy` is assigned inside that `.then` callback, which runs **asynchronously** (after the current React commit).
- The second `useEffect` (177-251) has deps `[]`, so it runs **once at mount**, in the same commit pass — at that point `cyRef.current` is still `null`.
- The early return at line 180 fires (`if (!cy || !overlay) return;`), the effect returns `undefined` (no cleanup), and `cy.on('render zoom pan', scheduleRender)` is **never** registered.
- Because the deps are `[]`, the effect never re-runs when `cy` later becomes available. The overlay `<div>` therefore stays empty forever in the live app — no glyph will ever render.

The existing Canvas tests mask this because `vi.mock('cytoscape', …)` returns a synchronous mock that's resolved by the time the second `useEffect` runs in jsdom. So the suite stays green even though the production path is broken.

**Fix.** Either:
- (a) Move the overlay-handler registration into the `.then` callback of `cytoscapeReadyPromise` (right after `cyRef.current = cy`), and keep a separate cleanup ref to detach on unmount; or
- (b) Lift a `cyReady` boolean to React state, set it from inside the `.then`, and add it to the overlay effect's deps array.

(b) is the smaller diff and keeps the two concerns separate.

### N2 — Screen-coordinate transform is wrong (HIGH, visual)

`Canvas.tsx:196-199`:

```ts
const sx = (sourceNode.position('x') - pan.x) * zoom;
const sy = (sourceNode.position('y') - pan.y) * zoom;
const tx = (targetNode.position('x') - pan.x) * zoom;
const ty = (targetNode.position('y') - pan.y) * zoom;
```

Cytoscape's model→screen transform is **`screen = model * zoom + pan`**, not `(model − pan) * zoom`. The current formula evaluates to `model*zoom − pan*zoom`, which is wrong at any non-zero pan and any non-unit zoom: glyphs will jump in the wrong direction when the user pans and will mis-scale when the user zooms.

**Fix.** Replace with `sourceNode.renderedPosition('x')` / `'y'`, which Cytoscape exposes as the already-transformed screen-space position. Then `pan`/`zoom` are not needed at all in this loop.

### N3 — Endpoint positions use node center, not edge–node intersection (MEDIUM, visual)

Plan (D-er-rendering.md:33 and 39-41) explicitly says "edge endpoint screen positions" — i.e. where the edge meets each node's boundary. The implementation uses `sourceNode.position()` (center of the node) and offsets by a hardcoded 16 px along the edge tangent. Because nodes are 200 px wide rectangles of variable height (`height: 'label'`), 16 px from center lands deep inside the node body for most edges. Both endpoint glyphs will overlap the node's HTML label, not sit near the boundary.

**Fix.** Use Cytoscape's `edge.sourceEndpoint()` / `edge.targetEndpoint()`, which return the actual intersection point on the node boundary in rendered coordinates.

### N4 — Duplicate `<line>` in `glyphFor('one-or-many')` (LOW, cosmetic)

`glyph-renderer.ts:12` emits four `<line>` elements but the 1st and 3rd are identical (`x1="0" y1="-16" x2="0" y2="-22"`). Overdrawn so visually invisible, but the duplicate is now baked into the snapshot.

**Fix.** Delete the duplicate; the geometry should be: 1 perpendicular tick + 3 crow's-foot lines = 4 distinct lines. Re-run `vitest -u` to update the snapshot.

### N5 — `glyphFor('one-or-many')` fan length inconsistent with `glyphFor('many')` (LOW, design)

`many`'s crow's-foot spans y=0 → y=-16 (16 px tall). `one-or-many`'s crow's-foot spans y=-16 → y=-22 (6 px tall). On the same canvas the two will look visually mismatched.

**Fix.** Pick a single fan length. The plan didn't specify exact pixel dimensions, but consistency between siblings is a design baseline.

### N6 — `adapter-er.test.ts` does not exercise the four scenarios the plan required (MEDIUM)

`packages/designer/src/cy/__tests__/adapter-er.test.ts` currently has three tests that pass pre-localized labels straight through the adapter:

- Plan-bullet 1 ("`displayLabel: { cs: 'Artikl', en: 'Item' }` + `preferredLanguage: 'cs'` → labelHtml opens with `Artikl`"): the test sets `label: 'Artikl'` on the input and asserts `data['label']` is `'Artikl'`. Pure pass-through; no localization decision exercised.
- Plan-bullet 2 ("`preferredLanguage: 'de'` (missing) → falls back to `'Item'` (en)"): **no test at all**. Also — the LSP currently falls back to `def.name`, not to `en` (see `getDisplayLabel` in `model-graph.ts:215-229` and `model-graph.test.ts:78`). This contradicts the plan-text. The contract `phase-03-contracts.md` § 4 says `label === name` on fallback, so the contract wins. **But:** then the plan-bullet should be updated to match, and a dedicated test should be added to lock the policy in.
- Plan-bullet 3 ("no `displayLabel` → bare `name`"): the test hard-codes `label: 'foo'` on input; again pure pass-through.
- Plan-bullet 4 (cardinality data on edge): covered. ✅

**Fix.** Either delete this file (the LSP-level test in `model-graph.test.ts` is the right place for these assertions) or rewrite it to construct a `ModelGraph` whose `label` field reflects the value the LSP layer would produce, and assert the adapter's effect on `labelHtml`. Also reconcile the fallback policy: pick "fallback to name" (current code + contract) or "fallback to en" (plan-text) and align both docs and tests.

### N7 — `Canvas-er.test.tsx` does not test what its purpose said it would (MEDIUM)

`packages/designer/src/components/__tests__/Canvas-er.test.tsx` has one assertion: a relative-positioned container exists. The plan (D-er-rendering.md:27) said:

> mock the glyph renderer; assert it's called per edge with the right cardinalities.

The mock for `glyphFor` is declared but never invoked or asserted on. No `cy.on('render', …)` is triggered. Combined with N1, this means **the glyph rendering path has zero direct test coverage** — neither integration nor unit. The integration test 4.5b only proves the LSP returns the right data; nothing tests that the Canvas overlay actually consumes it.

**Fix.** Use the mocked cytoscape's `on` recorder to capture the `'render zoom pan'` callback, invoke it manually, and assert `glyphFor` was called with each edge's `fromCardinality` and `toCardinality` values. This will also catch N1 (the callback will simply never be captured today).

### N8 — Integration test 4.5b's title claims more than its body verifies (LOW)

The `it()` name in `tests/integration/src/lsp-phase-03-custom-methods.test.ts:188` includes "localized entity labels" but no assertion compares `node.label` to anything. Since `samples/v1-metadata/er.ttr` doesn't use `displayLabel`, every entity's `label === name` — which would be a meaningful (if weak) assertion to add and would lock in the no-displayLabel fallback path.

**Fix.** Either retitle the test to drop "localized entity labels", or add a stronger assertion using a temporary fixture that does set `displayLabel`.

### N9 (informational) — D.5 visual review is still `[ ]` in `progress-phase-03.md`

Correct, and consistent with the actual state. But D.5 cannot pass while N1/N2/N3 are unfixed — glyphs will not appear at all (N1), and if N1 were patched in isolation, they would land in wrong positions (N2) and inside the node body (N3).

---

## Severity summary

| # | Finding | Severity | Resolution |
|---|---|---|---|
| N1 | Overlay handler never attaches (async-cy race) | **High** | Required to unblock D.5 |
| N2 | Wrong model→screen transform in overlay | **High** | Required to unblock D.5 |
| N3 | Uses node center + 16 px offset instead of edge endpoints | Medium | Required for the demo to look right |
| N4 | Duplicate `<line>` in `glyphFor('one-or-many')` | Low | Cosmetic |
| N5 | `one-or-many` fan length differs from `many` | Low | Cosmetic |
| N6 | `adapter-er.test.ts` does not exercise localization | Medium | Doc/test alignment |
| N7 | `Canvas-er.test.tsx` does not test the glyph pipeline | Medium | Required for non-trivial coverage |
| N8 | Integration test 4.5b title overclaims | Low | Title or assertion fix |
| N9 | D.5 visual review still unchecked | — | Honest; finish N1–N3 then run it |

See `tasks-review-013.md` for the actionable list.
