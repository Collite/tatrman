import { describe, it, expect } from 'vitest';
import { buildLayout, applyPositions, type CyShim } from '../save-layout';
import type { ViewportState } from '@modeler/lsp';

describe('layout-round-trip', () => {
  describe('buildLayout', () => {
    it('returns current viewport with updated zoom/pan', () => {
      const mockCy: CyShim = {
        nodes: () => [],
        pan: () => ({ x: 50, y: -20 }),
        zoom: () => 1.5,
      };

      const result = buildLayout(mockCy, null, 'just-names');

      expect(result.viewport.zoom).toBe(1.5);
      expect(result.viewport.panX).toBe(50);
      expect(result.viewport.panY).toBe(-20);
      expect(result.viewport.displayMode).toBe('just-names');
    });

    it('maps every cy node qname to its position', () => {
      const mockNode1 = { position: () => ({ x: 10, y: 20 }), data: (_k: string) => 'er.entity.artikl' };
      const mockNode2 = { position: () => ({ x: 30, y: 40 }), data: (_k: string) => 'er.entity.other' };
      const mockCy: CyShim = {
        nodes: () => [mockNode1 as unknown as ReturnType<CyShim['nodes']>[0], mockNode2 as unknown as ReturnType<CyShim['nodes']>[0]],
        pan: () => ({ x: 0, y: 0 }),
        zoom: () => 1,
      };

      const result = buildLayout(mockCy, null, 'just-names');

      expect(result.nodes['er.entity.artikl']).toEqual({ x: 10, y: 20 });
      expect(result.nodes['er.entity.other']).toEqual({ x: 30, y: 40 });
    });

    it('uses currentViewport displayMode when displayMode arg is not provided', () => {
      const mockCy: CyShim = {
        nodes: () => [],
        pan: () => ({ x: 0, y: 0 }),
        zoom: () => 1,
      };
      const existingViewport: ViewportState = { zoom: 1, panX: 0, panY: 0, displayMode: 'with-types' };

      const result = buildLayout(mockCy, existingViewport, 'just-names');

      expect(result.viewport.displayMode).toBe('just-names');
    });
  });

  describe('applyPositions', () => {
    it('ignores unknown qnames (F-6 regression)', () => {
      const positionCalls: Array<{ qname: string; pos: { x: number; y: number } }> = [];
      const mockCy = {
        getElementById: (qname: string) => {
          if (qname === 'er.entity.ghost') return { length: 0 };
          return {
            length: 1,
            position: (pos: { x: number; y: number }) => {
              positionCalls.push({ qname, pos });
            },
          };
        },
      };

      applyPositions(mockCy as unknown as Parameters<typeof applyPositions>[0], {
        'er.entity.artikl': { x: 42, y: 99 },
        'er.entity.ghost': { x: 0, y: 0 },
      });

      expect(positionCalls).toHaveLength(1);
      expect(positionCalls[0].qname).toBe('er.entity.artikl');
      expect(positionCalls[0].pos).toEqual({ x: 42, y: 99 });
    });
  });
});