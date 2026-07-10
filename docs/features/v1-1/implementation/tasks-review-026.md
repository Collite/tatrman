# Tasks — Review 026 (Section B1 follow-ups)

Companion task list to [`review-026.md`](review-026.md). All four blocks are small. The whole list is well under a day's work — most of it is in `packages/parser/src/walker.ts` and `packages/parser/src/__tests__/ast-v1.1.test.ts`.

The build is green right now (52/52 parser tests, 29/29 integration tests). These tasks add coverage and close three latent bugs that the current tests don't exercise.

---

## Block 1 — Fix the broken `bendPoints` walker (Finding 2)

The current code at `packages/parser/src/walker.ts:357–370` looks for property keys `'0'` and `'1'`, which TTR's grammar can never produce. The right shape for `bendPoints: [number, number][]` is a list of two-number lists.

- [ ] **1.1 — Rewrite the edges branch of `walkGraphLayout`.**

  Open `packages/parser/src/walker.ts`. Locate the block starting at the line `} else if (key === 'edges' && valueCtx.object_()) {`. Replace the inner `bendPoints` recovery with:

  ```ts
  } else if (key === 'edges' && valueCtx.object_()) {
    for (const edgeEntry of valueCtx.object_()!.propertyList()?.propertyEntry() ?? []) {
      const edgeKey = edgeEntry.key().getText();
      const edgeVal = edgeEntry.value()?.object_();
      if (!edgeVal) continue;

      const bpEntry = edgeVal.propertyList()?.propertyEntry()
        .find((e) => e.key().getText() === 'bendPoints');
      const bpList  = bpEntry?.value()?.list();
      if (!bpList) {
        edges[edgeKey] = {};
        continue;
      }

      const bendPoints: [number, number][] = [];
      for (const item of bpList.value()) {
        const inner = item.list();
        if (!inner) continue;
        const pair = inner.value();
        if (pair.length !== 2) continue;
        const a = pair[0].literal()?.NUMBER_LITERAL();
        const b = pair[1].literal()?.NUMBER_LITERAL();
        if (a && b) {
          bendPoints.push([Number(a.getText()), Number(b.getText())]);
        }
      }
      edges[edgeKey] = { bendPoints: bendPoints.length > 0 ? bendPoints : undefined };
    }
  }
  ```

  Key differences from what's there today:

  1. Each bendpoint is read as a nested `list()` (not an `object_()` with `'0'`/`'1'` keys).
  2. We require exactly two items per inner list (`pair.length === 2`) — silently skip malformed entries; B4 will diagnose them.

- [ ] **1.2 — Add a test for it in `packages/parser/src/__tests__/ast-v1.1.test.ts`.**

  Append to the existing `describe` block:

  ```ts
  it('graph with edges → bendPoints parsed as [number, number][]', () => {
    const result = parseString(
      'package a\n' +
      'graph v { schema: er, objects: [a.er.entity.X, a.er.entity.Y], layout: {\n' +
      '  nodes: { a_er_entity_X: { x: 0, y: 0 }, a_er_entity_Y: { x: 100, y: 100 } },\n' +
      '  edges: { rel_1: { bendPoints: [[10, 20], [30, 40]] } }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    const edges = result.ast!.graph!.layout!.edges;
    expect(edges['rel_1']).toEqual({ bendPoints: [[10, 20], [30, 40]] });
  });

  it('graph with edges but no bendPoints → entry present, bendPoints undefined', () => {
    const result = parseString(
      'package a\n' +
      'graph v { schema: er, objects: [a.er.entity.X], layout: {\n' +
      '  edges: { rel_2: {} }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    const edges = result.ast!.graph!.layout!.edges;
    expect(edges['rel_2']).toEqual({});
  });
  ```

- [ ] **1.3 — Run the parser suite. Both new cases must pass, everything else stays green.**

  ```bash
  pnpm --filter @modeler/parser test
  ```

  Expect 54/54.

---

## Block 2 — Resolve the `displayMode` type mismatch (Finding 3)

The current AST union (`'just-names' | 'with-types' | 'with-constraints'`) contains values that aren't expressible as TTR identifiers. This is a contract-vs-grammar inconsistency. **Make a decision and document it.**

