import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { DocumentSymbolTable } from '../symbol-table.js';
import { ProjectSymbolTable } from '../project-symbols.js';

describe('DocumentSymbolTable extended kinds (H.1)', () => {
  describe('relation', () => {
    it('emits SymbolEntry with kind relation at qname from document schema/namespace', () => {
      const ast = parseString(`model er schema entity
def relation je_vyrobeno {
  from: er.entity.vyrobek,
  to: er.entity.artikl,
  cardinality: { from: "1", to: "*" }
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'er', 'entity');
      const e = tbl.get('er.relation.je_vyrobeno');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('relation');
      expect(e!.qname).toBe('er.relation.je_vyrobeno');
      expect(e!.name).toBe('je_vyrobeno');
    });
  });

  describe('query', () => {
    it('emits SymbolEntry with kind query at qname from document schema/namespace', () => {
      const ast = parseString(`model query schema q1
def query find_artikl {
  language: SQL,
  sourceText: "SELECT * FROM artikl"
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'query', 'q1');
      const e = tbl.get('db.q1.query.find_artikl');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('query');
      expect(e!.qname).toBe('db.q1.query.find_artikl');
      expect(e!.name).toBe('find_artikl');
    });
  });

  describe('role', () => {
    it('emits SymbolEntry with kind role at qname from document schema/namespace', () => {
      const ast = parseString(`model cnc schema role
def role autor { description: "Author role" }
`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'cnc', 'role');
      const e = tbl.get('cnc.role.autor');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('role');
      expect(e!.qname).toBe('cnc.role.autor');
      expect(e!.name).toBe('autor');
    });
  });

  describe('er2dbEntity', () => {
    it('emits SymbolEntry with kind er2dbEntity at qname from document schema/namespace', () => {
      const ast = parseString(`model binding schema er2db
def er2db_entity tabulka_artikl {
  entity: er.entity.artikl,
  target: db.table
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'binding', 'er2db');
      const e = tbl.get('binding.er2dbEntity.tabulka_artikl');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('er2dbEntity');
      expect(e!.qname).toBe('binding.er2dbEntity.tabulka_artikl');
      expect(e!.name).toBe('tabulka_artikl');
    });
  });

  describe('er2dbAttribute', () => {
    it('emits SymbolEntry with kind er2dbAttribute at qname from document schema/namespace', () => {
      const ast = parseString(`model binding schema er2db
def er2db_attribute col_kod {
  attribute: er.entity.artikl.kod,
  target: db.column
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'binding', 'er2db');
      const e = tbl.get('binding.er2dbAttribute.col_kod');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('er2dbAttribute');
      expect(e!.qname).toBe('binding.er2dbAttribute.col_kod');
      expect(e!.name).toBe('col_kod');
    });
  });

  describe('er2dbRelation', () => {
    it('emits SymbolEntry with kind er2dbRelation at qname from document schema/namespace', () => {
      const ast = parseString(`model binding schema er2db
def er2db_relation rel_je_vyrobeno {
  relation: er.entity.je_vyrobeno,
  fk: binding.er2dbEntity.col_vyrobek
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'binding', 'er2db');
      const e = tbl.get('binding.er2dbRelation.rel_je_vyrobeno');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('er2dbRelation');
      expect(e!.qname).toBe('binding.er2dbRelation.rel_je_vyrobeno');
      expect(e!.name).toBe('rel_je_vyrobeno');
    });
  });

  describe('er2cncRole', () => {
    it('emits SymbolEntry with kind er2cncRole at qname from document schema/namespace', () => {
      const ast = parseString(`model cnc schema entity
def er2cnc_role cnc_role_autor {
  entity: er.entity.autor,
  role: cnc.role.autor
}`, 'file:///test.ttrm').ast!;
      const tbl = new DocumentSymbolTable('file:///test.ttrm', ast, 'cnc', 'entity');
      const e = tbl.get('cnc.er2cncRole.cnc_role_autor');
      expect(e).toBeDefined();
      expect(e!.kind).toBe('er2cncRole');
      expect(e!.qname).toBe('cnc.er2cncRole.cnc_role_autor');
      expect(e!.name).toBe('cnc_role_autor');
    });
  });

  describe('ProjectSymbolTable.all() with mixed kinds', () => {
    it('returns all kinds including the seven new ones', () => {
      const project = new ProjectSymbolTable();

      const erAst = parseString(`model er schema entity
def entity artikl { attributes: [def attribute id { type: int, isKey: true }] }
def relation rel1 { from: er.entity.a, to: er.entity.b, cardinality: { from: "1", to: "*" } }
`, 'file:///er.ttrm').ast!;
      project.upsertDocument('file:///er.ttrm', erAst, 'er', 'entity');

      const queryAst = parseString(`model query schema q1
def query q1 { language: SQL, sourceText: "SELECT 1" }
`, 'file:///query.ttrm').ast!;
      project.upsertDocument('file:///query.ttrm', queryAst, 'query', 'q1');

      const mapAst = parseString(`model binding schema er2db
def er2db_entity e2 { entity: er.entity.x, target: db.table }
`, 'file:///map.ttrm').ast!;
      project.upsertDocument('file:///map.ttrm', mapAst, 'binding', 'er2db');

      const allSymbols = project.all();
      const kinds = [...new Set(allSymbols.map(s => s.kind))];

      expect(kinds).toContain('relation');
      expect(kinds).toContain('query');
      expect(kinds).toContain('er2dbEntity');

      const relationSymbols = allSymbols.filter(s => s.kind === 'relation');
      expect(relationSymbols.length).toBeGreaterThanOrEqual(1);
      expect(relationSymbols[0].qname).toBe('er.relation.rel1');

      const querySymbols = allSymbols.filter(s => s.kind === 'query');
      expect(querySymbols.length).toBeGreaterThanOrEqual(1);
      expect(querySymbols[0].qname).toBe('db.q1.query.q1');

      const er2dbSymbols = allSymbols.filter(s => s.kind === 'er2dbEntity');
      expect(er2dbSymbols.length).toBeGreaterThanOrEqual(1);
      expect(er2dbSymbols[0].qname).toBe('binding.er2dbEntity.e2');
    });
  });
});