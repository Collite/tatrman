import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { DocumentSymbolTable } from '../symbol-table.js';
import { ProjectSymbolTable } from '../project-symbols.js';

const PACKAGED_ENTITY = `package billing.invoicing
schema er
def entity artikl {
  attributes: [ def attribute id { type: integer } ]
}`;

const UNPACKAGED_ENTITY = `schema er
def entity artikl {
  attributes: [ def attribute id { type: integer } ]
}`;

const STOCK_ROLE = `schema cnc namespace role
def role fact { description: "Fact role" }`;

describe('B2.1 — SymbolEntry carries packageName and schemaCode', () => {
  it('package declaration populates packageName on all entries', () => {
    const result = parseString(PACKAGED_ENTITY, 'file:///pkg.ttrm');
    const table = new DocumentSymbolTable('file:///pkg.ttrm', result.ast!, 'er', '');
    const entries = table.all();
    expect(entries.length).toBeGreaterThan(0);
    for (const entry of entries) {
      expect(entry.packageName).toBe('billing.invoicing');
      expect(entry.schemaCode).toBe('er');
    }
  });

  it('no package declaration gives packageName === ""', () => {
    const result = parseString(UNPACKAGED_ENTITY, 'file:///unpkg.ttrm');
    const table = new DocumentSymbolTable('file:///unpkg.ttrm', result.ast!, 'er', '');
    const entries = table.all();
    for (const entry of entries) {
      expect(entry.packageName).toBe('');
      expect(entry.schemaCode).toBe('er');
    }
  });
});

describe('B2.3 — qname is package-prefixed', () => {
  it('package billing.invoicing + def entity artikl → qname starts with billing.invoicing.er.', () => {
    const result = parseString(PACKAGED_ENTITY, 'file:///pkg.ttrm');
    const table = new DocumentSymbolTable('file:///pkg.ttrm', result.ast!, 'er', '');
    const entityEntry = table.all().find((e) => e.kind === 'entity');
    expect(entityEntry).toBeDefined();
    expect(entityEntry!.qname).toBe('billing.invoicing.er.entity.artikl');
    expect(entityEntry!.name).toBe('artikl');
    expect(entityEntry!.packageName).toBe('billing.invoicing');
    expect(entityEntry!.schemaCode).toBe('er');
  });

  it('no package → qname is v1 shape (er.entity.artikl)', () => {
    const result = parseString(UNPACKAGED_ENTITY, 'file:///unpkg.ttrm');
    const table = new DocumentSymbolTable('file:///unpkg.ttrm', result.ast!, 'er', '');
    const entityEntry = table.all().find((e) => e.kind === 'entity');
    expect(entityEntry).toBeDefined();
    expect(entityEntry!.qname).toBe('er.entity.artikl');
    expect(entityEntry!.packageName).toBe('');
  });

  it('package + namespace: billing.invoicing, ns myns, entity Order → billing.invoicing.er.myns.Order', () => {
    const result = parseString(
      `package billing.invoicing
schema er namespace myns
def entity Order {}`,
      'file:///pkg.ttrm'
    );
    const table = new DocumentSymbolTable('file:///pkg.ttrm', result.ast!, 'er', 'myns');
    const entityEntry = table.all().find((e) => e.kind === 'entity');
    expect(entityEntry!.qname).toBe('billing.invoicing.er.myns.Order');
  });

  it('stock cnc roles file: cnc.cnc.role.* form (doubled cnc, per contracts §3.1 for stock cnc package)', () => {
    const result = parseString(STOCK_ROLE, 'stock://cnc-roles.ttrm');
    const table = new DocumentSymbolTable('stock://cnc-roles.ttrm', result.ast!, 'cnc', 'role');
    const roleEntry = table.get('cnc.cnc.role.fact');
    expect(roleEntry).toBeDefined();
    expect(roleEntry!.qname).toBe('cnc.cnc.role.fact');
    expect(roleEntry!.packageName).toBe('');
    expect(roleEntry!.schemaCode).toBe('cnc');
  });
});