- [ ] **2.1 — Pick the resolution.**

  Three options, in increasing order of disruption. Read the trade-offs and choose:

  - **Option A (cheapest, recommended for now):** widen the AST field to `displayMode: string`. The validator (B4) narrows when it runs. **Update the contract:** open a PR against `docs/v1-1/design/v1-1-contracts.md` §2 changing `GraphLayout.viewport.displayMode` from the three-member union to `string`. Update §11.2 to keep the *Designer state* union (`DisplayMode = 'just-names' | 'with-types' | 'with-constraints'`) but note that the parser-level type is broader. Add a one-line entry to §12 (changelog).
  - **Option B:** keep the kebab-case union in the AST and translate at walk time. The walker maps camelCase or snake_case source ids to the kebab-case canonical form (`'withTypes' | 'with_types' → 'with-types'`). Adds translation tables; downside is that emitting code on the *.ttrg write path needs the inverse mapping.
  - **Option C:** change the union to camelCase (`'justNames' | 'withTypes' | 'withConstraints'`). Update contracts §2 and §11.2 together. Update v1's `LayoutFile` consumers in `packages/lsp` and `packages/designer` if they reference the kebab-case strings. Largest blast radius.

  Recommendation: **Option A**, because (a) we already accept that semantics-level validation lands in B4, (b) it lets the Designer's stricter union stay as-is, and (c) the contract change is one line in §2.

- [ ] **2.2 — Apply the chosen change.**

  For Option A (recommended):

  In `packages/parser/src/ast.ts`, line ~406, change:

  ```ts
  displayMode: 'just-names' | 'with-types' | 'with-constraints';
  ```

  to:

  ```ts
  displayMode: string;
  ```

  In `packages/parser/src/walker.ts`, line ~401, change:

  ```ts
  if (idCtx) displayMode = idCtx.getText() as typeof displayMode;
  ```

  to:

  ```ts
  if (idCtx) displayMode = idCtx.getText();
  ```

  (Remove the cast. With `displayMode: string` the assignment type-checks naturally.)

- [ ] **2.3 — Update the contract doc.**

  Edit `docs/v1-1/design/v1-1-contracts.md`:

  - **§2**, in the `GraphLayout.viewport` shape: change the union to `displayMode: string` and add a one-line comment: `// Validated against the DisplayMode union in §11.2 by the semantics layer.`
  - **§12 (Changelog)**: prepend a new bullet: `- **v3, <today's date>** — relaxed GraphLayout.viewport.displayMode in §2 from the three-member union to string; the union narrowing now happens in semantics, not parsing. Designer's DisplayMode in §11.2 unchanged.`

- [ ] **2.4 — Update the test.**

  In `packages/parser/src/__tests__/ast-v1.1.test.ts`, the existing "graph with viewport" case (around line 133) already passes — it asserts `displayMode === 'withTypes'`, which is now a valid `string`. No change needed. But add a second case to lock in the new contract:

  ```ts
  it('viewport displayMode is accepted as any identifier (validation deferred to semantics)', () => {
    const result = parseString(
      'package a\n' +
      'graph v { schema: er, objects: [], layout: {\n' +
      '  viewport: { zoom: 1.0, panX: 0, panY: 0, displayMode: nonsense_value }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.layout!.viewport!.displayMode).toBe('nonsense_value');
  });
  ```

- [ ] **2.5 — Run typecheck + parser tests.**

  ```bash
  pnpm --filter @modeler/parser typecheck
  pnpm --filter @modeler/parser test
  pnpm -r typecheck
  ```

  All green.

---

## Block 3 — Fix the `graphBlock` description parsing (Finding 4)

The current shortcut at `walker.ts:308` mangles escapes and triple-quoted strings.

- [ ] **3.1 — Replace the shortcut with `walkStringLiteralForm`.**

  In `packages/parser/src/walker.ts`, locate `walkGraphBlock` (around line 288). Change:

  ```ts
  if (gp.descriptionProperty()) {
    description = gp.descriptionProperty()!.stringLiteralForm()!.getText().slice(1, -1);
  }
  ```

  to:

  ```ts
  if (gp.descriptionProperty()) {
    const parsed = walkStringLiteralForm(gp.descriptionProperty()!.stringLiteralForm()!, file);
    description = parsed.value;
  }
  ```

  `walkStringLiteralForm` already exists in the same file and is what every other walker uses for description fields.

