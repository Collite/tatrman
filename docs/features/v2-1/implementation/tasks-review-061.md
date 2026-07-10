# Tasks — review-061 (Section D: semantic synthesizer)

> **STATUS (2026-05-28): all closed.** D1–D6 in commit `5f2324b`; D7 (this re-review's follow-up — uncommitted in working tree) replaces the 6 `as any` casts in `inline-mappings.test.ts` with a typed `assertKind` narrowing helper, taking `pnpm -r lint` fully green. Final gate: parser 122 · semantics 114 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92(+1 skip) · typecheck 8/8 · **lint 8/8**. Section E can proceed. See the "Resolution" section of [`review-061.md`](review-061.md).

Findings in [`review-061.md`](review-061.md). The synthesizer, symbol-table additions, and LSP wiring are correct, the unit tests pass, and the real-world collision scenario works (`duplicates()` fires for `schema map` files with no namespace). Three things must be cleaned up before Section E: a red lint (D1), missing tests for the two invariants that exist *because of* Section E (D2), and a qname-convention reconciliation (D3). D4–D6 are cleanup/notes.

> Work on branch `feat/v2.1-inline-mappings`. Run `pnpm -r build` (not just typecheck) before opening PRs so the LSP esbuild bundles pick up `synthesizeMappings` (see D4).

---

## D1 — Fix the 4 unused-import lint errors  *(HIGH — pnpm -r lint is currently RED)*

`packages/semantics/src/mapping-synthesizer.ts` imports four types it never references directly.

- [ ] **Edit `packages/semantics/src/mapping-synthesizer.ts`.** The top of the file currently reads:
  ```ts
  import type {
    Document,
    EntityDef,
    AttributeDef,
    RelationDef,
    MappingProperty,
    MappingColumnEntry,
    SourceLocation,
  } from '@modeler/parser';
  ```
  Replace with (drop `AttributeDef`, `MappingProperty`, `MappingColumnEntry`, `SourceLocation`):
  ```ts
  import type {
    Document,
    EntityDef,
    RelationDef,
  } from '@modeler/parser';
  ```

- [ ] **Verify the lint is now green:**
  ```bash
  pnpm --filter @modeler/semantics lint
  ```
  Expect: no errors.

- [ ] **Re-run typecheck and the semantics suite to confirm no regression:**
  ```bash
  pnpm --filter @modeler/semantics typecheck
  pnpm --filter @modeler/semantics test
  ```
  Both green; mapping-synthesizer still 5/5.

## D2 — Add a collision test and a real schemaless-absence test  *(MEDIUM — guards the two invariants Section E will rely on)*

The mapping-synthesizer tests currently never exercise a collision (the whole reason `mappingSource: 'inline'/'explicit'` exists), and the "schemaless" test only checks presence, not absence.

- [ ] **Add a collision test** to `packages/semantics/src/__tests__/mapping-synthesizer.test.ts`. Use the no-namespace `map.ttr` form (the real-world convention — see D3 for why namespaced `map.ttr` doesn't collide). Append this `describe` block to the file:

  ```ts
  import { DocumentSymbolTable } from '../symbol-table.js';
  // ... existing imports ...

  describe('mapping-synthesizer — collision with explicit def', () => {
    it('duplicates() reports inline+explicit collision at the same qname', () => {
      const er = parseString(`package billing.products
  schema er
  def entity artikl {
    mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: { id: IDZBOZI } }
  }`);
      const map = parseString(`package billing.products
  schema map
  def er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }`);
      if (er.errors.length || map.errors.length) throw new Error('fixture parse errors');

      const symbols = new ProjectSymbolTable();
      symbols.upsertDocument('file:///er.ttr', er.ast!, 'er', '', 'billing.products');
      synthesizeMappings(symbols, 'file:///er.ttr', er.ast!);
      symbols.upsertDocument('file:///map.ttr', map.ast!, 'map', '', 'billing.products');
      synthesizeMappings(symbols, 'file:///map.ttr', map.ast!);

      const dupes = symbols.duplicates();
      const entityDup = dupes.find((d) => d.qname === 'billing.products.map.er2dbEntity.artikl');
      expect(entityDup, `duplicates(): ${JSON.stringify(dupes)}`).toBeDefined();
      const sources = entityDup!.entries.map((e) => e.mappingSource).sort();
      expect(sources).toEqual(['explicit', 'inline']);
    });
  });
  ```

