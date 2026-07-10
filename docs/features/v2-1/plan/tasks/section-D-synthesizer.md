# Section D — Semantic synthesizer

Convert inline `MappingProperty` AST nodes into synthesized `er2db_entity` / `er2db_attribute` / `er2db_relation` symbol-table entries. Synthesized entries live in the project-level symbol table only, NOT in any per-file `DocumentSymbolTable` for the `map` schema. Source locations point back at the inline `mapping:` value.

**Depends on:** Section C (AST has `mapping` field).

**Spec:** [`docs/v2-1/design/v2.1-inline-mappings.md`](../../design/v2.1-inline-mappings.md) §4; [`docs/v2-1/design/grammar-v2-1-changes.md`](../../design/grammar-v2-1-changes.md) §4.

**Files:**
- `packages/semantics/src/symbol-table.ts` — extend `SymbolEntry` with `source: 'explicit' | 'inline'`.
- `packages/semantics/src/project-symbols.ts` — add `upsertSynthesizedSymbols(uri, entries)` for inline mappings.
- New file: `packages/semantics/src/mapping-synthesizer.ts` — the synthesizer itself.
- `packages/semantics/src/project.ts` (or wherever per-file load is orchestrated) — call the synthesizer after `upsertDocument`.
- New tests: `packages/semantics/src/__tests__/mapping-synthesizer.test.ts`.

All line numbers below are as of the planning snapshot — confirm by reading the surrounding code before editing.

---

## Tasks

### D.0 — Write the failing synthesizer tests first

