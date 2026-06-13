import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { collectMappingReferences } from '../mapping-references.js';

// A db file providing the target table + columns + a top-level fk, plus an er
// file with inline mappings. Returns the collected mapping references for the er
// doc.
function setup(er: string, pkg = '') {
  const db = `${pkg ? `package ${pkg}\n` : ''}schema db namespace dbo
def table QXXUKAZMUHOD {
  columns: [
    def column IDXXUKAZMU { type: int },
    def column NAZEV_UKAZ { type: text }
  ]
}
def fk fk_hodnoty_ukaz { description: "x" }
`;
  const symbols = new ProjectSymbolTable();
  const dbAst = parseString(db).ast!;
  symbols.upsertDocument('file:///p/db.ttr', dbAst, 'db', 'dbo', pkg);

  const erAst = parseString(er).ast!;
  symbols.upsertDocument('file:///p/er.ttr', erAst, 'er', 'entity', pkg);

  const resolver = new Resolver(symbols);
  return collectMappingReferences(erAst, resolver, 'er', 'entity', pkg);
}

describe('collectMappingReferences — Increment A (attribute column mappings)', () => {
  it('resolves a bare-id mapping (`mapping: IDXXUKAZMU`) to the db column', () => {
    const refs = setup(`schema er namespace entity
def entity hodnoty {
  mapping: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute id_uk { type: int, mapping: IDXXUKAZMU } ]
}
`);
    expect(refs).toHaveLength(1);
    expect(refs[0].ref.path).toBe('IDXXUKAZMU');
    expect(refs[0].targetQname).toBe('db.dbo.QXXUKAZMUHOD.IDXXUKAZMU');
    expect(refs[0].referrerQname).toBe('er.entity.hodnoty');
  });

  it('resolves `{ target: COL }` and `{ target: { column: COL } }` forms', () => {
    const refs = setup(`schema er namespace entity
def entity hodnoty {
  mapping: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [
    def attribute a { type: int, mapping: { target: IDXXUKAZMU } },
    def attribute b { type: text, mapping: { target: { column: NAZEV_UKAZ } } }
  ]
}
`);
    const targets = refs.map((r) => r.targetQname).sort();
    expect(targets).toEqual([
      'db.dbo.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.QXXUKAZMUHOD.NAZEV_UKAZ',
    ]);
  });

  it('skips mappings whose column does not exist in the target table', () => {
    const refs = setup(`schema er namespace entity
def entity hodnoty {
  mapping: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute a { type: int, mapping: NOSUCHCOL } ]
}
`);
    expect(refs).toHaveLength(0);
  });

  it('skips attribute mappings when the entity has no resolvable target table', () => {
    const refs = setup(`schema er namespace entity
def entity hodnoty {
  attributes: [ def attribute a { type: int, mapping: IDXXUKAZMU } ]
}
`);
    expect(refs).toHaveLength(0);
  });

  it('resolves an entity-level `columns:` map (all three value forms)', () => {
    const refs = setup(`schema er namespace entity
def entity hodnoty {
  mapping: {
    target: { table: db.dbo.QXXUKAZMUHOD },
    columns: {
      id_uk: IDXXUKAZMU,
      a:     { target: NAZEV_UKAZ },
      b:     { target: { column: IDXXUKAZMU } }
    }
  },
  attributes: [ def attribute id_uk { type: int }, def attribute a { type: text }, def attribute b { type: int } ]
}
`);
    const targets = refs.map((r) => r.targetQname).sort();
    expect(targets).toEqual([
      'db.dbo.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.QXXUKAZMUHOD.NAZEV_UKAZ',
    ]);
  });

  it('resolves a relation fk mapping (bare-id and wrapped forms) to the db fk', () => {
    const bare = setup(`schema er namespace entity
def relation r { from: er.entity.x, to: er.entity.y, mapping: db.dbo.fk_hodnoty_ukaz }
`);
    expect(bare.map((r) => r.targetQname)).toEqual(['db.dbo.fk_hodnoty_ukaz']);
    expect(bare[0].referrerQname).toBe('er.entity.r');

    const wrapped = setup(`schema er namespace entity
def relation r { from: er.entity.x, to: er.entity.y, mapping: { fk: db.dbo.fk_hodnoty_ukaz } }
`);
    expect(wrapped.map((r) => r.targetQname)).toEqual(['db.dbo.fk_hodnoty_ukaz']);
  });

  it('resolves the target table from an explicit def er2db_entity (Increment B2)', () => {
    // Entity has attribute mappings but NO inline mapping block; the target
    // table is declared in a separate map.ttr via `def er2db_entity`.
    const db = `schema db namespace dbo
def table QXXUKAZMUHOD { columns: [ def column IDXXUKAZMU { type: int } ] }
`;
    const map = `schema map
def er2db_entity hodnoty { entity: er.entity.hodnoty, target: { table: db.dbo.QXXUKAZMUHOD } }
`;
    const er = `schema er namespace entity
def entity hodnoty {
  attributes: [ def attribute id_uk { type: int, mapping: IDXXUKAZMU } ]
}
`;
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('file:///p/db.ttr', parseString(db).ast!, 'db', 'dbo', '');
    symbols.upsertDocument('file:///p/map.ttr', parseString(map).ast!, 'map', '', '');
    const erAst = parseString(er).ast!;
    symbols.upsertDocument('file:///p/er.ttr', erAst, 'er', 'entity', '');

    const refs = collectMappingReferences(erAst, new Resolver(symbols), 'er', 'entity', '');
    expect(refs).toHaveLength(1);
    expect(refs[0].targetQname).toBe('db.dbo.QXXUKAZMUHOD.IDXXUKAZMU');
  });

  it('respects the package prefix on both sides', () => {
    const refs = setup(`package billing
schema er namespace entity
def entity hodnoty {
  mapping: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute a { type: int, mapping: IDXXUKAZMU } ]
}
`, 'billing');
    expect(refs).toHaveLength(1);
    expect(refs[0].targetQname).toBe('billing.db.dbo.QXXUKAZMUHOD.IDXXUKAZMU');
  });
});
