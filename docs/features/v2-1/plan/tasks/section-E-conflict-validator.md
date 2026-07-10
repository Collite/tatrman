# Section E — Conflict validator (`ttr/duplicate-mapping`)

Emit `ttr/duplicate-mapping` (Error) whenever an inline mapping and an explicit `def er2db_*` target the same entity/attribute/relation. The diagnostic fires on **both** source locations so the user sees the conflict from either side.

**Depends on:** Section D (synthesized symbols carry `mappingSource: 'inline'`).

**Spec:** [`docs/v2-1/design/v2.1-inline-mappings.md`](../../design/v2.1-inline-mappings.md) §5; [`docs/v2-1/design/grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md) §5.

**Files:**
- `packages/parser/src/diagnostics.ts` — register the new code.
- `packages/semantics/src/validator.ts` — add the validation pass.
- Fixtures under `samples/broken/v2.1/`.
- Extend `packages/semantics/src/__tests__/validator.test.ts` (or a new sibling `duplicate-mapping.test.ts`).

All line numbers below are as of the planning snapshot — confirm by reading the surrounding code before editing.

---

## Tasks

### E.0 — Write the failing validator tests first

- [ ] **Create `packages/semantics/src/__tests__/duplicate-mapping.test.ts`.** Pattern matches existing validator tests — parse → assemble symbols → run synthesis → run validator → assert on `diags.find(d => d.code === ...)`.

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString, DiagnosticCode } from '@modeler/parser';
  import { ProjectSymbolTable, Resolver, Validator, resolveManifest, PackageGraphBuilder } from '../index.js';
  import { synthesizeMappings } from '../mapping-synthesizer.js';

  async function buildAndValidate(files: Record<string, string>) {
    const symbols = new ProjectSymbolTable();
    const manifestRoot = '/project';
    const manifest = await resolveManifest({ projectRoot: manifestRoot });
    const packageGraphBuilder = new PackageGraphBuilder();

    for (const [path, contents] of Object.entries(files)) {
      const parsed = parseString(contents);
      const uri = `file://${manifestRoot}/${path}`;
      symbols.upsertDocument(uri, parsed.ast!, parsed.ast!.schema?.code ?? 'er', '');
      synthesizeMappings(symbols, uri, parsed.ast!);
      packageGraphBuilder.add(uri, parsed.ast!);
    }
    const packageGraph = packageGraphBuilder.build();
    const resolver = new Resolver(symbols, packageGraph);
    const validator = new Validator(symbols, resolver, manifest);

    const diagnostics: any[] = [];
    for (const [path, contents] of Object.entries(files)) {
      const parsed = parseString(contents);
      const uri = `file://${manifestRoot}/${path}`;
      diagnostics.push(...validator.validateProject().filter(d => d.source.file === uri || true));
    }
    // De-dup since validateProject walks all docs each call.
    return [...new Map(diagnostics.map(d => [`${d.code}:${d.source.line}:${d.source.column}`, d])).values()];
  }

  describe('duplicate-mapping — entity', () => {
    it('inline entity + explicit er2db_entity for same name → error on both', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def entity artikl {
            mapping: { target: { table: db.dbo.QZBOZI_DF } },
            attributes: [ def attribute id { type: int, isKey: true } ]
          }
        `,
        'map.ttr': `
          package p
          schema map
          def er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }
        `,
      });
      const dup = diags.filter(d => d.code === DiagnosticCode.DuplicateMapping);
      expect(dup).toHaveLength(2); // one per source location
    });
  });

  describe('duplicate-mapping — attribute', () => {
    it('inline attribute + explicit er2db_attribute → error on both', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def entity foo {
            attributes: [
              def attribute id { type: int, isKey: true, mapping: IDX }
            ]
          }
        `,
        'map.ttr': `
          package p
          schema map
          def er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }
        `,
      });
      const dup = diags.filter(d => d.code === DiagnosticCode.DuplicateMapping);
      expect(dup).toHaveLength(2);
    });

    it('entity-level columns + explicit er2db_attribute for same attribute → error', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def entity foo {
            mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: { id: IDX } },
            attributes: [ def attribute id { type: int, isKey: true } ]
          }
        `,
        'map.ttr': `
          package p
          schema map
          def er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }
        `,
      });
      const dup = diags.filter(d => d.code === DiagnosticCode.DuplicateMapping);
      expect(dup.length).toBeGreaterThanOrEqual(2); // both attribute mappings collide; entity-level mapping may also fire
    });
  });

  describe('duplicate-mapping — relation', () => {
    it('inline relation + explicit er2db_relation → error on both', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def relation r {
            from: er.entity.a, to: er.entity.b,
            cardinality: { from: "0..*", to: "1" },
            join: [{ from: er.entity.a.x, to: er.entity.b.x }],
            mapping: db.dbo.fk_a_b
          }
        `,
        'map.ttr': `
          package p
          schema map
          def er2db_relation r { relation: er.entity.r, fk: db.dbo.fk_a_b }
        `,
      });
      const dup = diags.filter(d => d.code === DiagnosticCode.DuplicateMapping);
      expect(dup).toHaveLength(2);
    });
  });

  describe('duplicate-mapping — clean cases', () => {
    it('only inline (no explicit) → no diagnostic', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def entity foo {
            mapping: { target: { table: db.dbo.QZBOZI_DF } },
            attributes: [ def attribute id { type: int, isKey: true } ]
          }
        `,
      });
      expect(diags.filter(d => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
    });

    it('only explicit (no inline) → no diagnostic', async () => {
      const diags = await buildAndValidate({
        'er.ttr': `
          package p
          schema er
          def entity foo { attributes: [ def attribute id { type: int, isKey: true } ] }
        `,
        'map.ttr': `
          package p
          schema map
          def er2db_entity foo { entity: er.entity.foo, target: { table: db.dbo.QZBOZI_DF } }
        `,
      });
      expect(diags.filter(d => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
    });
  });
  ```

- [ ] **Run; confirm failure.** Every test fails because `DiagnosticCode.DuplicateMapping` doesn't exist:
  ```bash
  pnpm --filter @modeler/semantics test -- duplicate-mapping
  ```

### E.1 — Register the diagnostic code

- [ ] **Add `DuplicateMapping`** to the `DiagnosticCode` enum in `packages/parser/src/diagnostics.ts` (~line 24, alongside `DuplicateSearchProperty`):
  ```diff
   DuplicateSearchProperty = 'duplicate-search-property',
  +DuplicateMapping = 'ttr/duplicate-mapping',
   }
  ```
  Style: match the existing kebab-case + `ttr/` prefix.

- [ ] **Rebuild parser** so semantics sees the new member:
  ```bash
  pnpm --filter @modeler/parser build
  ```

### E.2 — Add the validation pass

- [ ] **Add `validateDuplicateMappings` to `Validator`** in `packages/semantics/src/validator.ts`. Insert as a new method near `validateProject` (~line 207). It walks the project symbol table and groups by qname:

  ```ts
  validateDuplicateMappings(): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];

    // Group all er2db_* entries by qname. The project table stores per-qname
    // arrays (see ProjectSymbolTable.byQname), so a length > 1 is the conflict.
    for (const qname of this.symbols.allQnames()) {
      const entries = this.symbols.getAll(qname); // returns SymbolEntry[]
      if (!entries || entries.length < 2) continue;

      // Only flag er2db_* kinds.
      const isErTwoDb = entries[0].kind === 'er2dbEntity' || entries[0].kind === 'er2dbAttribute' || entries[0].kind === 'er2dbRelation';
      if (!isErTwoDb) continue;

      // Need at least one explicit and one inline (or two inline) to be a duplicate-mapping.
      const sources = new Set(entries.map(e => e.mappingSource ?? 'explicit'));
      if (!sources.has('inline')) continue;            // pure explicit duplicates handled by ttr/duplicate-definition
      if (entries.length < 2) continue;

      // Emit one diagnostic per entry pointing at that entry's source.
      const otherLocations = entries.map(e => `${e.documentUri}:${e.source.line}`).join(', ');
      for (const e of entries) {
        diagnostics.push({
          code: DiagnosticCode.DuplicateMapping,
          severity: 'error',
          message: `Duplicate mapping for "${qname}" — declared in ${entries.length} places: ${otherLocations}`,
          source: e.source,
        });
      }
    }

    return diagnostics;
  }
  ```

- [ ] **Expose `allQnames()` and `getAll(qname)` on `ProjectSymbolTable`** if not already present. Check `packages/semantics/src/project-symbols.ts` first; `byQname` is private — surface it via:
  ```ts
  allQnames(): string[] {
    return [...this.byQname.keys()];
  }

  getAll(qname: string): SymbolEntry[] {
    return this.byQname.get(qname) ?? [];
  }
  ```
  (The existing `get(qname)` returns only the first entry. The new method returns all — needed for collision detection.)

- [ ] **Call `validateDuplicateMappings` from `validateProject`** so it runs as part of the standard validation cycle. `validator.ts` `validateProject` ~line 207, append:
  ```ts
  diagnostics.push(...this.validateDuplicateMappings());
  ```

### E.3 — Add fixtures

- [ ] **Create the broken-fixtures directory:** `samples/broken/v2.1/`.

- [ ] **`samples/broken/v2.1/duplicate-mapping-entity/`** — two files:
  - `er.ttr`:
    ```ttr
    package broken.v2_1.dup_entity
    schema er
    def entity artikl {
      mapping: { target: { table: db.dbo.QZBOZI_DF } },
      attributes: [ def attribute id { type: int, isKey: true } ]
    }
    ```
  - `map.ttr`:
    ```ttr
    package broken.v2_1.dup_entity
    schema map
    def er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }
    ```
  - Stub `db.ttr` (so `db.dbo.QZBOZI_DF` resolves):
    ```ttr
    package broken.v2_1.dup_entity
    schema db namespace dbo
    def table QZBOZI_DF { primaryKey: ["ID"], columns: [ def column ID { type: int, isKey: true } ] }
    ```

- [ ] **`samples/broken/v2.1/duplicate-mapping-attribute/`** — same shape, with inline `mapping:` on an attribute and explicit `def er2db_attribute` colliding.

- [ ] **`samples/broken/v2.1/duplicate-mapping-relation/`** — inline `mapping:` on a relation and explicit `def er2db_relation` colliding.

- [ ] **`samples/broken/v2.1/duplicate-mapping-mixed/`** — entity-level inline `mapping.columns.X` + explicit `def er2db_attribute X.Y` for the same attribute.

### E.4 — Wire fixtures into the integration guardrail

- [ ] **Add a guardrail row** to `tests/integration/src/integration.test.ts` for each broken fixture, asserting it emits exactly `ttr/duplicate-mapping`. Search for the existing B7 guardrail (`collectFixtureCodes`) and add rows:
  ```ts
  ['broken/v2.1/duplicate-mapping-entity/er.ttr', new Set(['ttr/duplicate-mapping'])],
  ['broken/v2.1/duplicate-mapping-entity/map.ttr', new Set(['ttr/duplicate-mapping'])],
  // ...same for attribute, relation, mixed
  ```
  Style matches the existing fixture table.

### E.5 — Run tests to green

- [ ] **Re-run.**
  ```bash
  pnpm --filter @modeler/semantics test -- duplicate-mapping
  pnpm --filter @modeler/integration-tests test
  ```

---

## Verification

- [ ] `duplicate-mapping.test.ts` passes.
- [ ] Each fixture under `samples/broken/v2.1/` produces exactly the expected `ttr/duplicate-mapping` count (2 diagnostics per pair).
- [ ] **Clean projects produce no diagnostic.** A project with only inline mappings, or only explicit, sees no `DuplicateMapping` codes.
- [ ] **LSP integration**: when a user opens a broken fixture in VS Code, both the `mapping:` value and the `def er2db_*` header light up red. Verify manually with F5 + opening the fixture.
- [ ] `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` green.

## Notes / gotchas

- **Don't conflate `ttr/duplicate-definition` and `ttr/duplicate-mapping`.** `duplicate-definition` already fires when two explicit `def er2db_*` declarations have the same qname. The new code only fires when at least one of the colliding entries is `mappingSource: 'inline'`.
- **`mappingSource` discriminator is the gate.** If you grouped purely by qname and emitted on every collision, you'd re-emit `duplicate-definition` cases. Filter with `if (!sources.has('inline')) continue;`.
- **Source location precision matters.** Each emitted diagnostic must point at *its own* entry's source location, not at the other side's. The Designer's red squiggle will land at exactly that location.
- **Message format.** The user-facing message should help them resolve the conflict. Include the qname and both/all locations. Don't expose the synthesized vs explicit distinction in the message itself (it's an implementation detail) — the locations tell the story.
- **No new severity levels needed.** Error is the right severity per design doc C5; silent override would hide an authoring bug.
- **Two-inline collisions are reserved** per design doc §5.2 — currently theoretically possible only across files where the same entity is referenced, which v2.1 doesn't actually permit (entities are package-local). Keep the validator's grouping general so a future change naturally handles it.
