// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { computeGraphEdges } from '../model-graph.js';

function graphBlock(name: string, schema: string, objects: string[]) {
  return {
    kind: 'graphBlock' as const,
    name,
    schema: schema as 'db' | 'er' | 'binding' | 'query' | 'cnc',
    objects,
    source: { file: 'test.ttrg', line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
  };
}

describe('C1 — computeGraphEdges edge-inclusion rule', () => {
  function setupAsts(content: string): import('@tatrman/parser').Document[] {
    const result = parseString(content, 'test.ttrm');
    return result.ast ? [result.ast] : [];
  }

  it('objects: [A, B, R] where R is a relation from A to B → 1 edge', () => {
    const asts = setupAsts(`
      model er schema entity
      def entity a { attributes: [def attribute id { type: int }] }
      def entity b { attributes: [def attribute id { type: int }] }
      def relation r { from: er.entity.a, to: er.entity.b }
    `);
    const g = graphBlock('test', 'er', ['er.entity.a', 'er.entity.b', 'er.relation.r']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(1);
    expect(edges[0].fromNode).toBe('er.entity.a');
    expect(edges[0].toNode).toBe('er.entity.b');
    expect(edges[0].kind).toBe('relation');
  });

  it('objects: [A, B] (relation R omitted) → 0 edges', () => {
    const asts = setupAsts(`
      model er schema entity
      def entity a { attributes: [def attribute id { type: int }] }
      def entity b { attributes: [def attribute id { type: int }] }
      def relation r { from: er.entity.a, to: er.entity.b }
    `);
    const g = graphBlock('test', 'er', ['er.entity.a', 'er.entity.b']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(0);
  });

  it('objects: [A, R] (B omitted) → 0 edges (edge needs both endpoints)', () => {
    const asts = setupAsts(`
      model er schema entity
      def entity a { attributes: [def attribute id { type: int }] }
      def entity b { attributes: [def attribute id { type: int }] }
      def relation r { from: er.entity.a, to: er.entity.b }
    `);
    const g = graphBlock('test', 'er', ['er.entity.a', 'er.relation.r']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(0);
  });

  it('objects: [A, B, R, FK] with FK from A to B → 2 edges', () => {
    const asts = setupAsts(`
      model db schema dbo
      def table a { columns: [def column id { type: int, isKey: true }] }
      def table b { columns: [def column id { type: int, isKey: true }, def column a_id { type: int }] }
      def fk fk_a_b { from: [a.id], to: [b.a_id] }
      def relation rel_a_b { from: db.dbo.a, to: db.dbo.b }
    `);
    const g = graphBlock('test', 'db', ['db.dbo.table.a', 'db.dbo.table.b', 'db.dbo.fk.fk_a_b', 'er.relation.rel_a_b']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(2);
    expect(edges.map((e) => e.kind).sort()).toEqual(['fk', 'relation']);
  });

  it('empty objects → 0 edges', () => {
    const asts = setupAsts(`
      model er schema entity
      def entity a { attributes: [def attribute id { type: int }] }
    `);
    const g = graphBlock('test', 'er', []);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(0);
  });

  it('objects with FK but endpoint not in objects → 0 edges', () => {
    const asts = setupAsts(`
      model db schema dbo
      def table a { columns: [def column id { type: int, isKey: true }] }
      def table b { columns: [def column id { type: int, isKey: true }, def column a_id { type: int }] }
      def fk fk_a_b { from: [a.id], to: [b.a_id] }
    `);
    const g = graphBlock('test', 'db', ['db.dbo.table.a', 'db.dbo.fk.fk_a_b']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(0);
  });

  it('relation with cardinality extracted correctly', () => {
    const asts = setupAsts(`
      model er schema entity
      def entity a { attributes: [def attribute id { type: int }] }
      def entity b { attributes: [def attribute id { type: int }] }
      def relation r { from: er.entity.a, to: er.entity.b, cardinality: { from: "1", to: "n" } }
    `);
    const g = graphBlock('test', 'er', ['er.entity.a', 'er.entity.b', 'er.relation.r']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(1);
    expect(edges[0].fromCardinality).toBe('one');
    expect(edges[0].toCardinality).toBe('many');
  });

  it('FK with bare-id from/to (no brackets) produces an edge', () => {
    const asts = setupAsts(`
      model db schema dbo
      def table a { columns: [def column id { type: int, isKey: true }] }
      def table b { columns: [def column id { type: int, isKey: true }, def column a_id { type: int }] }
      def fk fk_a_b { from: a.id, to: b.a_id }
    `);
    const g = graphBlock('test', 'db', ['db.dbo.table.a', 'db.dbo.table.b', 'db.dbo.fk.fk_a_b']);
    const edges = computeGraphEdges(g, asts);
    expect(edges).toHaveLength(1);
    expect(edges[0].kind).toBe('fk');
  });
});