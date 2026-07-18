// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  bindingGenerator, lineageGenerator, NotImplementedYet,
  type PerspectiveResult, type LineageQuery,
} from '../index.js';

describe('perspective generator contracts (contracts §4)', () => {
  it('the v1 roster is exactly binding + lineage, keyed by id', () => {
    expect(bindingGenerator.id).toBe('binding');
    expect(lineageGenerator.id).toBe('lineage');
  });

  it('both generators expose a generate() function', () => {
    expect(typeof bindingGenerator.generate).toBe('function');
    expect(typeof lineageGenerator.generate).toBe('function');
  });

  it('both generators are implemented (DS-P4); a malformed lineage input still fails fast', () => {
    // binding returns a ribbon result over empty inputs.
    const eb = bindingGenerator.generate({ er: { nodes: [] }, db: { nodes: [] }, bindings: { entities: [], attributes: [] } });
    expect(eb.kind).toBe('custom');
    // lineage returns a layers result over a (valid, empty) composed model.
    const q: LineageQuery = { root: { qname: 'md.Sales.net_amount', kind: 'measure' }, scope: 'neighborhood', direction: 'upstream' };
    const el = lineageGenerator.generate({ query: q, model: { objects: [], links: [] } });
    expect(el.kind).toBe('custom');
    // a malformed input (no composed model) fails fast with the shape guard.
    const bad = { query: q } as unknown as Parameters<typeof lineageGenerator.generate>[0];
    expect(() => lineageGenerator.generate(bad)).toThrow(NotImplementedYet);
    expect(() => lineageGenerator.generate(bad)).toThrow(/DS-P4/);
  });

  it('PerspectiveResult is a discriminated union of canvas | custom (type-level, exercised)', () => {
    const canvasResult: PerspectiveResult = {
      kind: 'canvas',
      graph: { id: 'g', face: 'modeling', nodes: [], edges: [], containers: [] },
    };
    const customResult: PerspectiveResult = { kind: 'custom', view: 'binding-ribbon', data: { rows: [] } };
    expect(canvasResult.kind).toBe('canvas');
    expect(customResult.kind).toBe('custom');
    if (customResult.kind === 'custom') expect(customResult.view).toBe('binding-ribbon');
  });

  it('lineage scope + direction vocabularies are the C-3/C-4 sets', () => {
    const scopes: LineageQuery['scope'][] = ['column', 'neighborhood', 'fullPath'];
    const dirs: LineageQuery['direction'][] = ['upstream', 'downstream'];
    expect(scopes).toContain('neighborhood'); // default
    expect(dirs).toContain('downstream'); // impact
  });
});
