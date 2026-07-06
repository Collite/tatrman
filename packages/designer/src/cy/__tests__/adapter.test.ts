import { describe, it, expect } from 'vitest';
import type { ModelGraph, ModelGraphNode } from '@tatrman/lsp';
import { modelGraphToCyElements } from '../adapter';

function makeNode(overrides: Partial<ModelGraphNode> = {}): ModelGraphNode {
  return {
    qname: 'db.dbo.foo',
    kind: 'table',
    name: 'foo',
    schemaCode: 'db',
    label: 'Foo',
    sourceUri: 'file:///x.ttrm',
    sourceLocation: { line: 1, column: 0 },
    rows: [],
    ...overrides,
  };
}

function makeGraph(nodes: ModelGraphNode[], edges: ModelGraph['edges'] = []): ModelGraph {
  return { schemaCode: 'db', nodes, edges };
}

describe('modelGraphToCyElements', () => {
  it('empty graph returns empty elements', () => {
    expect(modelGraphToCyElements(makeGraph([]), 'just-names')).toEqual([]);
  });

  it('one node with no rows produces one node element', () => {
    const graph = makeGraph([makeNode({ qname: 'db.dbo.bar', name: 'bar', label: 'bar', rows: [] })]);
    const els = modelGraphToCyElements(graph, 'just-names');
    expect(els).toHaveLength(1);
    expect(els[0]).toEqual({
      group: 'nodes',
      data: expect.objectContaining({ qname: 'db.dbo.bar', kind: 'table', label: 'bar' }),
    });
    // labelHtml always contains the node title; with zero rows the row block is omitted.
    const html = (els[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('cy-node-title');
    expect(html).toContain('bar');
    expect(html).not.toContain('cy-rows');
  });

  it('just-names includes only row names', () => {
    const graph = makeGraph([
      makeNode({
        rows: [
          { name: 'id', qname: 'db.dbo.foo.id', kind: 'column', type: 'int', isKey: true, optional: false, isNameAttribute: false, isCodeAttribute: false },
          { name: 'name', qname: 'db.dbo.foo.name', kind: 'column', type: 'varchar(40)', isKey: false, optional: true, isNameAttribute: false, isCodeAttribute: false },
        ],
      }),
    ]);
    const html = (modelGraphToCyElements(graph, 'just-names')[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('id');
    expect(html).not.toContain('int');
    expect(html).not.toContain('varchar');
  });

  it('with-types includes row names and types', () => {
    const graph = makeGraph([
      makeNode({
        rows: [
          { name: 'id', qname: 'db.dbo.foo.id', kind: 'column', type: 'int', isKey: true, optional: false, isNameAttribute: false, isCodeAttribute: false },
          { name: 'name', qname: 'db.dbo.foo.name', kind: 'column', type: 'varchar(40)', isKey: false, optional: true, isNameAttribute: false, isCodeAttribute: false },
        ],
      }),
    ]);
    const html = (modelGraphToCyElements(graph, 'with-types')[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('id');
    expect(html).toContain('int');
    expect(html).toContain('name');
    expect(html).toContain('varchar(40)');
  });

  it('with-constraints shows PK/NN badges', () => {
    const graph = makeGraph([
      makeNode({
        rows: [
          { name: 'idx', qname: 'db.dbo.foo.idx', kind: 'column', type: 'int', isKey: true, optional: false, isNameAttribute: false, isCodeAttribute: false },
          { name: 'name', qname: 'db.dbo.foo.name', kind: 'column', type: 'varchar(40)', isKey: false, optional: false, isNameAttribute: false, isCodeAttribute: false },
          { name: 'opt', qname: 'db.dbo.foo.opt', kind: 'column', type: 'int', isKey: false, optional: true, isNameAttribute: false, isCodeAttribute: false },
        ],
      }),
    ]);
    const html = (modelGraphToCyElements(graph, 'with-constraints')[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('PK');
    expect(html).toContain('NN');
    expect(html).toContain('idx');
    expect(html).toContain('name');
    expect(html).toContain('opt');
    expect(html).toContain('int');
    expect(html).toContain('varchar(40)');
    expect(html).not.toContain('isKey');
    expect(html).not.toContain('optional');
  });

  it('one edge produces one edge element', () => {
    const graph = makeGraph(
      [
        makeNode({ qname: 'db.dbo.t1', name: 't1', label: 'T1' }),
        makeNode({ qname: 'db.dbo.t2', name: 't2', label: 'T2' }),
      ],
      [
        {
          id: 'db.dbo.fk1',
          qname: 'db.dbo.fk1',
          kind: 'fk',
          fromNode: 'db.dbo.t1',
          toNode: 'db.dbo.t2',
          fromCardinality: null,
          toCardinality: null,
          sourceUri: 'file:///x.ttrm',
          sourceLocation: { line: 1, column: 0 },
        },
      ]
    );
    const els = modelGraphToCyElements(graph, 'just-names');
    expect(els).toHaveLength(3);
    const edgeEl = els.find((e) => e.group === 'edges')!;
    expect(edgeEl.data).toEqual(expect.objectContaining({
      id: 'db.dbo.fk1',
      kind: 'fk',
      source: 'db.dbo.t1',
      target: 'db.dbo.t2',
    }));
  });

  it('escapes < > & in row names and types', () => {
    const graph = makeGraph([
      makeNode({ rows: [{ name: 'a<b>', qname: 'db.dbo.t.a', kind: 'column', type: 'int&', isKey: false, optional: true, isNameAttribute: false, isCodeAttribute: false }] }),
    ]);
    const html = (modelGraphToCyElements(graph, 'with-types')[0].data as Record<string, unknown>).labelHtml as string;
    expect(html).toContain('a&lt;b&gt;');
    expect(html).toContain('int&amp;');
    expect(html).not.toContain('<b>');
  });
});