- [ ] **3.2 — Add two test cases for description parsing.**

  In `packages/parser/src/__tests__/ast-v1.1.test.ts`, append:

  ```ts
  it('graph description with escape sequence is unescaped', () => {
    const result = parseString(
      'package a\n' +
      'graph v { schema: er, description: "say \\"hi\\"", objects: [a.er.entity.X] }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.description).toBe('say "hi"');
  });

  it('graph description with triple-quoted string preserves content', () => {
    const result = parseString(
      'package a\n' +
      'graph v {\n' +
      '  schema: er,\n' +
      '  description: """multi\nline""",\n' +
      '  objects: [a.er.entity.X]\n' +
      '}'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.description).toBe('multi\nline');
  });
  ```

- [ ] **3.3 — Run the parser suite.**

  ```bash
  pnpm --filter @modeler/parser test
  ```

  Both new cases pass.

---

## Block 4 — Hygiene (Findings 1, 5)

- [ ] **4.1 — Add a `TODO(C1)` next to the `WrongFileKind` emit site in `walker.ts`.**

  Around `walker.ts:224`, just before the `if (graphCtx && definitions.length > 0) {` block, add:

  ```ts
  // TODO(C1): also emit WrongFileKind when a .ttrg file has no graph block.
  // The walker doesn't know the file extension; that check belongs in the LSP/file-loader
  // layer once .ttrg parsing lands.
  ```

  This prevents future-you from thinking the diagnostic is complete.

- [ ] **4.2 — Flip `STATUS.md`.**

  When you're ready to commit, change line 19 of `STATUS.md` from `[ ] B1 ast extension` to `[x] B1 ast extension`. (Skipping the intermediate `under review` state since this review approves the work modulo the four follow-ups; if you'd rather formalize the cycle, mark it `under review` first, do the follow-ups, then flip to `[x]`.)

- [ ] **4.3 — Commit.**

  Suggested commit message:

  ```
  Section B1: AST extension (review-026 follow-ups)

  - PackageDecl / ImportDecl / GraphBlock / GraphLayout AST types per contracts §2.
  - Walker handlers (walkPackageDecl / walkImportDecl / walkGraphBlock /
    walkGraphLayout / walkViewport) populate them from the parse tree.
  - WrongFileKind diagnostic emitted for the .ttr + graph case
    (the .ttrg + no-graph case is C1's responsibility — TODO left in place).
  - bendPoints layout serialization corrected to [number, number][].
  - displayMode AST type relaxed to string; semantics narrows in B4
    (contracts §2 amended; see changelog v3).
  - graph description parsed via walkStringLiteralForm to honor escapes
    and triple-quoted form.
  ```

---

## Block 5 — Final verification

- [ ] **5.1 — Per-package tests.**

  ```bash
  pnpm --filter @modeler/parser test            # expect ≥56 tests, all green
  pnpm --filter @modeler/vscode-ext test        # still green (unrelated)
  ```

- [ ] **5.2 — Whole workspace.**

  ```bash
  pnpm -r typecheck
  pnpm -r build
  pnpm -r test
  ```

  All four exit 0.

- [ ] **5.3 — Integration tests (sanity).**

  ```bash
  pnpm --filter @modeler/integration-tests test
  ```

  Still 29/29.

---

## Out of scope

- Symbol-table additions (`packageName`, `getByPackage`, `getBySuffix`, `listPackages`) — B2.
- Resolver six-step chain — B3.
- Validator diagnostics (`ttr/unimported-reference`, `ttr/circular-package-dependency`, `ttr/graph-objects-empty`, etc.) — B4.
- `.ttrg` file-extension-aware parsing — C1.
- Designer-side layout round-trip — E4.

If you find yourself editing `packages/semantics/`, `packages/lsp/`, or anything Designer-related, you've drifted out of B1's scope.