- [ ] **Replace the existing "schemaless" test** (the last `describe` block in the file) with one that asserts **absence** from the host file's per-file table, not just presence in the project table:

  ```ts
  describe('mapping-synthesizer — schemaless (project-table only, not in per-file table)', () => {
    it('synthesized er2db_* symbols do NOT appear in the host file DocumentSymbolTable', () => {
      const parsed = parseString(`package p
  schema er
  def entity e {
    mapping: { target: { table: db.dbo.T }, columns: { id: IDX } }
  }`);
      if (parsed.errors.length) throw new Error('fixture parse errors');

      const uri = 'file:///er.ttr';
      const symbols = new ProjectSymbolTable();
      symbols.upsertDocument(uri, parsed.ast!, 'er', '', 'p');
      synthesizeMappings(symbols, uri, parsed.ast!);

      // Project table sees the synthesized symbol.
      expect(symbols.get('p.map.er2dbEntity.e')).toBeDefined();
      expect(symbols.get('p.map.er2dbAttribute.e.id')).toBeDefined();

      // But a fresh DocumentSymbolTable on the same er.ttr AST sees ONLY the entity —
      // no er2db_* leakage, per design §C6.
      const docTable = new DocumentSymbolTable(uri, parsed.ast!, 'er', '');
      const er2 = docTable.all().filter((e) => String(e.kind).startsWith('er2db'));
      expect(er2, `unexpected er2db_* in per-file table: ${er2.map((e) => e.qname).join(', ')}`).toHaveLength(0);
    });
  });
  ```

- [ ] **Run the tests:**
  ```bash
  pnpm --filter @modeler/semantics test -- mapping-synthesizer
  ```
  Expect 6 tests green (was 5; +1 collision, "schemaless" rewritten in place).

## D3 — Reconcile the qname-format docs and document the namespace assumption  *(MEDIUM — doc + in-code comment)*

The synthesized qname (`<pkg>.map.er2dbEntity.<name>`, camelCase) matches what `addEntry` produces only when `map.ttr` has **no** namespace declared — which is the production convention (see `samples/v1.1-metadata/billing/map.ttr`). Two docs disagree with reality; one in-code note will save the next reader from rediscovering this.

- [ ] **Fix the design doc.** In `docs/v2-1/design/v2.1-inline-mappings.md` §4.2, the example currently says:
  > Synthesizes `billing.products.map.er2db_entity.artikl` for the entity and `billing.products.map.er2db_attribute.artikl.id_artiklu` for the attribute.

  Change `er2db_entity` → `er2dbEntity` and `er2db_attribute` → `er2dbAttribute` so the doc matches what the code (and `addEntry`) actually produces. Apply the same fix everywhere in §4.2 and §4.4 of `v2.1-inline-mappings.md`.

- [ ] **Fix the same shapes in the contract doc.** In `docs/v2-1/design/grammar-v2-1-changes.md` §4.2, the two synthesized-qname examples (`billing.products.map.er2db_entity.artikl` and `billing.products.map.er2db_attribute.artikl.id_artiklu`) should use `er2dbEntity` / `er2dbAttribute`.

