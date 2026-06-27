import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { collectBindingReferences } from '../mapping-references.js';

// A db file providing the target table + columns + a top-level fk, plus an er
// file with inline mappings. Returns the collected mapping references for the er
// doc.
function setup(er: string, pkg = '') {
  const db = `${pkg ? `package ${pkg}\n` : ''}model db schema dbo
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
  symbols.upsertDocument('file:///p/db.ttrm', dbAst, 'db', 'dbo', pkg);

  const erAst = parseString(er).ast!;
  symbols.upsertDocument('file:///p/er.ttrm', erAst, 'er', 'entity', pkg);

  const resolver = new Resolver(symbols);
  return collectBindingReferences(erAst, resolver, 'er', 'entity', pkg);
}

describe('collectBindingReferences — Increment A (attribute column mappings)', () => {
  it('resolves a bare-id mapping (`binding: IDXXUKAZMU`) to the db column', () => {
    const refs = setup(`model er schema entity
def entity hodnoty {
  binding: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute id_uk { type: int, binding: IDXXUKAZMU } ]
}
`);
    expect(refs).toHaveLength(1);
    expect(refs[0].ref.path).toBe('IDXXUKAZMU');
    expect(refs[0].targetQname).toBe('db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU');
    expect(refs[0].referrerQname).toBe('er.entity.hodnoty');
  });

  it('resolves `{ target: COL }` and `{ target: { column: COL } }` forms', () => {
    const refs = setup(`model er schema entity
def entity hodnoty {
  binding: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [
    def attribute a { type: int, binding: { target: IDXXUKAZMU } },
    def attribute b { type: text, binding: { target: { column: NAZEV_UKAZ } } }
  ]
}
`);
    const targets = refs.map((r) => r.targetQname).sort();
    expect(targets).toEqual([
      'db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.table.QXXUKAZMUHOD.NAZEV_UKAZ',
    ]);
  });

  it('skips mappings whose column does not exist in the target table', () => {
    const refs = setup(`model er schema entity
def entity hodnoty {
  binding: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute a { type: int, binding: NOSUCHCOL } ]
}
`);
    expect(refs).toHaveLength(0);
  });

  it('skips attribute mappings when the entity has no resolvable target table', () => {
    const refs = setup(`model er schema entity
def entity hodnoty {
  attributes: [ def attribute a { type: int, binding: IDXXUKAZMU } ]
}
`);
    expect(refs).toHaveLength(0);
  });

  it('resolves an entity-level `columns:` map (all three value forms)', () => {
    const refs = setup(`model er schema entity
def entity hodnoty {
  binding: {
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
      'db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU',
      'db.dbo.table.QXXUKAZMUHOD.NAZEV_UKAZ',
    ]);
  });

  it('resolves a relation fk mapping (bare-id and wrapped forms) to the db fk', () => {
    const bare = setup(`model er schema entity
def relation r { from: er.entity.x, to: er.entity.y, binding: db.dbo.fk_hodnoty_ukaz }
`);
    expect(bare.map((r) => r.targetQname)).toEqual(['db.dbo.fk.fk_hodnoty_ukaz']);
    expect(bare[0].referrerQname).toBe('er.relation.r');

    const wrapped = setup(`model er schema entity
def relation r { from: er.entity.x, to: er.entity.y, binding: { fk: db.dbo.fk_hodnoty_ukaz } }
`);
    expect(wrapped.map((r) => r.targetQname)).toEqual(['db.dbo.fk.fk_hodnoty_ukaz']);
  });

  it('resolves the target table from an explicit def er2db_entity (Increment B2)', () => {
    // Entity has attribute mappings but NO inline mapping block; the target
    // table is declared in a separate map.ttrm via `def er2db_entity`.
    const db = `model db schema dbo
def table QXXUKAZMUHOD { columns: [ def column IDXXUKAZMU { type: int } ] }
`;
    const map = `model binding
def er2db_entity hodnoty { entity: er.entity.hodnoty, target: { table: db.dbo.QXXUKAZMUHOD } }
`;
    const er = `model er schema entity
def entity hodnoty {
  attributes: [ def attribute id_uk { type: int, binding: IDXXUKAZMU } ]
}
`;
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('file:///p/db.ttrm', parseString(db).ast!, 'db', 'dbo', '');
    symbols.upsertDocument('file:///p/map.ttrm', parseString(map).ast!, 'binding', '', '');
    const erAst = parseString(er).ast!;
    symbols.upsertDocument('file:///p/er.ttrm', erAst, 'er', 'entity', '');

    const refs = collectBindingReferences(erAst, new Resolver(symbols), 'er', 'entity', '');
    expect(refs).toHaveLength(1);
    expect(refs[0].targetQname).toBe('db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU');
  });

  it('resolves an inline `target: { view: … }` (view treated like a table)', () => {
    // A db view with columns; the entity binds to it via the `view` target key.
    const db = `model db schema dbo
def view V_HODNOTY {
  columns: [ def column IDV { type: int }, def column NAZEV { type: text } ],
  definitionSql: """SELECT 1"""
}
`;
    const er = `model er schema entity
def entity hodnoty {
  binding: { target: { view: db.dbo.V_HODNOTY } },
  attributes: [
    def attribute a { type: int, binding: IDV },
    def attribute b { type: text, binding: { target: { column: NAZEV } } }
  ]
}
`;
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('file:///p/db.ttrm', parseString(db).ast!, 'db', 'dbo', '');
    const erAst = parseString(er).ast!;
    symbols.upsertDocument('file:///p/er.ttrm', erAst, 'er', 'entity', '');

    const refs = collectBindingReferences(erAst, new Resolver(symbols), 'er', 'entity', '');
    expect(refs.map((r) => r.targetQname).sort()).toEqual([
      'db.dbo.view.V_HODNOTY.IDV',
      'db.dbo.view.V_HODNOTY.NAZEV',
    ]);
  });

  it('resolves the target view from an explicit def er2db_entity { target: { view: … } }', () => {
    const db = `model db schema dbo
def view V_HODNOTY { columns: [ def column IDV { type: int } ], definitionSql: """SELECT 1""" }
`;
    const map = `model binding
def er2db_entity hodnoty { entity: er.entity.hodnoty, target: { view: db.dbo.V_HODNOTY } }
`;
    const er = `model er schema entity
def entity hodnoty {
  attributes: [ def attribute id_v { type: int, binding: IDV } ]
}
`;
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('file:///p/db.ttrm', parseString(db).ast!, 'db', 'dbo', '');
    symbols.upsertDocument('file:///p/map.ttrm', parseString(map).ast!, 'binding', '', '');
    const erAst = parseString(er).ast!;
    symbols.upsertDocument('file:///p/er.ttrm', erAst, 'er', 'entity', '');

    const refs = collectBindingReferences(erAst, new Resolver(symbols), 'er', 'entity', '');
    expect(refs).toHaveLength(1);
    expect(refs[0].targetQname).toBe('db.dbo.view.V_HODNOTY.IDV');
  });

  it('respects the package prefix on both sides', () => {
    const refs = setup(`package billing
model er schema entity
def entity hodnoty {
  binding: { target: { table: db.dbo.QXXUKAZMUHOD } },
  attributes: [ def attribute a { type: int, binding: IDXXUKAZMU } ]
}
`, 'billing');
    expect(refs).toHaveLength(1);
    expect(refs[0].targetQname).toBe('billing.db.dbo.table.QXXUKAZMUHOD.IDXXUKAZMU');
  });
});
