import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';

function tableWith(uri: string, src: string) {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.modelDirective?.modelCode ?? 'db';
  const namespace = ast.modelDirective?.schema ?? '';
  const packageName = ast.packageDecl?.name ?? '';
  const table = new ProjectSymbolTable();
  table.upsertDocument(uri, ast, schemaCode, namespace, packageName);
  return { table, schemaCode, namespace, packageName };
}

describe('Resolver (B3 six-step chain)', () => {
  describe('step 1: lexical', () => {
    it('resolves a bare id as a child of the enclosing entity', () => {
      const { table } = tableWith(
        'er.ttrm',
        `model er schema entity
         def entity artikl {
           nameAttribute: id,
           attributes: [def attribute id { type: int }]
         }`
      );
      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'id', parts: ['id'] },
        { schemaCode: 'er', namespace: 'entity', enclosingQname: 'er.entity.artikl' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('lexical');
    });
  });

  describe('step 2: same-package', () => {
    it('resolves a bare ref to a def in the same package', () => {
      const table = new ProjectSymbolTable();
      const astA = parseString(
        `package billing.invoicing
         model er schema entity
         def entity artikl { attributes: [def attribute id { type: int }] }`,
        'billing/invoicing/a.ttrm'
      ).ast!;
      table.upsertDocument('billing/invoicing/a.ttrm', astA, 'er', 'entity', 'billing.invoicing');

      const astB = parseString(
        `package billing.invoicing
         model er schema entity
         def relation r { from: artikl, to: artikl }`,
        'billing/invoicing/b.ttrm'
      ).ast!;
      table.upsertDocument('billing/invoicing/b.ttrm', astB, 'er', 'entity', 'billing.invoicing');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'artikl', parts: ['artikl'] },
        { schemaCode: 'er', namespace: 'entity', packageName: 'billing.invoicing' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('same-package');
    });
  });

  describe('step 3: named import', () => {
    it('resolves a bare ref via a named import', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.products
         model er schema entity
         def entity produkt { attributes: [def attribute id { type: int }] }`,
        'billing/products/target.ttrm'
      ).ast!;
      table.upsertDocument('billing/products/target.ttrm', astTarget, 'er', 'entity', 'billing.products');

      const astSource = parseString(
        `package billing.app
         import billing.products.er.entity.produkt
         model er schema entity
         def relation r { from: produkt, to: produkt }`,
        'billing/app/source.ttrm'
      ).ast!;
      table.upsertDocument('billing/app/source.ttrm', astSource, 'er', 'entity', 'billing.app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'produkt', parts: ['produkt'] },
        { schemaCode: 'er', namespace: 'entity', imports: astSource.imports, packageName: 'billing.app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('named-import');
    });
  });

  describe('step 4: wildcard import', () => {
    it('resolves a bare ref via a wildcard import', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.products
         model er schema entity
         def entity produkt { attributes: [def attribute id { type: int }] }`,
        'billing/products/target.ttrm'
      ).ast!;
      table.upsertDocument('billing/products/target.ttrm', astTarget, 'er', 'entity', 'billing.products');

      const astSource = parseString(
        `package billing.app
         import billing.products.*
         model er schema entity
         def relation r { from: produkt, to: produkt }`,
        'billing/app/source.ttrm'
      ).ast!;
      table.upsertDocument('billing/app/source.ttrm', astSource, 'er', 'entity', 'billing.app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'produkt', parts: ['produkt'] },
        { schemaCode: 'er', namespace: 'entity', imports: astSource.imports, packageName: 'billing.app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('wildcard-import');
    });

it('wildcard does NOT recurse into sub-packages', () => {
      const table = new ProjectSymbolTable();
      const astSub = parseString(
        `package billing.products.subordinates
         model er schema entity
         def entity worker { attributes: [] }`,
        'billing/products/subordinates/worker.ttrm'
      ).ast!;
      table.upsertDocument('billing/products/subordinates/worker.ttrm', astSub, 'er', 'entity', 'billing.products.subordinates');

      const astAnother = parseString(
        `package other.pkg
         model er schema entity
         def entity worker { attributes: [] }`,
        'other/worker.ttrm'
      ).ast!;
      table.upsertDocument('other/worker.ttrm', astAnother, 'er', 'entity', 'other.pkg');

      const astSource = parseString(
        `package billing.app
         import billing.products.*
         model er schema entity
         def relation r { from: worker, to: worker }`,
        'billing/app/source.ttrm'
      ).ast!;
      table.upsertDocument('billing/app/source.ttrm', astSource, 'er', 'entity', 'billing.app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'worker', parts: ['worker'] },
        { schemaCode: 'er', namespace: 'entity', imports: astSource.imports, packageName: 'billing.app' }
      );
      expect(res.resolved).toBe(false);
    });
  });

  describe('step 5: auto-import (cnc stock)', () => {
    it('resolves bare cnc.role.<name> via auto-import', () => {
      const table = new ProjectSymbolTable();
      const astStock = parseString(
        `model cnc schema role
         def role fact { description: "fact" }`,
        'stock://cnc-roles.ttrm'
      ).ast!;
      table.upsertDocument('stock://cnc-roles.ttrm', astStock, 'cnc', 'role', '');

      const astSource = parseString(
        `model er schema entity
         def entity artikl {
           nameAttribute: fact,
           attributes: []
         }`,
        'er.ttrm'
      ).ast!;
      table.upsertDocument('er.ttrm', astSource, 'er', 'entity', '');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'fact', parts: ['fact'] },
        { schemaCode: 'er', namespace: 'entity' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) {
        expect(res.viaStep).toBe('auto-import');
        expect(res.symbol.qname).toBe('cnc.role.fact');
      }
    });
  });

  describe('step 6: fully-qualified-but-unique', () => {
    it('resolves multi-part FQN ref via step 6 when no imports and unique across project', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.invoicing
         model er schema entity
         def entity artikl { attributes: [] }`,
        'billing/invoicing/artikl.ttrm'
      ).ast!;
      table.upsertDocument('billing/invoicing/artikl.ttrm', astTarget, 'er', 'entity', 'billing.invoicing');

      const astSource = parseString(
        `package billing.app
         model er schema entity
         def relation r { from: billing.invoicing.er.entity.artikl, to: billing.invoicing.er.entity.artikl }`,
        'billing/app/source.ttrm'
      ).ast!;
      table.upsertDocument('billing/app/source.ttrm', astSource, 'er', 'entity', 'billing.app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'billing.invoicing.er.entity.artikl', parts: ['billing', 'invoicing', 'er', 'entity', 'artikl'] },
        { schemaCode: 'er', namespace: 'entity', packageName: 'billing.app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('fully-qualified');
    });

    it('resolves bare-but-unique ref via step 6 when no imports exist anywhere', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.invoicing
         model er schema entity
         def entity artikl { attributes: [] }`,
        'billing/invoicing/artikl.ttrm'
      ).ast!;
      table.upsertDocument('billing/invoicing/artikl.ttrm', astTarget, 'er', 'entity', 'billing.invoicing');

      const astSource = parseString(
        `package billing.app
         model er schema entity
         def relation r { from: artikl, to: artikl }`,
        'billing/app/source.ttrm'
      ).ast!;
      table.upsertDocument('billing/app/source.ttrm', astSource, 'er', 'entity', 'billing.app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'artikl', parts: ['artikl'] },
        { schemaCode: 'er', namespace: 'entity', packageName: 'billing.app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('fully-qualified');
    });
  });

  describe('ambiguity', () => {
    it('returns ambiguous when two wildcards expose the same name', () => {
      const table = new ProjectSymbolTable();
      const astA = parseString(
        `package pkgA
         model er schema entity
         def entity thing { attributes: [] }`,
        'pkgA/a.ttrm'
      ).ast!;
      table.upsertDocument('pkgA/a.ttrm', astA, 'er', 'entity', 'pkgA');

      const astB = parseString(
        `package pkgB
         model er schema entity
         def entity thing { attributes: [] }`,
        'pkgB/b.ttrm'
      ).ast!;
      table.upsertDocument('pkgB/b.ttrm', astB, 'er', 'entity', 'pkgB');

      const astSource = parseString(
        `package app
         import pkgA.*
         import pkgB.*
         model er schema entity
         def relation r { from: thing, to: thing }`,
        'app/source.ttrm'
      ).ast!;
      table.upsertDocument('app/source.ttrm', astSource, 'er', 'entity', 'app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'thing', parts: ['thing'] },
        { schemaCode: 'er', namespace: 'entity', imports: astSource.imports, packageName: 'app' }
      );
      expect(res.resolved).toBe(false);
      if (!res.resolved) {
        expect(res.reason).toBe('ambiguous');
        expect(res.candidates).toHaveLength(2);
      }
    });
  });

  describe('ResolutionResult shape', () => {
    it('resolved result has viaStep field', () => {
      const { table } = tableWith(
        'er.ttrm',
        `model er schema entity
         def entity artikl { attributes: [def attribute id { type: int }] }`
      );
      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'artikl', parts: ['artikl'] },
        { schemaCode: 'er', namespace: 'entity' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(typeof res.viaStep).toBe('string');
    });

    it('unresolved result has tried: ResolutionAttempt[]', () => {
      const { table } = tableWith(
        'er.ttrm',
        `model er schema entity
         def entity artikl { attributes: [] }`
      );
      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'does_not_exist', parts: ['does_not_exist'] },
        { schemaCode: 'er', namespace: 'entity' }
      );
      expect(res.resolved).toBe(false);
      if (!res.resolved) {
        expect(Array.isArray(res.tried)).toBe(true);
        expect(res.tried.length).toBeGreaterThan(0);
        expect(typeof res.tried[0].step).toBe('string');
        expect(typeof res.tried[0].candidate).toBe('string');
      }
    });
  });
});