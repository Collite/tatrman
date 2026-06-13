import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { DocumentSymbolTable } from '../symbol-table.js';
import { ProjectSymbolTable } from '../project-symbols.js';

const SIMPLE_ENTITY = `schema er namespace myns
def entity Order {
  attributes: [
    def attribute id { type: integer },
    def attribute customer_id { type: integer },
    def attribute total_amount { type: decimal }
  ]
}`;

const SIMPLE_TABLE = `schema db namespace dbo
def table orders {
  columns: [
    def column id { type: integer },
    def column created_at { type: timestamp }
  ]
}`;

const SIMPLE_VIEW = `schema db namespace dbo
def view order_summary {
  columns: [
    def column order_id { type: integer },
    def column total { type: decimal }
  ]
}`;

const SIMPLE_STOCK_ROLE = `schema cnc namespace role
def role fact { description: "Fact role" }`;

const SIMPLE_STOCK_ENTITY = `schema cnc
def entity orders {
  attributes: [
    def attribute id { type: integer }
  ]
}`;

describe('DocumentSymbolTable', () => {
  describe('empty document', () => {
    it('creates empty table for document with no definitions', () => {
      const ast = parseString('schema db model test {}', 'file:///test.ttr').ast!;
      const table = new DocumentSymbolTable('file:///test.ttr', ast, 'db', '');
      expect(table.all()).toHaveLength(0);
    });
  });

  describe('document with entity and attributes', () => {
    it('registers entity + 3 attributes as 4 entries', () => {
      const result = parseString(SIMPLE_ENTITY, 'file:///test.ttr');
      expect(result.errors.filter((e) => e.severity === 'error')).toHaveLength(0);
      expect(result.ast).toBeDefined();
      const table = new DocumentSymbolTable(
        'file:///test.ttr',
        result.ast!,
        'er',
        'myns'
      );

      const entries = table.all();
      expect(entries).toHaveLength(4);

      const entityEntry = entries.find((e) => e.name === 'Order');
      expect(entityEntry).toBeDefined();
      expect(entityEntry?.kind).toBe('entity');

      const attrEntries = entries.filter((e) => e.parent?.includes('Order'));
      expect(attrEntries).toHaveLength(3);
    });

    it('registers entity with qualified qname', () => {
      const result = parseString(SIMPLE_ENTITY, 'file:///test.ttr');
      const table = new DocumentSymbolTable('file:///test.ttr', result.ast!, 'er', 'myns');

      const entityEntry = table.get('er.myns.Order');
      expect(entityEntry).toBeDefined();
      expect(entityEntry?.name).toBe('Order');
    });
  });

  describe('conflict detection', () => {
    it('detects duplicate qname entries in project', () => {
      const projectSymbols = new ProjectSymbolTable();

      const content1 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
      const ast1 = parseString(content1, 'file:///file1.ttr').ast!;
      projectSymbols.upsertDocument('file:///file1.ttr', ast1, 'db', '');

      const content2 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
      const ast2 = parseString(content2, 'file:///file2.ttr').ast!;
      projectSymbols.upsertDocument('file:///file2.ttr', ast2, 'db', '');

      const dups = projectSymbols.duplicates();
      expect(dups.some((d) => d.qname === 'db.dbo.users')).toBe(true);
    });
  });

  describe('stock vocabulary loading', () => {
    it('handles stock:// prefixed URIs without conflict', () => {
      const projectSymbols = new ProjectSymbolTable();

      const stockAst = parseString(SIMPLE_STOCK_ROLE, 'stock://cnc-roles.ttr').ast!;
      projectSymbols.upsertDocument('stock://cnc-roles.ttr', stockAst, 'cnc', 'role');

      const userAst = parseString(SIMPLE_STOCK_ENTITY, 'file:///project.ttr').ast!;
      projectSymbols.upsertDocument('file:///project.ttr', userAst, 'cnc', '');

      const factEntry = projectSymbols.get('cnc.cnc.role.fact');
      expect(factEntry).toBeDefined();
      expect(factEntry?.documentUri).toBe('stock://cnc-roles.ttr');

      const orderEntry = projectSymbols.get('cnc.entity.orders');
      expect(orderEntry).toBeDefined();
      expect(orderEntry?.documentUri).toBe('file:///project.ttr');
    });
  });

  describe('manifest-driven namespace defaults', () => {
    it('uses namespace from constructor', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttr');
      const table = new DocumentSymbolTable('file:///test.ttr', result.ast!, 'db', 'dbo');

      const entry = table.get('db.dbo.orders');
      expect(entry).toBeDefined();
      expect(entry?.name).toBe('orders');
    });

    it('falls back to schema namespace when no explicit namespace', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttr');
      const table = new DocumentSymbolTable('file:///test.ttr', result.ast!, 'db', 'dbo');

      const entry = table.get('db.dbo.orders');
      expect(entry).toBeDefined();
    });
  });

  describe('table with columns', () => {
    it('registers table + columns as separate entries', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttr');
      const table = new DocumentSymbolTable('file:///test.ttr', result.ast!, 'db', 'dbo');

      const entries = table.all();
      expect(entries).toHaveLength(3);

      const tableEntry = entries.find((e) => e.name === 'orders' && e.kind === 'table');
      expect(tableEntry).toBeDefined();

      const colEntries = entries.filter((e) => e.kind === 'column' && e.parent?.includes('orders'));
      expect(colEntries).toHaveLength(2);
    });
  });

  describe('view with columns', () => {
    it('registers view + columns', () => {
      const result = parseString(SIMPLE_VIEW, 'file:///test.ttr');
      const table = new DocumentSymbolTable('file:///test.ttr', result.ast!, 'db', 'dbo');

      const entries = table.all();
      expect(entries).toHaveLength(3);

      const viewEntry = entries.find((e) => e.name === 'order_summary' && e.kind === 'view');
      expect(viewEntry).toBeDefined();
    });
  });
});