describe('B2.4 — getByPackage()', () => {
  it('returns only entries from the specified package', () => {
    const projectSymbols = new ProjectSymbolTable();

    const ast1 = parseString(
      `package billing.invoicing
schema er namespace myns
def entity artikl { attributes: [def attribute id { type: integer }] }`,
      'file:///pkg1.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg1.ttrm', ast1, 'er', 'myns');

    const ast2 = parseString(
      `package billing.invoicing
schema er namespace myns
def entity partner { attributes: [def attribute id { type: integer }] }`,
      'file:///pkg2.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg2.ttrm', ast2, 'er', 'myns');

    const ast3 = parseString(
      `package accounting
schema er namespace myns
def entity invoice {}`,
      'file:///pkg3.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg3.ttrm', ast3, 'er', 'myns');

    const billingEntries = projectSymbols.getByPackage('billing.invoicing');
    expect(billingEntries.length).toBeGreaterThanOrEqual(4);
    for (const entry of billingEntries) {
      expect(entry.packageName).toBe('billing.invoicing');
    }

    const accountingEntries = projectSymbols.getByPackage('accounting');
    expect(accountingEntries.length).toBeGreaterThanOrEqual(1);
    for (const entry of accountingEntries) {
      expect(entry.packageName).toBe('accounting');
    }

    const defaultEntries = projectSymbols.getByPackage('');
    expect(defaultEntries).toHaveLength(0);
  });
});

describe('B2.5 — getBySuffix()', () => {
  it('returns entries whose qname ends with the suffix', () => {
    const projectSymbols = new ProjectSymbolTable();

    const ast1 = parseString(
      `package billing
schema er
def entity artikl { attributes: [def attribute id { type: integer }] }`,
      'file:///pkg.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg.ttrm', ast1, 'er', '');

    const results = projectSymbols.getBySuffix('er.entity.artikl');
    expect(results.length).toBeGreaterThanOrEqual(1);
    expect(results.some((r) => r.qname === 'billing.er.entity.artikl')).toBe(true);
  });

  it('exact match also works', () => {
    const projectSymbols = new ProjectSymbolTable();
    const ast = parseString(`schema er namespace ns1 def entity X {}`, 'file:///test.ttrm').ast!;
    projectSymbols.upsertDocument('file:///test.ttrm', ast, 'er', 'ns1');

    const results = projectSymbols.getBySuffix('er.ns1.X');
    expect(results.length).toBeGreaterThanOrEqual(1);
  });

  it('returns multiple entries when two packages both have the same suffix', () => {
    const projectSymbols = new ProjectSymbolTable();

    const ast1 = parseString(
      `package pkg1
schema er
def entity artikl { attributes: [def attribute id { type: integer }] }`,
      'file:///pkg1.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg1.ttrm', ast1, 'er', '');

    const ast2 = parseString(
      `package pkg2
schema er
def entity artikl { attributes: [def attribute id { type: integer }] }`,
      'file:///pkg2.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///pkg2.ttrm', ast2, 'er', '');

    const results = projectSymbols.getBySuffix('er.entity.artikl');
    expect(results.length).toBe(2);
    const qnames = results.map((r) => r.qname).sort();
    expect(qnames).toEqual(['pkg1.er.entity.artikl', 'pkg2.er.entity.artikl']);
  });
});

describe('B2.6 — listPackages()', () => {
  it('returns sorted distinct package names including empty-string sentinel', () => {
    const projectSymbols = new ProjectSymbolTable();

    const ast1 = parseString(
      `package billing.invoicing
schema er
def entity a {}`,
      'file:///f1.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///f1.ttrm', ast1, 'er', '');

    const ast2 = parseString(
      `package accounting
schema er
def entity b {}`,
      'file:///f2.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///f2.ttrm', ast2, 'er', '');

    const ast3 = parseString(
      `schema er namespace ns1
def entity c {}`,
      'file:///f3.ttrm'
    ).ast!;
    projectSymbols.upsertDocument('file:///f3.ttrm', ast3, 'er', 'ns1');

    const packages = projectSymbols.listPackages();
    expect(packages).toEqual(['', 'accounting', 'billing.invoicing']);
  });

  it('returns only empty string when no packages declared', () => {
    const projectSymbols = new ProjectSymbolTable();
    const ast = parseString(`schema er def entity X {}`, 'file:///f.ttrm').ast!;
    projectSymbols.upsertDocument('file:///f.ttrm', ast, 'er', '');
    expect(projectSymbols.listPackages()).toEqual(['']);
  });
});