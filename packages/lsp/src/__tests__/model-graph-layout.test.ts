// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { emptyLayout, validateLayout } from '../model-graph';
import type { LayoutFile } from '../model-graph';

describe('validateLayout / emptyLayout', () => {
  it('5.2a validateLayout(emptyLayout()) returns unchanged', () => {
    const layout = emptyLayout();
    expect(validateLayout(layout)).toBe(layout);
  });

  it('5.2b validateLayout({}) returns null', () => {
    expect(validateLayout({})).toBe(null);
  });

  it('5.2c validateLayout({...emptyLayout(), version:2}) returns null', () => {
    const layout = { ...emptyLayout(), version: 2 as const };
    expect(validateLayout(layout)).toBe(null);
  });

  it('5.2d validateLayout with wrong node type returns null', () => {
    const layout = {
      ...emptyLayout(),
      nodes: { foo: { x: 'bad' as unknown as number, y: 0 } },
    };
    expect(validateLayout(layout)).toBe(null);
  });

  it('5.2e round-trip with non-empty nodes and edges', () => {
    const layout: LayoutFile = {
      version: 1,
      viewport: { zoom: 1.5, panX: 100, panY: 200, displayMode: 'with-types' },
      nodes: { 'er.entity.artikl': { x: 42, y: 99 } },
      edges: { 'er.rel.foo': { bendPoints: [[10, 20], [30, 40]] as Array<[number, number]> } },
    };
    expect(validateLayout(layout)).toEqual(layout);
  });

  it('5.2f validateLayout does not throw on null', () => {
    expect(validateLayout(null)).toBe(null);
  });

  it('5.2f validateLayout does not throw on undefined', () => {
    expect(validateLayout(undefined)).toBe(null);
  });

  it('5.2f validateLayout does not throw on string', () => {
    expect(validateLayout('not an object')).toBe(null);
  });

  it('5.2f validateLayout does not throw on number', () => {
    expect(validateLayout(42)).toBe(null);
  });
});