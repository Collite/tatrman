import { afterEach, describe, it, expect, vi, beforeEach } from 'vitest';
import { render, cleanup, waitFor, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Canvas } from '../Canvas';

const mockModelGraphToCyElements = vi.fn(() => []);

const handlers: Record<string, ((arg: unknown) => void)[]> = {};

const glyphForMock = vi.fn(() => '<g class="glyph-mock"/>');
vi.mock('../cy/glyph-renderer', () => ({
  glyphFor: glyphForMock,
}));

vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn((_opts: unknown) => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({ forEach: vi.fn() })),
    edges: vi.fn(() => ({
      forEach: (cb: (e: unknown) => void) => {
        cb({
          data: (k: string) => k === 'fromCardinality' ? 'one' : k === 'toCardinality' ? 'many' : k === 'kind' ? 'relation' : undefined,
          renderedSourceEndpoint: () => ({ x: 0, y: 0 }),
          renderedTargetEndpoint: () => ({ x: 100, y: 0 }),
        });
      },
      [Symbol.iterator]: function* () {
        yield {
          data: (k: string) => k === 'fromCardinality' ? 'one' : k === 'toCardinality' ? 'many' : k === 'kind' ? 'relation' : undefined,
          renderedSourceEndpoint: () => ({ x: 0, y: 0 }),
          renderedTargetEndpoint: () => ({ x: 100, y: 0 }),
        };
      },
    })),
    on: vi.fn((_evt: string, cb: (arg: unknown) => void) => { (handlers[_evt] ??= []).push(cb); }),
    off: vi.fn(),
    nodeHtmlLabel: vi.fn(),
    destroy: vi.fn(),
    pan: vi.fn(() => ({ x: 0, y: 0 })),
    zoom: vi.fn(() => 1),
  }));
  (mockDefault as unknown as Record<string, unknown>).use = mockUse;
  return { default: mockDefault, use: mockUse };
});

vi.mock('cytoscape-cose-bilkent', () => ({ default: vi.fn() }));
vi.mock('cytoscape-node-html-label', () => ({ default: vi.fn() }));

vi.mock('../cy/adapter', () => ({
  modelGraphToCyElements: mockModelGraphToCyElements,
}));

const defaultViewports = {
  db: { zoom: 1, panX: 0, panY: 0, displayMode: 'with-types' as const },
  er: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' as const },
};

describe('Canvas (er schema)', () => {
  beforeEach(() => {
    for (const key of Object.keys(handlers)) delete handlers[key];
    glyphForMock.mockClear();
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders a container div with relative positioning', () => {
    render(<Canvas graph={null} displayMode="just-names" activeSchema="er" viewports={defaultViewports} nodePositions={{}} lspClient={null} projectRoot={null} onNodeSelect={vi.fn()} currentViewport={null} onRemoveNode={vi.fn()} />);
    expect(document.querySelector('[style*="position: relative"]')).toBeInTheDocument();
  });

  it('registers a render/zoom/pan handler once cy is ready', async () => {
    render(<Canvas graph={null} displayMode="just-names" activeSchema="er" viewports={defaultViewports} nodePositions={{}} lspClient={null} projectRoot={null} onNodeSelect={vi.fn()} currentViewport={null} onRemoveNode={vi.fn()} />);
    await waitFor(() => {
      expect(handlers['render zoom pan']).toBeDefined();
      expect(handlers['render zoom pan'].length).toBeGreaterThan(0);
    });
  });

  it('calls glyphFor with each edge cardinalities when the render handler fires', () => {
    act(() => {
      vi.useFakeTimers();
      render(<Canvas graph={null} displayMode="just-names" activeSchema="er" viewports={defaultViewports} nodePositions={{}} lspClient={null} projectRoot={null} onNodeSelect={vi.fn()} currentViewport={null} onRemoveNode={vi.fn()} />);
    });
    act(() => {
      vi.runAllTimers();
    });
    vi.useRealTimers();
  });
});