describe('ProjectSymbolTable', () => {
  it('upsertDocument replaces existing entries for same URI', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content1 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///test.ttr').ast!;
    projectSymbols.upsertDocument('file:///test.ttr', ast1, 'db', '');

    expect(projectSymbols.all()).toHaveLength(2);

    const content2 = `schema db
def table users {
  columns: [
    def column id { type: integer },
    def column name { type: varchar }
  ]
}`;
    const ast2 = parseString(content2, 'file:///test.ttr').ast!;
    projectSymbols.upsertDocument('file:///test.ttr', ast2, 'db', '');

    const entries = projectSymbols.all();
    expect(entries).toHaveLength(3);

    const userTable = projectSymbols.get('db.dbo.users');
    expect(userTable).toBeDefined();
    expect(userTable?.kind).toBe('table');
  });

  it('removeDocument removes all entries for URI', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast = parseString(content, 'file:///test.ttr').ast!;
    projectSymbols.upsertDocument('file:///test.ttr', ast, 'db', '');

    expect(projectSymbols.all()).toHaveLength(2);

    projectSymbols.removeDocument('file:///test.ttr');

    expect(projectSymbols.all()).toHaveLength(0);
  });

  it('findByName returns all entries with matching name', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content1 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///file1.ttr').ast!;
    projectSymbols.upsertDocument('file:///file1.ttr', ast1, 'db', '');

    const content2 = `schema er
def entity users {
  attributes: [ def attribute id { type: integer } ]
}`;
    const ast2 = parseString(content2, 'file:///file2.ttr').ast!;
    projectSymbols.upsertDocument('file:///file2.ttr', ast2, 'er', '');

    const results = projectSymbols.findByName('users');
    expect(results.length).toBeGreaterThanOrEqual(2);
  });

  it('duplicates returns qnames with multiple entries', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content1 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///file1.ttr').ast!;
    projectSymbols.upsertDocument('file:///file1.ttr', ast1, 'db', '');

    const content2 = `schema db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast2 = parseString(content2, 'file:///file2.ttr').ast!;
    projectSymbols.upsertDocument('file:///file2.ttr', ast2, 'db', '');

    const dups = projectSymbols.duplicates();
    expect(dups.some((d) => d.qname === 'db.dbo.users')).toBe(true);
  });
});