- [ ] **Add an in-code note on the namespace assumption.** In `packages/semantics/src/mapping-synthesizer.ts`, above `synthQname`, add:
  ```ts
  /**
   * Builds the synthesized er2db_* qname using the host file's package and the
   * camelCase AST `kind` token (e.g. `er2dbEntity`) — matching what `addEntry`
   * produces for an explicit `def er2db_entity X` in a `schema map` file WITH NO
   * NAMESPACE. If a project's `map.ttr` declares a namespace
   * (`schema map namespace <X>`), `addEntry` produces `<pkg>.map.<X>.<name>` and
   * synthesized symbols will live at a different qname; `duplicates()` and the
   * Section E validator will not see those as collisions. The production
   * convention (see `samples/v1.1-metadata/billing/map.ttr`) is no namespace on
   * map-schema files, so this is acceptable for v2.1; if it ever changes,
   * synthQname must consult the explicit map.ttr's namespace (or `makeQname`
   * must be made kind-stable for er2db_* defs).
   */
  function synthQname(pkg: string, kindToken: string, name: string): string {
  ```

- [ ] **Add a "known limitation" bullet to the design doc.** In `docs/v2-1/design/v2.1-inline-mappings.md` §9 "Open questions" (or §4.5 if you prefer), add:
  > **Collision detection assumes no namespace on `map`-schema files.** Synthesized qnames use the host file's `<package>.map.<camelCaseKind>.<name>`; explicit `def er2db_*` symbols only land at the same qname when their `map.ttr` declares `schema map` without a namespace (the production convention). Projects that write `schema map namespace <X>` will *not* trigger `ttr/duplicate-mapping`. Defer kind-stable er2db_* qnaming to a future cleanup.

## D4 — Add `pnpm -r build` to Section H's final gate  *(LOW — operational)*

Section D's commit didn't run `pnpm -r build`, so the LSP esbuild bundles (`packages/lsp/dist/server-stdio.js`, `server-browser.js`) didn't include `synthesizeMappings` until I rebuilt by hand. Tests use the per-package tsc output (`dist/server.js`), so the gap doesn't show up in CI.

- [ ] In `docs/v2-1/plan/tasks/section-H-wrap-up.md`, in the final-verification list, ensure `pnpm -r build` is run before `pnpm -r test && pnpm -r typecheck && pnpm -r lint`. If it's not already there, add a checkbox: "Run `pnpm -r build` to refresh the LSP esbuild bundles."

## D5 — Cosmetic cleanup  *(LOW)*

- [ ] **Add trailing newline at EOF** to `packages/semantics/src/mapping-synthesizer.ts` and `packages/semantics/src/index.ts` (both currently show `\ No newline at end of file` in the diff).
- [ ] **Restore the explanatory comment** on the empty top-level-attribute branch in `mapping-synthesizer.ts`. Currently:
  ```ts
  } else if (def.kind === 'attribute') {
  } else if (def.kind === 'relation') {
  ```
  Change to:
  ```ts
  } else if (def.kind === 'attribute') {
    // Top-level `def attribute X { mapping: ... }` (outside any entity) is
    // silently skipped — the synthesized qname would have no entity qualifier
    // and the use case is `def attribute` *inside* `entity.attributes`. Per
    // design (Section D.3 spec): silent skip is acceptable for v2.1.
  } else if (def.kind === 'relation') {
  ```
- [ ] **Fix the commit-hash typo** in `docs/v2-1/plan/tasks/section-D-synthesizer.md` D.0 status line. Currently: `(see commit 05b0748)`. Change to `(see commit 6c0903a)`.

## D6 — Clarify the spec's Verification scope vs Section F  *(LOW — doc tidy)*

The Section D Verification list has four bullets that are inherently LSP-level integration checks (round-trip via LSP harness, `workspace/symbol`, `textDocument/references`, no-leakage). Those are F's job.

- [ ] In `docs/v2-1/plan/tasks/section-D-synthesizer.md`, in the Verification section, move bullets 3–6 (round-trip / no leakage / `workspace/symbol` / `textDocument/references`) to `section-F-integration-tests.md` (or annotate them "deferred to Section F integration tests"), keeping bullets 1–2 (unit tests + typecheck) as D's gate. Otherwise D's "done" is ambiguous.

---

