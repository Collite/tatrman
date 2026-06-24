import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { DocumentSymbolTable } from '../symbol-table.js';
import { synthesizeMappings } from '../mapping-synthesizer.js';

function setup(ttr: string, uri = 'file:///t/er.ttr') {
  const parsed = parseString(ttr);
  if (parsed.errors.length) throw new Error(`parse errors: ${JSON.stringify(parsed.errors)}`);
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument(uri, parsed.ast!, 'er', 'entity', parsed.ast!.packageDecl?.name ?? '');
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

    const entityEntry = symbols.get('billing.products.binding.er2dbEntity.artikl');
    expect(entityEntry).toBeDefined();
    expect(entityEntry!.kind).toBe('er2dbEntity');
    expect(entityEntry!.source.line).toBeGreaterThan(0);
    expect(entityEntry!.mappingSource).toBe('inline');

    const attrA = symbols.get('billing.products.binding.er2dbAttribute.artikl.id_artiklu');
    expect(attrA).toBeDefined();
    const attrB = symbols.get('billing.products.binding.er2dbAttribute.artikl.název');
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
    const entry = symbols.get('billing.products.binding.er2dbAttribute.foo.id');
    expect(entry).toBeDefined();
    expect(entry!.mappingSource).toBe('inline');
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
    const entry = symbols.get('billing.products.binding.er2dbRelation.r');
    expect(entry).toBeDefined();
    expect(entry!.mappingSource).toBe('inline');
  });
});

describe('mapping-synthesizer — source location', () => {
  it('synthesized entry points at the inline mapping value', () => {
    const symbols = setup(`
  package p
  schema er
  def entity e {
    attributes: [def attribute id { type: int, mapping: IDX }]
  }
  `);
    const entry = symbols.get('p.binding.er2dbAttribute.e.id');
    expect(entry).toBeDefined();
    expect(entry!.source.line).toBe(5);
  });
});

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

    expect(symbols.get('p.binding.er2dbEntity.e')).toBeDefined();
    expect(symbols.get('p.binding.er2dbAttribute.e.id')).toBeDefined();

    const docTable = new DocumentSymbolTable(uri, parsed.ast!, 'er', '');
    const er2 = docTable.all().filter((e) => String(e.kind).startsWith('er2db'));
    expect(er2, `unexpected er2db_* in per-file table: ${er2.map((e) => e.qname).join(', ')}`).toHaveLength(0);
  });
});

describe('mapping-synthesizer — collision with explicit def', () => {
  it('duplicates() reports inline+explicit collision at the same qname', () => {
    const er = parseString(`package billing.products
  schema er
  def entity artikl {
    mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: { id: IDZBOZI } }
  }`);
    const map = parseString(`package billing.products
  schema binding
  def er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }`);
    if (er.errors.length || map.errors.length) throw new Error('fixture parse errors');

    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('file:///er.ttr', er.ast!, 'er', '', 'billing.products');
    synthesizeMappings(symbols, 'file:///er.ttr', er.ast!);
    symbols.upsertDocument('file:///map.ttr', map.ast!, 'binding', '', 'billing.products');

    const dupes = symbols.duplicates();
    const entityDup = dupes.find((d) => d.qname === 'billing.products.binding.er2dbEntity.artikl');
    expect(entityDup, `duplicates(): ${JSON.stringify(dupes)}`).toBeDefined();
    const sources = entityDup!.entries.map((e) => e.mappingSource).sort();
    expect(sources).toEqual(['explicit', 'inline']);
  });
});