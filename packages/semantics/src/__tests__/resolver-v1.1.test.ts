import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';

function tableWith(uri: string, src: string) {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
  const namespace = ast.schemaDirective?.namespace ?? '';
  const packageName = ast.packageDecl?.name ?? '';
  const table = new ProjectSymbolTable();
  table.upsertDocument(uri, ast, schemaCode, namespace, packageName);
  return { table, schemaCode, namespace, packageName };
}

describe('Resolver (B3 six-step chain)', () => {
  describe('step 1: lexical', () => {
    it('resolves a bare id as a child of the enclosing entity', () => {
      const { table } = tableWith(
        'er.ttr',
        `schema er namespace entity
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
         schema er namespace entity
         def entity artikl { attributes: [def attribute id { type: int }] }`,
        'billing/invoicing/a.ttr'
      ).ast!;
      table.upsertDocument('billing/invoicing/a.ttr', astA, 'er', 'entity', 'billing.invoicing');

      const astB = parseString(
        `package billing.invoicing
         schema er namespace entity
         def relation r { from: artikl, to: artikl }`,
        'billing/invoicing/b.ttr'
      ).ast!;
      table.upsertDocument('billing/invoicing/b.ttr', astB, 'er', 'entity', 'billing.invoicing');

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
         schema er namespace entity
         def entity produkt { attributes: [def attribute id { type: int }] }`,
        'billing/products/target.ttr'
      ).ast!;
      table.upsertDocument('billing/products/target.ttr', astTarget, 'er', 'entity', 'billing.products');

      const astSource = parseString(
        `package billing.app
         import billing.products.er.entity.produkt
         schema er namespace entity
         def relation r { from: produkt, to: produkt }`,
        'billing/app/source.ttr'
      ).ast!;
      table.upsertDocument('billing/app/source.ttr', astSource, 'er', 'entity', 'billing.app');

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
         schema er namespace entity
         def entity produkt { attributes: [def attribute id { type: int }] }`,
        'billing/products/target.ttr'
      ).ast!;
      table.upsertDocument('billing/products/target.ttr', astTarget, 'er', 'entity', 'billing.products');

      const astSource = parseString(
        `package billing.app
         import billing.products.*
         schema er namespace entity
         def relation r { from: produkt, to: produkt }`,
        'billing/app/source.ttr'
      ).ast!;
      table.upsertDocument('billing/app/source.ttr', astSource, 'er', 'entity', 'billing.app');

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
         schema er namespace entity
         def entity worker { attributes: [] }`,
        'billing/products/subordinates/worker.ttr'
      ).ast!;
      table.upsertDocument('billing/products/subordinates/worker.ttr', astSub, 'er', 'entity', 'billing.products.subordinates');

      const astAnother = parseString(
        `package other.pkg
         schema er namespace entity
         def entity worker { attributes: [] }`,
        'other/worker.ttr'
      ).ast!;
      table.upsertDocument('other/worker.ttr', astAnother, 'er', 'entity', 'other.pkg');

      const astSource = parseString(
        `package billing.app
         import billing.products.*
         schema er namespace entity
         def relation r { from: worker, to: worker }`,
        'billing/app/source.ttr'
      ).ast!;
      table.upsertDocument('billing/app/source.ttr', astSource, 'er', 'entity', 'billing.app');

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
        `schema cnc namespace role
         def role fact { description: "fact" }`,
        'stock://cnc-roles.ttr'
      ).ast!;
      table.upsertDocument('stock://cnc-roles.ttr', astStock, 'cnc', 'role', '');

      const astSource = parseString(
        `schema er namespace entity
         def entity artikl {
           nameAttribute: fact,
           attributes: []
         }`,
        'er.ttr'
      ).ast!;
      table.upsertDocument('er.ttr', astSource, 'er', 'entity', '');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'fact', parts: ['fact'] },
        { schemaCode: 'er', namespace: 'entity' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) {
        expect(res.viaStep).toBe('auto-import');
        expect(res.symbol.qname).toBe('cnc.cnc.role.fact');
      }
    });
  });

  describe('step 6: fully-qualified-but-unique', () => {
    it('resolves multi-part FQN ref via step 6 when no imports and unique across project', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.invoicing
         schema er namespace entity
         def entity artikl { attributes: [] }`,
        'billing/invoicing/artikl.ttr'
      ).ast!;
      table.upsertDocument('billing/invoicing/artikl.ttr', astTarget, 'er', 'entity', 'billing.invoicing');

      const astSource = parseString(
        `package billing.app
         schema er namespace entity
         def relation r { from: billing.invoicing.er.entity.artikl, to: billing.invoicing.er.entity.artikl }`,
        'billing/app/source.ttr'
      ).ast!;
      table.upsertDocument('billing/app/source.ttr', astSource, 'er', 'entity', 'billing.app');

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
         schema er namespace entity
         def entity artikl { attributes: [] }`,
        'billing/invoicing/artikl.ttr'
      ).ast!;
      table.upsertDocument('billing/invoicing/artikl.ttr', astTarget, 'er', 'entity', 'billing.invoicing');

      const astSource = parseString(
        `package billing.app
         schema er namespace entity
         def relation r { from: artikl, to: artikl }`,
        'billing/app/source.ttr'
      ).ast!;
      table.upsertDocument('billing/app/source.ttr', astSource, 'er', 'entity', 'billing.app');

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
         schema er namespace entity
         def entity thing { attributes: [] }`,
        'pkgA/a.ttr'
      ).ast!;
      table.upsertDocument('pkgA/a.ttr', astA, 'er', 'entity', 'pkgA');

      const astB = parseString(
        `package pkgB
         schema er namespace entity
         def entity thing { attributes: [] }`,
        'pkgB/b.ttr'
      ).ast!;
      table.upsertDocument('pkgB/b.ttr', astB, 'er', 'entity', 'pkgB');

      const astSource = parseString(
        `package app
         import pkgA.*
         import pkgB.*
         schema er namespace entity
         def relation r { from: thing, to: thing }`,
        'app/source.ttr'
      ).ast!;
      table.upsertDocument('app/source.ttr', astSource, 'er', 'entity', 'app');

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
        'er.ttr',
        `schema er namespace entity
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
        'er.ttr',
        `schema er namespace entity
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