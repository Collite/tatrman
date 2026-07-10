# Review 012 — Phase 3, Section D (er schema rendering)

**Scope reviewed:** the developer's claim that **Section 3.D — er-rendering** is finished (per `docs/plan/tasks-phase-03-designer.md` and the per-section plan in `docs/plan/phase-03/D-er-rendering.md`).
**Verdict:** **Not done.** Several plan-mandated artifacts are missing, one wire contract is violated, one core algorithm fails on the actual demo sample, and the chosen rendering architecture diverged from the plan without a documented amendment.
**Tests on disk:** `pnpm -r test` is green (designer 30, lsp 35), but a green suite is consistent with the gaps below because the tests that would have caught them were never written.

---

## 1. Architectural deviation from §D.4 — silent change of glyph-rendering approach

**Plan (D.4 / Library reference, approach A).** A sibling `<div class="cy-overlay">` with `pointer-events: none`, populated per-frame by a `cy.on('render zoom pan', renderOverlay)` handler that:
- Reads each relation edge's `sourceEndpoint` / `targetEndpoint` screen coordinates;
- Renders two glyphs per edge (one at each endpoint, oriented along the edge tangent);
- Throttles via `requestAnimationFrame`.

**Implementation (`packages/designer/src/components/Canvas.tsx:112-125`).** A second `nodeHtmlLabel` entry with selector `'edge[hasCardinalityLabel = true]'` is added. The label is a single centered HTML span containing both glyphs (`<span data-end="source">` mirrored via `transform: scaleX(-1)`, `<span data-end="target">` upright — `index.css:46-62`). Both glyphs render at the edge **midpoint**, not at the endpoints.

