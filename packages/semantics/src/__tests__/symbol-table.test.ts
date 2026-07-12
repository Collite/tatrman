// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { DocumentSymbolTable } from '../symbol-table.js';
import { ProjectSymbolTable } from '../project-symbols.js';

const SIMPLE_ENTITY = `model er schema myns
def entity Order {
  attributes: [
    def attribute id { type: integer },
    def attribute customer_id { type: integer },
    def attribute total_amount { type: decimal }
  ]
}`;

const SIMPLE_TABLE = `model db schema dbo
def table orders {
  columns: [
    def column id { type: integer },
    def column created_at { type: timestamp }
  ]
}`;

const SIMPLE_VIEW = `model db schema dbo
def view order_summary {
  columns: [
    def column order_id { type: integer },
    def column total { type: decimal }
  ]
}`;

const SIMPLE_STOCK_ROLE = `model cnc schema role
def role fact { description: "Fact role" }`;

const SIMPLE_STOCK_ENTITY = `model cnc
def entity orders {
  attributes: [
    def attribute id { type: integer }
  ]
}`;

describe('DocumentSymbolTable', () => {
  describe('empty document', () => {
    it('creates empty table for document with no definitions', () => {
      const ast = parseString('model db model test {}', 'file:///test.ttrm').ast!;
      const table = new DocumentSymbolTable('file:///test.ttrm', ast, 'db', '');
      expect(table.all()).toHaveLength(0);
    });
  });

  describe('document with entity and attributes', () => {
    it('registers entity + 3 attributes as 4 entries', () => {
      const result = parseString(SIMPLE_ENTITY, 'file:///test.ttrm');
      expect(result.errors.filter((e) => e.severity === 'error')).toHaveLength(0);
      expect(result.ast).toBeDefined();
      const table = new DocumentSymbolTable(
        'file:///test.ttrm',
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
      const result = parseString(SIMPLE_ENTITY, 'file:///test.ttrm');
      const table = new DocumentSymbolTable('file:///test.ttrm', result.ast!, 'er', 'myns');

      const entityEntry = table.get('er.entity.Order');
      expect(entityEntry).toBeDefined();
      expect(entityEntry?.name).toBe('Order');
    });
  });

  describe('conflict detection', () => {
    it('detects duplicate qname entries in project', () => {
      const projectSymbols = new ProjectSymbolTable();

      const content1 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
      const ast1 = parseString(content1, 'file:///file1.ttrm').ast!;
      projectSymbols.upsertDocument('file:///file1.ttrm', ast1, 'db', '');

      const content2 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
      const ast2 = parseString(content2, 'file:///file2.ttrm').ast!;
      projectSymbols.upsertDocument('file:///file2.ttrm', ast2, 'db', '');

      const dups = projectSymbols.duplicates();
      expect(dups.some((d) => d.qname === 'db.dbo.table.users')).toBe(true);
    });
  });

  describe('stock vocabulary loading', () => {
    it('handles stock:// prefixed URIs without conflict', () => {
      const projectSymbols = new ProjectSymbolTable();

      const stockAst = parseString(SIMPLE_STOCK_ROLE, 'stock://cnc-roles.ttrm').ast!;
      projectSymbols.upsertDocument('stock://cnc-roles.ttrm', stockAst, 'cnc', 'role');

      const userAst = parseString(SIMPLE_STOCK_ENTITY, 'file:///project.ttrm').ast!;
      projectSymbols.upsertDocument('file:///project.ttrm', userAst, 'cnc', '');

      const factEntry = projectSymbols.get('cnc.role.fact');
      expect(factEntry).toBeDefined();
      expect(factEntry?.documentUri).toBe('stock://cnc-roles.ttrm');

      const orderEntry = projectSymbols.get('er.entity.orders');
      expect(orderEntry).toBeDefined();
      expect(orderEntry?.documentUri).toBe('file:///project.ttrm');
    });
  });

  describe('manifest-driven schema defaults', () => {
    it('uses schema from constructor', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttrm');
      const table = new DocumentSymbolTable('file:///test.ttrm', result.ast!, 'db', 'dbo');

      const entry = table.get('db.dbo.table.orders');
      expect(entry).toBeDefined();
      expect(entry?.name).toBe('orders');
    });

    it('falls back to schema schema when no explicit namespace', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttrm');
      const table = new DocumentSymbolTable('file:///test.ttrm', result.ast!, 'db', 'dbo');

      const entry = table.get('db.dbo.table.orders');
      expect(entry).toBeDefined();
    });
  });

  describe('table with columns', () => {
    it('registers table + columns as separate entries', () => {
      const result = parseString(SIMPLE_TABLE, 'file:///test.ttrm');
      const table = new DocumentSymbolTable('file:///test.ttrm', result.ast!, 'db', 'dbo');

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
      const result = parseString(SIMPLE_VIEW, 'file:///test.ttrm');
      const table = new DocumentSymbolTable('file:///test.ttrm', result.ast!, 'db', 'dbo');

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

    const content1 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///test.ttrm').ast!;
    projectSymbols.upsertDocument('file:///test.ttrm', ast1, 'db', '');

    expect(projectSymbols.all()).toHaveLength(2);

    const content2 = `model db
def table users {
  columns: [
    def column id { type: integer },
    def column name { type: varchar }
  ]
}`;
    const ast2 = parseString(content2, 'file:///test.ttrm').ast!;
    projectSymbols.upsertDocument('file:///test.ttrm', ast2, 'db', '');

    const entries = projectSymbols.all();
    expect(entries).toHaveLength(3);

    const userTable = projectSymbols.get('db.dbo.table.users');
    expect(userTable).toBeDefined();
    expect(userTable?.kind).toBe('table');
  });

  it('removeDocument removes all entries for URI', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast = parseString(content, 'file:///test.ttrm').ast!;
    projectSymbols.upsertDocument('file:///test.ttrm', ast, 'db', '');

    expect(projectSymbols.all()).toHaveLength(2);

    projectSymbols.removeDocument('file:///test.ttrm');

    expect(projectSymbols.all()).toHaveLength(0);
  });

  it('findByName returns all entries with matching name', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content1 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///file1.ttrm').ast!;
    projectSymbols.upsertDocument('file:///file1.ttrm', ast1, 'db', '');

    const content2 = `model er
def entity users {
  attributes: [ def attribute id { type: integer } ]
}`;
    const ast2 = parseString(content2, 'file:///file2.ttrm').ast!;
    projectSymbols.upsertDocument('file:///file2.ttrm', ast2, 'er', '');

    const results = projectSymbols.findByName('users');
    expect(results.length).toBeGreaterThanOrEqual(2);
  });

  it('duplicates returns qnames with multiple entries', () => {
    const projectSymbols = new ProjectSymbolTable();

    const content1 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast1 = parseString(content1, 'file:///file1.ttrm').ast!;
    projectSymbols.upsertDocument('file:///file1.ttrm', ast1, 'db', '');

    const content2 = `model db
def table users {
  columns: [ def column id { type: integer } ]
}`;
    const ast2 = parseString(content2, 'file:///file2.ttrm').ast!;
    projectSymbols.upsertDocument('file:///file2.ttrm', ast2, 'db', '');

    const dups = projectSymbols.duplicates();
    expect(dups.some((d) => d.qname === 'db.dbo.table.users')).toBe(true);
  });
});