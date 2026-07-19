import { describe, it, expect } from 'vitest';
import type { ModelGraph } from '@tatrman/lsp';
import { modelGraphToCanvas } from '@tatrman/canvas-core';
import { applyGraphFixtures, HERO_FIXTURES } from '../graph-fixtures.js';

// DS-P3.S1.T4 — the fixture channel merges logged grammar gaps into slotData (never the LSP).
const mdGraph: ModelGraph = {
  schemaCode: 'md',
  nodes: [
    { qname: 'orders_hero.md.cubelet.Sales', kind: 'cubelet', name: 'Sales', schemaCode: 'md', label: 'Sales', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] },
    { qname: 'orders_hero.md.dimension.Customer', kind: 'dimension', name: 'Customer', schemaCode: 'md', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 2, column: 0 }, rows: [] },
  ],
  edges: [],
};

describe('applyGraphFixtures', () => {
  it('lands the cube margin_pct calc into slotExtra (the derived-measure gap fill)', () => {
    const fixtured = applyGraphFixtures(mdGraph);
    const cube = fixtured.nodes.find((n) => n.name === 'Sales')! as { slotExtra?: { calcs?: string[] } };
    expect(cube.slotExtra?.calcs).toEqual(['margin_pct']);
  });

  it('leaves un-fixtured nodes untouched (no slotExtra)', () => {
    const fixtured = applyGraphFixtures(mdGraph);
    const dim = fixtured.nodes.find((n) => n.name === 'Customer')! as { slotExtra?: unknown };
    expect(dim.slotExtra).toBeUndefined();
  });

  it('the fill reaches CanvasNode.slotData through the mapper (skins read it there)', () => {
    const canvas = modelGraphToCanvas(applyGraphFixtures(mdGraph));
    const cube = canvas.nodes.find((n) => n.qname === 'orders_hero.md.cubelet.Sales')!;
    expect((cube.slotData as { calcs?: string[] }).calcs).toEqual(['margin_pct']);
  });

  it('every hero fixture key is a logged gap fill (calcs | role | synonyms only)', () => {
    for (const fx of Object.values(HERO_FIXTURES)) {
      const keys = Object.keys(fx);
      expect(keys.every((k) => k === 'calcs' || k === 'role' || k === 'synonyms')).toBe(true);
    }
  });
});
