# Section F — Integration tests + sample cleanup

Rewrite `samples/2.1/` into a canonical, parseable demonstration. Add end-to-end integration tests through the LSP harness that exercise all four surface forms plus the conflict validator.

**Depends on:** Sections B–E (grammar, parser, synthesizer, validator all working).

**Files:**
- `samples/2.1/er.ttr` — rewrite from the user's sketch.
- `samples/2.1/map.ttr` — trim to entries not covered inline.
- `samples/2.1/db.ttr` — unchanged.
- `samples/2.1/modeler.toml` — add if missing.
- New tests under `tests/integration/src/`:
  - `inline-mappings-parse.test.ts`
  - `inline-mappings-resolve.test.ts`
  - `inline-mappings-conflict.test.ts`

All line numbers below are as of the planning snapshot — confirm by reading the surrounding code before editing.

---

## F.WIP — Remove the `samples/2.1/` exclusion guard (review-059 B2)

`parser.test.ts` and `integration.test.ts` have a temporary `'2.1'` exclusion on `getAllTtrFiles(samplesDir, ['broken', '2.1'])` because the sketch is pre-design and not yet parseable. Remove it once `samples/2.1/er.ttr` has been rewritten and parses cleanly.

- [ ] Remove `'2.1'` from `packages/parser/src/__tests__/parser.test.ts` line ~94.
- [ ] Remove `'2.1'` from `tests/integration/src/integration.test.ts` lines ~126 and ~225.
- [ ] Confirm `pnpm -r test` is still green after removal.

## Tasks

### F.0 — Add `modeler.toml` if missing

- [ ] **Check.** `ls samples/2.1/modeler.toml` — if absent, create a minimal one matching the v1.1 samples' shape:
  ```toml
  [language]
  version = "2.1"

  [schemas]
  declared = ["db", "er", "map"]
  ```
  Reference a working file: `cat samples/v1.1-mini/modeler.toml` to match style.

### F.1 — Rewrite `samples/2.1/er.ttr`

- [ ] **Sanitize the file.** The user's draft has unbalanced braces around lines 8–44 and uses some shapes we agreed to drop (bareword `target {...}` value, missing `target:` wrapper). Replace with the four agreed forms only:

  ```ttr
  package samples.v2_1
  import samples.v2_1.db.*

  schema er namespace entity

  // ---- artikl: entity-level mapping with mixed inline-column forms ----
  def entity artikl { description: """Evidence všech artiklů a jejich vlastností.""",
      displayLabel: { cs: "Artikl", en: "Item" },
      nameAttribute: název_artiklu,
      codeAttribute: kód_artiklu,
      mapping: {
          target: { table: db.dbo.QZBOZI_DF },
          columns: {
              id_artiklu:     IDZBOZI,                              // form (a): bare id
              kód_artiklu:    { target: KOD_ZBOZI },                // form (b): wrapped, bare target
              název_artiklu:  { target: { column: NAZEV_ZBOZI } },  // form (c): fully nested
              id_produktu:    IDSKUPZBOZI,
              id_podproduktu: IDXXPODSKUPZBOZI
          }
      },
      attributes: [
          def attribute id_artiklu     { type: int, isKey: true, description: "Unikátní identifikátor artiklu" },
          def attribute kód_artiklu    { type: text, optional: true, description: "Unikátní kód artiklu" },
          def attribute název_artiklu  { type: text, optional: true, description: "Obchodní název artiklu" },
          def attribute id_produktu    { type: int, optional: true, description: "Vazba na nadřazený produkt" },
          def attribute id_podproduktu { type: int, optional: true, description: "Vazba na nadřazený podprodukt" }
      ]
  }

  // ---- produkt: attribute-level inline mappings (no entity-level mapping block) ----
  def entity produkt { description: """Evidence produktů a jejich vlastností.""",
      displayLabel: { cs: "Produkt", en: "Product" },
      nameAttribute: název_produktu,
      attributes: [
          def attribute id_produktu     { type: int, isKey: true, mapping: IDSKUPZBOZI },
          def attribute kód_produktu    { type: text, optional: true, mapping: KOD_SKUPZBOZI },
          def attribute název_produktu  { type: text, optional: true, mapping: { target: { column: NAZEV_SKUPZBOZI } } }
      ]
  }

  // ---- podprodukt: NO inline mappings — covered by explicit def er2db_* in map.ttr ----
  def entity podprodukt { description: """Evidence podproduktů a jejich vlastností.""",
      nameAttribute: název_podproduktu,
      attributes: [
          def attribute id_podproduktu     { type: int, isKey: true },
          def attribute kód_podproduktu    { type: text, optional: true },
          def attribute název_podproduktu  { type: text, optional: true },
          def attribute id_produktu        { type: int, optional: true }
      ]
  }

  // (other entities — obchodní_kanál, tržní_skupina, subjekt — unchanged from user's draft)

  // ---- relations: one with inline mapping, one without ----
  def relation artikl_produkt { description: "Artikl patří pod nadřazený produkt",
      from: er.entity.artikl, to: er.entity.produkt,
      cardinality: { from: "0..*", to: "0..1" },
      join: [{ from: er.entity.artikl.id_produktu, to: er.entity.produkt.id_produktu }],
      mapping: db.dbo.fk_artikl_produkt
  }

  def relation artikl_podprodukt { description: "Artikl patří pod nadřazený podprodukt",
      from: er.entity.artikl, to: er.entity.podprodukt,
      cardinality: { from: "0..*", to: "1" },
      join: [{ from: er.entity.artikl.id_podproduktu, to: er.entity.podprodukt.id_podproduktu }],
      mapping: { fk: db.dbo.fk_artikl_podprodukt }
  }

  def relation podprodukt_produkt { description: "Podprodukt patří pod nadřazený produkt",
      from: er.entity.podprodukt, to: er.entity.produkt,
      cardinality: { from: "0..*", to: "1" },
      join: [{ from: er.entity.podprodukt.id_produktu, to: er.entity.produkt.id_produktu }]
      // no inline mapping — covered explicitly in map.ttr
  }
  ```

  Keep the other entities (`obchodní_kanál`, `tržní_skupina`, `subjekt`) and their relations from the user's draft as-is — they form the "untouched legacy" demonstration.

