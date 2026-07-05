import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { buildModelGraph, buildProjectModelGraph } from '../model-graph.js';

describe('buildModelGraph (db schema)', () => {
  it('2-table fixture with 1 FK: returns 2 nodes, 1 edge, edge from/to match node qnames', () => {
    const content = `
model db schema dbo
def table orders { columns: [def column id { type: int, isKey: true }] }
def table items {
  columns: [def column id { type: int, isKey: true }, def column order_id { type: int }]
}
def fk items_order { from: [orders.id], to: [items.order_id] }
`;
    const result = parseString(content, 'file:///fixture.ttrm');
    const graph = buildModelGraph(result.ast!, 'db');
    expect(graph.schemaCode).toBe('db');
    expect(graph.nodes).toHaveLength(2);
    expect(graph.edges).toHaveLength(1);
    const orderNode = graph.nodes.find(n => n.name === 'orders')!;
    const itemsNode = graph.nodes.find(n => n.name === 'items')!;
    expect(orderNode).toBeDefined();
    expect(itemsNode).toBeDefined();
    expect(graph.edges[0].fromNode).toBe(orderNode.qname);
    expect(graph.edges[0].toNode).toBe(itemsNode.qname);
    expect(graph.nodes[0].rows.length).toBeGreaterThan(0);
  });

  it('1-table with simple-type column renders type as int', () => {
    const content = `
model db schema dbo
def table products {
  columns: [def column id { type: int, isKey: true }, def column name { type: text }]
}
`;
    const result = parseString(content, 'file:///fixture.ttrm');
    const graph = buildModelGraph(result.ast!, 'db');
    const nameRow = graph.nodes[0].rows.find(r => r.name === 'name')!;
    expect(nameRow.type).toBe('text');
  });

  it('unsupported schema returns empty graph', () => {
    const content = `model cnc schema role`;
    const result = parseString(content, 'file:///fixture.ttrm');
    const graph = buildModelGraph(result.ast!, 'cnc');
    expect(graph.schemaCode).toBe('cnc');
    expect(graph.nodes).toHaveLength(0);
    expect(graph.edges).toHaveLength(0);
  });
});

describe('buildModelGraph (er schema)', () => {
  it('1-entity fixture with 2 attributes: 1 node with rows.length === 2, 0 edges', () => {
    const content = `
model er schema entity
def entity artikl {
  attributes: [
    def attribute id { type: int, isKey: true },
    def attribute nazev { type: text }
  ]
}
`;
    const result = parseString(content, 'file:///fixture.ttrm');
    const graph = buildModelGraph(result.ast!, 'er');
    expect(graph.schemaCode).toBe('er');
    expect(graph.nodes).toHaveLength(1);
    expect(graph.nodes[0].rows).toHaveLength(2);
    expect(graph.edges).toHaveLength(0);
  });

  it('entity with displayLabel honors preferredLanguage', () => {
    const content = `model er schema entity def entity foo {
      displayLabel: { cs: "Artikl", en: "Item" },
      attributes: [def attribute id { type: int }]
    }`;
    const ast = parseString(content, 'file:///x.ttrm').ast!;
    expect(buildModelGraph(ast, 'er', 'cs').nodes[0].label).toBe('Artikl');
    expect(buildModelGraph(ast, 'er', 'de').nodes[0].label).toBe('foo');
    expect(buildModelGraph(ast, 'er', 'en').nodes[0].label).toBe('Item');
    expect(buildModelGraph(ast, 'er', 'fr').nodes[0].label).toBe('foo');
  });

  it('entity with nameAttribute marks the row', () => {
    const content = `model er schema entity def entity foo {
      attributes: [def attribute id { type: int }, def attribute label { type: text }],
      nameAttribute: label
    }`;
    const node = buildModelGraph(parseString(content, 'file:///x.ttrm').ast!, 'er').nodes[0];
    expect(node.rows.find(r => r.name === 'label')!.isNameAttribute).toBe(true);
    expect(node.rows.find(r => r.name === 'id')!.isNameAttribute).toBe(false);
  });
});

describe('buildProjectModelGraph (multi-document)', () => {
  it('2 ASTs with 1 entity each returns 2 nodes', () => {
    const ast1 = parseString(`model er schema entity def entity foo { attributes: [def attribute id { type: int }] }`, 'file:///p/x.ttrm').ast!;
    const ast2 = parseString(`model er schema entity def entity bar { attributes: [def attribute id { type: int }] }`, 'file:///p/y.ttrm').ast!;
    const graph = buildProjectModelGraph([ast1, ast2], 'er');
    expect(graph.nodes).toHaveLength(2);
    expect(graph.edges).toHaveLength(0);
  });

  it('cross-document FK resolves when def is in a different AST', () => {
    const ast1 = parseString(`model db schema dbo def table orders { columns: [def column id { type: int, isKey: true }] }`, 'file:///p/orders.ttrm').ast!;
    const ast2 = parseString(`model db schema dbo def table items { columns: [def column id { type: int, isKey: true }, def column order_id { type: int }] } def fk items_order { from: [orders.id], to: [items.order_id] }`, 'file:///p/items.ttrm').ast!;
    const graph = buildProjectModelGraph([ast1, ast2], 'db');
    expect(graph.nodes).toHaveLength(2);
    expect(graph.edges).toHaveLength(1);
    const orderNode = graph.nodes.find(n => n.name === 'orders')!;
    const itemsNode = graph.nodes.find(n => n.name === 'items')!;
    expect(graph.edges[0].fromNode).toBe(orderNode.qname);
    expect(graph.edges[0].toNode).toBe(itemsNode.qname);
  });
});