This is a fundamentally different visual result:
- Glyph orientation is fixed (mirrored on one side) instead of computed from edge tangents — wrong for any non-horizontal edge.
- Both glyphs are stacked at the edge centroid rather than near the entity boundary they qualify, so the visual "this end of the edge has cardinality X" semantics are lost on diagonal edges.
- No request-animation-frame throttling is needed (because there's no per-frame work) — but the trade-off is the *intended* visual that the plan signed off on.

The deviation is **not documented** anywhere:
- `docs/design/phase-03-contracts.md` § Amendments / Changelog has no entry for it.
- `docs/plan/progress-phase-03.md` D.4 line acknowledges it ("nodeHtmlLabel entry for `edge[hasCardinalityLabel = true]`"), but that is the *progress* doc — the *plan* doc still says SVG overlay, and the tasks-phase-03 "Contract amendment discipline" (lines 63-66) requires a contract amendment for changes of this kind.

Per `reviews.md`: this is exactly the class of undocumented architectural deviation the review process is meant to catch.

**Decide and record one of:**
(a) revert to the planned overlay approach, or
(b) keep the centered-label approach **with a written amendment** explaining the cost/benefit (and an updated D.5 acceptance bar that no longer claims glyphs sit "at endpoints").

---

## 2. Wire-contract violation: `ModelGraphNode.label` is never localized

**Contract §4 (phase-03-contracts.md:166-168):**
> `label: string`. Localized per `manifest.preferredLanguage` when a `displayLabel` exists; otherwise `=== name`.

**Code (`packages/lsp/src/model-graph.ts:481-491`).** For entity nodes inside `buildProjectModelGraph`, `label` is hard-coded to `def.name`. The `getDisplayLabel(def, preferredLang)` helper exists (lines 215-229) and *is* used by `buildSymbolDetailForDef` — but `buildProjectModelGraph` is not even passed a manifest. The `modeler/getModelGraph` handler in `server.ts:322-336` likewise doesn't fetch the manifest.

**Effect.** Any future `er` model that declares `displayLabel: { cs: "...", en: "..." }` will not localize in the Designer. The current `samples/v1-metadata/er.ttr` happens to omit `displayLabel` on all entities, so this defect is invisible at demo time — but it is still a contract breach and breaks the adapter-er test the plan demanded (D.3, see §5 below).

---

## 3. `parseCardinality` does not recognize `"0..*"` — every relation in the demo sample loses its glyphs

**`samples/v1-metadata/er.ttr`** uses `cardinality: { from: "0..*", to: "0..1" }` on every relation (verified via grep — all 35+ relations).

**Contract §8 (phase-03-contracts.md:514-549)** lists only:

| input | output |
|---|---|
| `"1"` | `'one'` |
| `"0..1"` | `'zero-or-one'` |
| `"n"`, `"*"` | `'many'` |
| `"1..n"`, `"1..*"` | `'one-or-many'` |
| anything else | `null` |

`packages/lsp/src/model-graph.ts:65-75` faithfully implements the contract, so `parseCardinality("0..*")` returns `null`. Every relation edge in the demo is therefore built with `fromCardinality: null, toCardinality: null`, the adapter sets `hasCardinalityLabel: false`, and **no glyph renders for any edge** in the v1-metadata demo.

The plan's hand-verified demo path (`tasks-phase-03-designer.md:117-128`) item 3 — "relation edges show Crow's-foot cardinality glyphs matching the `.ttr` source" — cannot pass. So D.5 cannot pass.

**Resolve by either** (i) amending the contract §8 to accept `"0..*"` (most likely → `'many'`, or introduce `'zero-or-many'` — note the latter cascades into the `Cardinality` enum, the glyph renderer, and the inspector's relation rendering), **or** (ii) treating the sample as wrong and migrating it to one of the supported strings. Either way it is a contract decision that needs a `phase-03-contracts.md` amendment before D.5 is run.

---

## 4. §D.1 glyph renderer — signature, output shape, and tests do not match the plan

**Plan (D-er-rendering.md:11-17, 45):**
- A pure function **`glyphFor(card: Cardinality | null): string`**.
- Output for each non-null case is a `<g class="glyph-<name>">…</g>` snippet:
  - `glyphFor('one')` → `<g class="glyph-one">` with exactly one `<line>`.
  - `glyphFor('zero-or-one')` → `<g class="glyph-zero-or-one">` with one `<circle>` and one `<line>`.
  - `glyphFor('many')` → `<g class="glyph-many">` with three `<line>`s.
  - `glyphFor('one-or-many')` → `<g class="glyph-one-or-many">` with one perpendicular `<line>` + three crow's-foot `<line>`s.
  - `glyphFor(null)` → `''`.
- Snapshot test per case via `vi.toMatchSnapshot()`.
- Test file at `packages/designer/src/cy/__tests__/glyph-renderer.test.ts`.

**Implementation (`packages/designer/src/cy/glyph-renderer.ts`).**
- Exports `cardinalityToGlyph(c: Cardinality): GlyphResult` (object, not string; missing the `null` overload).
- No `<g class="...">` wrapper — `barSvg()` returns a bare `<line>`, `crowFootSvg()` returns a bare `<path>` with three `M`/`L` strokes (not three separate `<line>` elements as the plan-spec expects).
- `GlyphResult.offset` is always `0` and never read anywhere — dead field.
- **The test file `glyph-renderer.test.ts` does not exist.** The plan's "tests-first" discipline (lines 24-33 of the tasks doc) and §D's stated risk-mitigation ("a focused `cy/glyph-renderer.ts` module with unit tests for each glyph shape … reviewed before §D moves on to integration") were both skipped.

---

## 5. §D.3 adapter-er — required tests and behavior missing

**Plan (D-er-rendering.md:21-26).** A new `packages/designer/src/cy/__tests__/adapter-er.test.ts` must exist and cover:
1. Entity with `displayLabel: { cs: 'Artikl', en: 'Item' }` + `preferredLanguage: 'cs'` → `labelHtml` opens with `Artikl`.
2. Same entity with `preferredLanguage: 'de'` (missing) → fallback `'Item'` (en).
3. Same entity with no `displayLabel` → fallback to `name`.
4. Relation edge with `fromCardinality: 'one'`, `toCardinality: 'many'` → edge `data` carries both values.

**Status.** The file does not exist. `cy/__tests__/adapter.test.ts` covers db cases plus one generic FK edge but no localization and no cardinality assertion on the adapter output.

Note that even if (1)–(3) were written, they would **fail** today because of finding #2 (the wire `label` is never localized — it's not the adapter's job, but its input from the LSP is wrong).

**Also missing from D.3: attribute markers.** The plan says (D-er-rendering.md:47):
> ensure the er-specific bits (display-label localization, attribute markers `★` / `#` for `nameAttribute` / `codeAttribute`) work

The adapter (`adapter.ts:17-29`) only renders name / type / PK / NN badges. There is no `★` / `#` rendering, and `ModelGraphRow` (`model-graph.ts:22-29`) doesn't even carry an `isNameAttribute` or `isCodeAttribute` flag for the adapter to consume. This is an LSP-side gap (model-graph) plus a Designer-side gap (adapter).

---

## 6. §D.4 Canvas-er test missing

**Plan (D-er-rendering.md:27):**
> `packages/designer/src/components/__tests__/Canvas-er.test.tsx` — analog of C's Canvas test for the er switch + glyph overlay placement (mock the glyph renderer; assert it's called per edge with the right cardinalities).

**Status.** Does not exist. The current `Canvas.test.tsx` has a single smoke assertion that the container div renders — no schema switch, no glyph assertion. Combined with the deviation in §1, there is **no** test verifying that any of the cardinality plumbing actually reaches the Cytoscape layer.

---

## 7. §D.5 visual review is unchecked

`docs/plan/progress-phase-03.md:42-47` correctly marks D.5 as `[ ]`. The "DONE when" in `D-er-rendering.md:63-67` requires it. The dev claim that §D is finished is therefore inconsistent with the developer's own progress doc. Per `feedback-progress-doc-skepticism` in auto-memory and `reviews.md`, this is exactly the kind of "intent vs truth" gap to refuse.

---

## 8. Minor / cleanup

- **`Canvas.tsx:97-108`** — `edge[kind = "relation"]` style is declared twice (one with the `hasCardinalityLabel = true` qualifier) with identical body. Drop the duplicate.
- **`glyph-renderer.ts:3-6`** — `GlyphResult.offset` is always 0 and never read. Delete the wrapper interface and have `cardinalityToGlyph` return the SVG string directly (which is what the plan's `glyphFor` shape calls for anyway — finding #4).
- **`buildEdgeStyle`** (`glyph-renderer.ts:51-65`) — accepts both cardinality params but ignores them; returns a constant. Either parameterize (so an edge with no cardinality can pick a different style) or drop the parameters.
- **`tests/integration/`** — no integration test exercises `modeler/getModelGraph` for er + relation edges, even though CLAUDE.md mandates the integration layer as the canonical place for new LSP feature tests. The breach in #2/#3 would be caught immediately by one. Recommend adding it as part of the §D fix.

---

## Severity summary

| # | Finding | Severity |
|---|---|---|
| 1 | Undocumented architectural deviation from §D.4 overlay design | **High** — requires plan/contract amendment or revert |
| 2 | `ModelGraphNode.label` never localized — wire-contract violation | **High** — silent contract breach |
| 3 | `parseCardinality` rejects `"0..*"`, the only string in the demo sample | **High** — D.5 cannot pass |
| 4 | `glyphFor` signature/output wrong; no `glyph-renderer.test.ts` | **High** — TDD discipline + risk-mitigation skipped |
| 5 | `adapter-er.test.ts` missing; attribute markers not implemented | **High** |
| 6 | `Canvas-er.test.tsx` missing | Medium |
| 7 | D.5 visual review unchecked but §D claimed done | **High** |
| 8 | Cleanup items (duplicate style, dead fields, missing integration test) | Low–Medium |

See `tasks-review-012.md` for the actionable checklist.