- [ ] **Create the test file** at `packages/semantics/src/__tests__/mapping-synthesizer.test.ts`. Cover the synthesis rules:

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString } from '@modeler/parser';
  import { ProjectSymbolTable } from '../project-symbols.js';
  import { synthesizeMappings } from '../mapping-synthesizer.js';

  function setup(ttr: string, uri = 'file:///t/er.ttr') {
    const parsed = parseString(ttr);
    if (parsed.errors.length) throw new Error(`parse errors: ${JSON.stringify(parsed.errors)}`);
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument(uri, parsed.ast!, parsed.ast!.schema?.code ?? 'er', '');
    synthesizeMappings(symbols, uri, parsed.ast!);
    return symbols;
  }

  describe('mapping-synthesizer — entity', () => {
    it('synthesizes one er2dbEntity + N er2dbAttribute from entity-level mapping', () => {
      const symbols = setup(`
        package billing.products
        schema er
        def entity artikl {
          mapping: {
            target: { table: db.dbo.QZBOZI_DF },
            columns: { id_artiklu: IDZBOZI, název: NAZEV_ZBOZI }
          },
          attributes: [
            def attribute id_artiklu { type: int, isKey: true },
            def attribute název { type: text }
          ]
        }
      `);

      const entityEntry = symbols.get('billing.products.map.er2db_entity.artikl');
      expect(entityEntry).toBeDefined();
      expect(entityEntry!.kind).toBe('er2dbEntity');
      expect(entityEntry!.source.line).toBeGreaterThan(0);
      expect((entityEntry as any).mappingSource).toBe('inline');

      const attrA = symbols.get('billing.products.map.er2db_attribute.artikl.id_artiklu');
      expect(attrA).toBeDefined();
      const attrB = symbols.get('billing.products.map.er2db_attribute.artikl.název');
      expect(attrB).toBeDefined();
    });
  });

  describe('mapping-synthesizer — attribute', () => {
    it('synthesizes er2dbAttribute from attribute bare-id mapping', () => {
      const symbols = setup(`
        package billing.products
        schema er
        def entity foo {
          attributes: [
            def attribute id { type: int, mapping: IDX, isKey: true }
          ]
        }
      `);
      const entry = symbols.get('billing.products.map.er2db_attribute.foo.id');
      expect(entry).toBeDefined();
      expect((entry as any).mappingSource).toBe('inline');
    });
  });

  describe('mapping-synthesizer — relation', () => {
    it('synthesizes er2dbRelation from relation bare-fk mapping', () => {
      const symbols = setup(`
        package billing.products
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }],
          mapping: db.dbo.fk_a_b
        }
      `);
      const entry = symbols.get('billing.products.map.er2db_relation.r');
      expect(entry).toBeDefined();
      expect((entry as any).mappingSource).toBe('inline');
    });
  });

  describe('mapping-synthesizer — source location', () => {
    it('synthesized entry points at the inline mapping value', () => {
      const symbols = setup(`
  package p
  schema er
  def attribute id { type: int, mapping: IDX }
  `);
      const entry = symbols.get('p.map.er2db_attribute.id');
      expect(entry).toBeDefined();
      // The source location should be inside the line containing "mapping: IDX",
      // not at the def header line.
      expect(entry!.source.line).toBe(4); // 0-indexed? confirm with adjacent existing tests
    });
  });

  describe('mapping-synthesizer — schemaless', () => {
    it('does NOT add synthesized symbols to any map-schema DocumentSymbolTable', () => {
      const symbols = setup(`
        package p
        schema er
        def attribute id { type: int, mapping: IDX }
      `);
      // The project table sees the symbol.
      expect(symbols.get('p.map.er2db_attribute.id')).toBeDefined();
      // But the per-file table for the er.ttr document does NOT.
      // (assert via a helper that returns documentSymbols(uri) — if present.)
    });
  });
  ```

- [ ] **Run; confirm failure.**
  ```bash
  pnpm --filter @modeler/semantics test -- mapping-synthesizer
  ```
  Expect every test to error out because `synthesizeMappings` doesn't exist yet.

**Status: DONE.** Tests written and passing (see commit `6c0903a`).

### D.1 — Extend `SymbolEntry` with mapping source

- [ ] **Add the field** to `SymbolEntry` in `packages/semantics/src/symbol-table.ts` (~line 3):
  ```diff
   export interface SymbolEntry {
     qname: string;
     kind: Definition['kind'];
     name: string;
     source: SourceLocation;
     documentUri: string;
     parent?: string;
     packageName: string;
     schemaCode: string;
  +  /**
  +   * For er2db_* symbols only: distinguishes explicit `def er2db_*` declarations
  +   * from symbols synthesized from inline `mapping:` properties on def entity /
  +   * attribute / relation (v2.1). Used by the duplicate-mapping validator.
  +   * Undefined for all non-er2db_* kinds.
  +   */
  +  mappingSource?: 'explicit' | 'inline';
   }
  ```

- [ ] **Set `mappingSource: 'explicit'`** when an `er2db_*` symbol is added to a `DocumentSymbolTable`. In `symbol-table.ts` `addEntry` (~line 64), after building the `entry` object:
  ```ts
  if (def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' || def.kind === 'er2dbRelation') {
    entry.mappingSource = 'explicit';
  }
  ```
  (Or set it in the object literal directly.)

### D.2 — Add `upsertSynthesizedSymbols` to `ProjectSymbolTable`

The trick: synthesized entries live in `byQname` (the project-wide index) but are NOT added to any `DocumentSymbolTable.byDocument` entry. They're a parallel pool keyed by their host document URI so they can be cleared on `removeDocument`.

- [ ] **Add a parallel store and the upsert method.** In `packages/semantics/src/project-symbols.ts`:
  ```diff
   export class ProjectSymbolTable {
     private byDocument: Map<string, DocumentSymbolTable> = new Map();
     private byQname: Map<string, SymbolEntry[]> = new Map();
  +  private synthesizedByDocument: Map<string, SymbolEntry[]> = new Map();
  ```

  ```ts
  /**
   * Add synthesized er2db_* symbol entries (from v2.1 inline mappings) attributed
   * to the host document. These appear in `byQname` queries but NOT in
   * `byDocument(uri).all()` — by design, inline-synthesized symbols live in the
   * project index only.
   */
  upsertSynthesizedSymbols(uri: string, entries: SymbolEntry[]): void {
    // Clear previous synthesis for this document first.
    const prev = this.synthesizedByDocument.get(uri) ?? [];
    for (const e of prev) {
      const list = this.byQname.get(e.qname);
      if (list) {
        const filtered = list.filter((x) => !(x.documentUri === uri && x.mappingSource === 'inline' && x.qname === e.qname));
        if (filtered.length === 0) this.byQname.delete(e.qname);
        else this.byQname.set(e.qname, filtered);
      }
    }
    // Add new.
    for (const e of entries) {
      const list = this.byQname.get(e.qname) ?? [];
      list.push(e);
      this.byQname.set(e.qname, list);
    }
    this.synthesizedByDocument.set(uri, entries);
  }
  ```

- [ ] **Clear synthesized entries on `removeDocument`** too. ~line 26:
  ```diff
   removeDocument(uri: string): void {
     const table = this.byDocument.get(uri);
     if (!table) return;
     ...
     this.byDocument.delete(uri);
  +  // Also remove any synthesized entries attributed to this URI.
  +  const synth = this.synthesizedByDocument.get(uri) ?? [];
  +  for (const e of synth) {
  +    const list = this.byQname.get(e.qname);
  +    if (list) {
  +      const filtered = list.filter((x) => x.documentUri !== uri);
  +      if (filtered.length === 0) this.byQname.delete(e.qname);
  +      else this.byQname.set(e.qname, filtered);
  +    }
  +  }
  +  this.synthesizedByDocument.delete(uri);
   }
  ```

### D.3 — Write the synthesizer

- [ ] **Create `packages/semantics/src/mapping-synthesizer.ts`:**

  ```ts
  import type {
    Document,
    EntityDef,
    AttributeDef,
    RelationDef,
    MappingProperty,
    MappingPropertyBlock,
    MappingColumnEntry,
    SourceLocation,
    Reference,
    ObjectValue,
  } from '@modeler/parser';
  import { ProjectSymbolTable } from './project-symbols.js';
  import type { SymbolEntry } from './symbol-table.js';

  /**
   * Walks `ast.definitions` for inline `mapping:` properties and synthesizes the
   * equivalent er2db_entity / er2db_attribute / er2db_relation symbol entries.
   * Pushes them into `symbols.upsertSynthesizedSymbols(uri, ...)`.
   *
   * The synthesized qname uses the host file's package — *not* the map schema's
   * package. See design doc §4.2.
   */
  export function synthesizeMappings(
    symbols: ProjectSymbolTable,
    uri: string,
    ast: Document
  ): void {
    const packageName = ast.packageDecl?.name ?? '';
    const entries: SymbolEntry[] = [];

    for (const def of ast.definitions) {
      if (def.kind === 'entity') {
        collectFromEntity(def, packageName, uri, entries);
      } else if (def.kind === 'attribute') {
        // Top-level attribute defs don't have a parent entity context, so they
        // can't carry an inline mapping (the mapping target would have no name
        // qualifier). Skip — the grammar allows it but it's not meaningful.
        // (We could emit a diagnostic here; for v2.1 silent skip is fine —
        // the use case is attributes-inside-entities.)
      } else if (def.kind === 'relation') {
        collectFromRelation(def, packageName, uri, entries);
      }
    }

    symbols.upsertSynthesizedSymbols(uri, entries);
  }

  function collectFromEntity(
    entity: EntityDef,
    packageName: string,
    uri: string,
    entries: SymbolEntry[]
  ): void {
    if (entity.mapping) {
      if (entity.mapping.kind !== 'block') {
        // Entity inline mapping must be a block (bare-id form doesn't make
        // sense — what would the table reference look like?). Silently ignore
        // for now; Section E's validator will surface it as a diagnostic.
        return;
      }
      const block = entity.mapping;

      // er2db_entity for the entity itself.
      entries.push({
        qname: synthQname(packageName, 'er2db_entity', entity.name),
        kind: 'er2dbEntity',
        name: entity.name,
        source: mappingValueLocation(block),
        documentUri: uri,
        packageName,
        schemaCode: 'map',
        mappingSource: 'inline',
      });

      // One er2db_attribute per columns: entry.
      for (const col of block.columns ?? []) {
        entries.push({
          qname: synthQname(packageName, 'er2db_attribute', `${entity.name}.${col.name}`),
          kind: 'er2dbAttribute',
          name: `${entity.name}.${col.name}`,
          source: col.source,
          documentUri: uri,
          packageName,
          schemaCode: 'map',
          mappingSource: 'inline',
          parent: synthQname(packageName, 'er2db_entity', entity.name),
        });
      }
    }

    // Attributes inside this entity may carry their own `mapping:` too.
    for (const attr of entity.attributes ?? []) {
      if (!attr.mapping) continue;
      entries.push({
        qname: synthQname(packageName, 'er2db_attribute', `${entity.name}.${attr.name}`),
        kind: 'er2dbAttribute',
        name: `${entity.name}.${attr.name}`,
        source: attr.mapping.source,
        documentUri: uri,
        packageName,
        schemaCode: 'map',
        mappingSource: 'inline',
        parent: synthQname(packageName, 'er2db_entity', entity.name),
      });
    }
  }

  function collectFromRelation(
    rel: RelationDef,
    packageName: string,
    uri: string,
    entries: SymbolEntry[]
  ): void {
    if (!rel.mapping) return;
    entries.push({
      qname: synthQname(packageName, 'er2db_relation', rel.name),
      kind: 'er2dbRelation',
      name: rel.name,
      source: rel.mapping.source,
      documentUri: uri,
      packageName,
      schemaCode: 'map',
      mappingSource: 'inline',
    });
  }

  function synthQname(pkg: string, kindToken: string, name: string): string {
    const segments: string[] = [];
    if (pkg) segments.push(pkg);
    segments.push('map', kindToken, name);
    return segments.join('.');
  }

  function mappingValueLocation(mapping: MappingProperty): SourceLocation {
    return mapping.source;
  }
  ```

- [ ] **Important — qname kind token.** Note the qname uses `er2db_entity` (with underscore), not `er2dbEntity` (the AST `kind` field). Explicit `def er2db_entity X` declarations produce qnames the same way — verify by reading `symbol-table.ts` `makeQname` and existing fixtures. If `makeQname` produces qnames like `<pkg>.map.er2dbEntity.<name>` (camelCase, matching `def.kind`), then `synthQname` must match that exactly. **Check before committing**:
  ```bash
  grep -rn "er2db_entity\|er2dbEntity" packages/semantics/src/symbol-table.ts samples/v1.1-* 2>&1 | head
  ```
  Most likely the qname uses the camelCase `kind` token; if so, change `synthQname` to:
  ```ts
  function synthQname(pkg: string, kind: 'er2dbEntity' | 'er2dbAttribute' | 'er2dbRelation', name: string): string {
    const segments = pkg ? [pkg, 'map', kind, name] : ['map', kind, name];
    return segments.join('.');
  }
  ```
  Pick whichever matches the explicit-declaration qname shape — they must be identical for the duplicate-mapping validator (Section E) to detect collisions.

### D.4 — Wire the synthesizer into the load pipeline

- [ ] **Locate the project load orchestrator.** Likely `packages/semantics/src/project.ts` or wherever `ProjectSymbolTable.upsertDocument` is called. Grep:
  ```bash
  grep -rn "upsertDocument" packages/semantics/src --include=*.ts
  grep -rn "upsertDocument" packages/lsp/src --include=*.ts
  ```

- [ ] **Call `synthesizeMappings` immediately after `upsertDocument`** for every file load/reload. Sketch:
  ```ts
  symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);
  synthesizeMappings(symbols, uri, ast);
  ```
  Apply at every call site (likely two: the bulk-load and the single-document reload path).

### D.5 — Run tests to green

- [ ] **Re-run.**
  ```bash
  pnpm --filter @modeler/semantics test -- mapping-synthesizer
  pnpm --filter @modeler/semantics typecheck
  ```
  Every test from D.0 should pass.

- [ ] **Run the full semantics suite.**
  ```bash
  pnpm --filter @modeler/semantics test
  ```
  No regressions.

---

## Verification

- [x] `mapping-synthesizer.test.ts` passes.
- [x] `pnpm --filter @modeler/semantics typecheck` clean.
- [ ] **Round-trip check** *(deferred to Section F — requires LSP integration harness).*
- [ ] **No leakage into `map`-schema document tables** *(deferred to Section F integration test — `byDocument(uri)` assertion).*
- [ ] **`workspace/symbol` finds synthesized symbols** *(deferred to Section F — requires LSP `workspace/symbol` request).*
- [ ] **`textDocument/references` on a target column** *(deferred to Section F — requires LSP reference request).*

## Notes / gotchas

- **Don't call `addEntry` directly on `DocumentSymbolTable`** from the synthesizer. Per design doc §C6 / grammar-changes §4.4, synthesized symbols are project-table only.
- **`mappingSource: 'inline'`** discrimination is critical for Section E's validator. Don't drop it.
- **`packageName` must be the host file's package**, not the `map` schema's notional package. If you find yourself writing `'map'` as the package, you've made an error.
- **Re-synthesis on document reload.** `upsertSynthesizedSymbols` must clear the document's previous synthesized set before adding new ones — otherwise a delete-and-retype of a `mapping:` line leaves stale entries.
- **AttributeDef inside `attributes: [...]`** is the *same* AST node type as a top-level `def attribute`. The synthesizer walks the entity's `attributes` array and reads `attr.mapping`; do not try to dispatch by some other discriminator.
- **Resolver chain unaffected.** Inline mappings don't change how `er.entity.artikl` or `db.dbo.QZBOZI_DF` resolve — that remains the standard resolver chain. The synthesizer just adds new symbols; it doesn't intercept resolution.
