import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { PackageGraphBuilder } from '../package-graph.js';
import type { Document } from '@modeler/parser';

function makeDocuments(asts: Array<{ uri: string; src: string }>): Map<string, Document> {
  const map = new Map<string, Document>();
  for (const { uri, src } of asts) {
    const result = parseString(src, uri);
    if (result.ast) map.set(uri, result.ast);
  }
  return map;
}

describe('PackageGraphBuilder', () => {
  describe('three-package project A→B→C', () => {
    it('build() returns 3 nodes and 2 edges', () => {
      const table = new ProjectSymbolTable();
      const docs = makeDocuments([
        {
          uri: 'pkgA/a.ttrm',
          src: `package pkgA\nschema er namespace entity\ndef entity a { attributes: [] }`,
        },
        {
          uri: 'pkgB/b.ttrm',
          src: `package pkgB\nimport pkgA.*\nschema er namespace entity\ndef entity b { attributes: [] }`,
        },
        {
          uri: 'pkgC/c.ttrm',
          src: `package pkgC\nimport pkgB.*\nschema er namespace entity\ndef entity c { attributes: [] }`,
        },
      ]);

      for (const [uri, doc] of docs) {
        const schemaCode = doc.schemaDirective?.schemaCode ?? 'db';
        const namespace = doc.schemaDirective?.namespace ?? '';
        const packageName = doc.packageDecl?.name ?? '';
        table.upsertDocument(uri, doc, schemaCode, namespace, packageName);
      }

      const builder = new PackageGraphBuilder(table, docs);
      const graph = builder.build();

      expect(graph.nodes).toHaveLength(3);
      expect(graph.edges).toHaveLength(2);
    });

    it('getDependencies(C) includes B and A (transitive)', () => {
      const table = new ProjectSymbolTable();
      const docs = makeDocuments([
        {
          uri: 'pkgA/a.ttrm',
          src: `package pkgA\nschema er namespace entity\ndef entity a { attributes: [] }`,
        },
        {
          uri: 'pkgB/b.ttrm',
          src: `package pkgB\nimport pkgA.*\nschema er namespace entity\ndef entity b { attributes: [] }`,
        },
        {
          uri: 'pkgC/c.ttrm',
          src: `package pkgC\nimport pkgB.*\nschema er namespace entity\ndef entity c { attributes: [] }`,
        },
      ]);

      for (const [uri, doc] of docs) {
        const schemaCode = doc.schemaDirective?.schemaCode ?? 'db';
        const namespace = doc.schemaDirective?.namespace ?? '';
        const packageName = doc.packageDecl?.name ?? '';
        table.upsertDocument(uri, doc, schemaCode, namespace, packageName);
      }

      const builder = new PackageGraphBuilder(table, docs);
      const deps = builder.getDependencies('pkgC');
      expect(deps).toContain('pkgB');
      expect(deps).toContain('pkgA');
    });

    it('getDependents(A) includes B (direct), and B dependents include C (transitive)', () => {
      const table = new ProjectSymbolTable();
      const docs = makeDocuments([
        {
          uri: 'pkgA/a.ttrm',
          src: `package pkgA\nschema er namespace entity\ndef entity a { attributes: [] }`,
        },
        {
          uri: 'pkgB/b.ttrm',
          src: `package pkgB\nimport pkgA.*\nschema er namespace entity\ndef entity b { attributes: [] }`,
        },
        {
          uri: 'pkgC/c.ttrm',
          src: `package pkgC\nimport pkgB.*\nschema er namespace entity\ndef entity c { attributes: [] }`,
        },
      ]);

      for (const [uri, doc] of docs) {
        const schemaCode = doc.schemaDirective?.schemaCode ?? 'db';
        const namespace = doc.schemaDirective?.namespace ?? '';
        const packageName = doc.packageDecl?.name ?? '';
        table.upsertDocument(uri, doc, schemaCode, namespace, packageName);
      }

      const builder = new PackageGraphBuilder(table, docs);
      const dependentsOfA = builder.getDependents('pkgA');
      expect(dependentsOfA).toContain('pkgB');

      const dependentsOfB = builder.getDependents('pkgB');
      expect(dependentsOfB).toContain('pkgC');
    });
  });

  describe('cycle A→B→A', () => {
    it('findCycles() returns [[A, B]]', () => {
      const table = new ProjectSymbolTable();
      const docs = makeDocuments([
        {
          uri: 'pkgA/a.ttrm',
          src: `package pkgA\nimport pkgB.*\nschema er namespace entity\ndef entity a { attributes: [] }`,
        },
        {
          uri: 'pkgB/b.ttrm',
          src: `package pkgB\nimport pkgA.*\nschema er namespace entity\ndef entity b { attributes: [] }`,
        },
      ]);

      for (const [uri, doc] of docs) {
        const schemaCode = doc.schemaDirective?.schemaCode ?? 'db';
        const namespace = doc.schemaDirective?.namespace ?? '';
        const packageName = doc.packageDecl?.name ?? '';
        table.upsertDocument(uri, doc, schemaCode, namespace, packageName);
      }

      const builder = new PackageGraphBuilder(table, docs);
      const cycles = builder.findCycles();
      expect(cycles).toHaveLength(1);
      const cycle = cycles[0];
      expect(cycle.length).toBe(2);
      expect(new Set(cycle)).toEqual(new Set(['pkgA', 'pkgB']));
    });
  });

  describe('self-import (package A imports package A)', () => {
    it('is NOT treated as a cycle', () => {
      const table = new ProjectSymbolTable();
      const docs = makeDocuments([
        {
          uri: 'pkgA/a.ttrm',
          src: `package pkgA\nimport pkgA.*\nschema er namespace entity\ndef entity a { attributes: [] }`,
        },
      ]);

      for (const [uri, doc] of docs) {
        const schemaCode = doc.schemaDirective?.schemaCode ?? 'db';
        const namespace = doc.schemaDirective?.namespace ?? '';
        const packageName = doc.packageDecl?.name ?? '';
        table.upsertDocument(uri, doc, schemaCode, namespace, packageName);
      }

      const builder = new PackageGraphBuilder(table, docs);
      const cycles = builder.findCycles();
      expect(cycles).toHaveLength(0);
    });
  });
});