## D7 — Replace `as any` in `inline-mappings.test.ts` *(HIGH — surfaced by re-review; carry-over from Section C, blocks `pnpm -r lint`)*

Workspace-wide `pnpm -r lint` (first run as part of D's gate) surfaces 6 `@typescript-eslint/no-explicit-any` errors in `packages/parser/src/__tests__/inline-mappings.test.ts` at lines 25, 55, 70, 90, 108, 129 — all of the form `const X = result.ast!.definitions[N] as any;`. CLAUDE.md is explicit: `ESLint forbids any outside generated/**`. The tests were copied verbatim from the Section C spec template (which used `as any` for brevity); they need typed assertions.

- [ ] **Edit `packages/parser/src/__tests__/inline-mappings.test.ts`.** Add a type-only import at the top of the file (matching the style of other parser tests):
  ```ts
  import type { EntityDef, AttributeDef, RelationDef } from '../index.js';
  ```

- [ ] **Replace each `as any` cast with the precise AST type, gated by a `kind` narrowing assertion.** For each cast at lines 25/55/70/90/108/129, change `const X = result.ast!.definitions[N] as any;` to (pattern per kind):

  Entity case (line 25):
  ```ts
  const def0 = result.ast!.definitions[0];
  if (def0.kind !== 'entity') throw new Error(`expected entity, got ${def0.kind}`);
  const entity: EntityDef = def0;
  ```

  Attribute cases (lines 55, 70, 129) — same pattern, but check `'attribute'` and type `AttributeDef`:
  ```ts
  const def0 = result.ast!.definitions[0];
  if (def0.kind !== 'attribute') throw new Error(`expected attribute, got ${def0.kind}`);
  const attr: AttributeDef = def0;
  ```

  Relation cases (lines 90, 108) — `definitions[2]` with `'relation'` and `RelationDef`:
  ```ts
  const def2 = result.ast!.definitions[2];
  if (def2.kind !== 'relation') throw new Error(`expected relation, got ${def2.kind}`);
  const rel: RelationDef = def2;
  ```

  (You can keep the existing variable name — `entity` / `attr` / `rel` — by renaming the temporary if you prefer; the point is the narrowing pattern.)

- [ ] **Verify:**
  ```bash
  pnpm --filter @modeler/parser lint
  pnpm --filter @modeler/parser test -- inline-mappings
  pnpm --filter @modeler/parser typecheck
  ```
  All three green; inline-mappings test count unchanged.

- [ ] **Run the full lint gate:**
  ```bash
  pnpm -r lint
  ```
  Expect: all packages green. (If a different package complains, it's a separate carry-over — flag it but don't fix here.)

---

## Done when

- [x] D1: `pnpm --filter @modeler/semantics lint` clean.
- [x] D2: `pnpm --filter @modeler/semantics test -- mapping-synthesizer` green with **6** tests; the new collision test asserts `duplicates()` finds the qname with both `mappingSource` values; the schemaless test asserts absence from the host `DocumentSymbolTable`.
- [x] D3: design doc §4.2 and grammar-changes §4.2 use `er2dbEntity` / `er2dbAttribute` (not underscored); `synthQname` carries the namespace-assumption note; design doc has a "Namespace assumption" paragraph.
- [x] D4: Section H's verification list runs `pnpm -r build`.
- [x] D5: trailing newlines added on `mapping-synthesizer.ts` and `index.ts`; explanatory comment restored; commit-hash typo fixed.
- [x] D6: D's Verification list trims LSP-level bullets (deferred to F).
- [x] **D7: `as any` casts in `inline-mappings.test.ts` replaced with a typed `assertKind` narrowing helper; `pnpm -r lint` green.**
- [x] Re-run the Section D gate after D7: `pnpm -r test` green · `pnpm -r typecheck` green · `pnpm -r lint` green.
- [ ] Commit on `feat/v2.1-inline-mappings`, e.g. `Section D (review-061 follow-up): typed AST asserts in inline-mappings test`.