### F.2 — Trim `samples/2.1/map.ttr`

- [ ] **Remove entries already covered inline.** After F.1, `map.ttr` should still contain explicit `def er2db_*` for the entities NOT inline-mapped (`podprodukt`, `obchodní_kanál`, `tržní_skupina`, `subjekt`) and for the relations without inline mapping (`podprodukt_produkt`, `obchodní_kanál_tržní_skupina`, `subjekt_obchodní_kanál`).

- [ ] **Keep `db.ttr` untouched.**

### F.3 — Write the parse integration test

- [ ] **Create `tests/integration/src/inline-mappings-parse.test.ts`.** Pattern matches existing `v1.1-samples.test.ts`:

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseFile } from '@modeler/parser';
  import path from 'path';
  import { readdirSync } from 'fs';

  const sampleDir = path.resolve(__dirname, '../../../samples/2.1');

  describe('samples/2.1 — inline mappings parse cleanly', () => {
    for (const name of readdirSync(sampleDir).filter(n => n.endsWith('.ttr'))) {
      it(`${name} has zero parse errors`, async () => {
        const parsed = await parseFile(path.join(sampleDir, name));
        expect(parsed.errors).toEqual([]);
      });
    }
  });
  ```

### F.4 — Write the resolve integration test

- [ ] **Create `tests/integration/src/inline-mappings-resolve.test.ts`.** Asserts that synthesized symbols appear in the project table and references resolve both ways.

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseFile } from '@modeler/parser';
  import { ProjectSymbolTable, Resolver, Validator, resolveManifest, PackageGraphBuilder, synthesizeMappings } from '@modeler/semantics';
  import path from 'path';
  import { readdirSync } from 'fs';

  const root = path.resolve(__dirname, '../../../samples/2.1');

  async function loadProject() {
    const files = readdirSync(root).filter(n => n.endsWith('.ttr'));
    const symbols = new ProjectSymbolTable();
    const pgb = new PackageGraphBuilder();
    for (const f of files) {
      const full = path.join(root, f);
      const parsed = await parseFile(full);
      const uri = `file://${full}`;
      symbols.upsertDocument(uri, parsed.ast!, parsed.ast!.schema?.code ?? '', '');
      synthesizeMappings(symbols, uri, parsed.ast!);
      pgb.add(uri, parsed.ast!);
    }
    return { symbols, packageGraph: pgb.build() };
  }

  describe('samples/2.1 — synthesized symbols', () => {
    it('artikl gets a synthesized er2dbEntity', async () => {
      const { symbols } = await loadProject();
      const entry = symbols.get('samples.v2_1.map.er2dbEntity.artikl');
      expect(entry).toBeDefined();
      expect((entry as any).mappingSource).toBe('inline');
    });

    it('artikl gets 5 synthesized er2dbAttribute entries from the columns map', async () => {
      const { symbols } = await loadProject();
      const expected = ['id_artiklu', 'kód_artiklu', 'název_artiklu', 'id_produktu', 'id_podproduktu'];
      for (const a of expected) {
        const entry = symbols.get(`samples.v2_1.map.er2dbAttribute.artikl.${a}`);
        expect(entry).toBeDefined();
        expect((entry as any).mappingSource).toBe('inline');
      }
    });

    it('produkt.id_produktu gets a synthesized er2dbAttribute from attribute-level mapping', async () => {
      const { symbols } = await loadProject();
      const entry = symbols.get('samples.v2_1.map.er2dbAttribute.produkt.id_produktu');
      expect(entry).toBeDefined();
      expect((entry as any).mappingSource).toBe('inline');
    });

    it('artikl_produkt gets a synthesized er2dbRelation', async () => {
      const { symbols } = await loadProject();
      const entry = symbols.get('samples.v2_1.map.er2dbRelation.artikl_produkt');
      expect(entry).toBeDefined();
      expect((entry as any).mappingSource).toBe('inline');
    });

    it('podprodukt (no inline) only has explicit er2dbEntity', async () => {
      const { symbols } = await loadProject();
      const entries = (symbols as any).getAll('samples.v2_1.map.er2dbEntity.podprodukt');
      expect(entries).toHaveLength(1);
      expect(entries[0].mappingSource).toBe('explicit');
    });
  });
  ```

### F.5 — Write the conflict integration test

- [ ] **Create `tests/integration/src/inline-mappings-conflict.test.ts`.** Walks each fixture under `samples/broken/v2.1/` and asserts the diagnostic count. Reuse the existing `collectFixtureCodes` helper from `integration.test.ts`:

  ```ts
  import { describe, it, expect } from 'vitest';
  import { DiagnosticCode } from '@modeler/parser';
  import path from 'path';
  import { readdirSync } from 'fs';
  // Import or duplicate collectFixtureCodes from integration.test.ts.

  const brokenDir = path.resolve(__dirname, '../../../samples/broken/v2.1');

  describe('samples/broken/v2.1 — duplicate-mapping fixtures', () => {
    for (const fixture of readdirSync(brokenDir, { withFileTypes: true }).filter(d => d.isDirectory())) {
      it(`${fixture.name} produces ttr/duplicate-mapping`, async () => {
        const fixturePath = path.join(brokenDir, fixture.name);
        const codes = await collectFixtureCodes(fixturePath);
        const allCodes = [...codes.values()].flatMap(s => [...s]);
        expect(allCodes).toContain(DiagnosticCode.DuplicateMapping);
      });
    }
  });
  ```

### F.6 — Backward-compat smoke

- [ ] **Confirm `samples/v1.1-mini` and `samples/v1.1-metadata` still parse and validate cleanly.** Add to the existing `v1.1-samples.test.ts` if not already covered (likely already covered — just run it):
  ```bash
  pnpm --filter @modeler/integration-tests test -- v1.1-samples
  ```

### F.7 — Run full suite

- [ ] **Run.**
  ```bash
  pnpm -r build
  pnpm -r test
  pnpm -r typecheck
  pnpm -r lint
  ```
  All green.

---

## Verification

- [ ] `samples/2.1/er.ttr` parses with 0 errors. Run `node packages/parser/dist/cli.js samples/2.1/er.ttr` if a CLI is available, otherwise check via `inline-mappings-parse.test.ts`.
- [ ] All inline-mapping integration tests green.
- [ ] All broken-fixture tests green (each fixture emits `ttr/duplicate-mapping`).
- [ ] `pnpm -r test` reports no regressions in any existing test file.
- [ ] **VS Code smoke**: open `packages/vscode-ext` in VS Code, press F5, open `samples/2.1/er.ttr` from the Extension Development Host. Confirm:
  - Syntax highlighting works (the new `mapping` keyword is highlighted).
  - No spurious red squiggles.
  - Cmd-click on `db.dbo.QZBOZI_DF` (inside the inline `mapping:` block) navigates to `samples/2.1/db.ttr`.

## Notes / gotchas

- **The user's draft is a sketch, not a spec.** Don't try to preserve its exact wording — preserve its *intent* (all four forms demonstrated, mix of inline and explicit), but produce a file that parses.
- **`columns:` map preserves declaration order.** If the integration tests check column order, the synthesizer's `MappingColumnEntry[]` is already ordered (it's an array, not a record). Don't reorder.
- **Don't add new fixtures to `samples/broken/v2.1/`** beyond the four from Section E unless a new failure mode emerges during testing.
- **VS Code extension development host** may need `pnpm --filter @modeler/vscode-ext build` before F5 picks up the regenerated TextMate grammar.
- **`workspace/symbol` test** would be a nice-to-have here but adds LSP harness complexity. Defer to a follow-up if the core resolve tests pass.
