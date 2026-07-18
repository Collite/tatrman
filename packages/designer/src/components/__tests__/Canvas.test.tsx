import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Canvas } from '../Canvas';

const mockModelGraphToCyElements = vi.fn(() => []);

vi.mock('cytoscape', () => {
  const mockUse = vi.fn();
  const mockDefault = vi.fn((_opts: unknown) => ({
    elements: vi.fn(() => ({ remove: vi.fn().mockReturnThis() })),
    add: vi.fn().mockReturnThis(),
    layout: vi.fn(() => ({ run: vi.fn() })),
    nodes: vi.fn(() => ({ forEach: vi.fn() })),
    on: vi.fn(),
    nodeHtmlLabel: vi.fn(),
    destroy: vi.fn(),
  }));
  (mockDefault as unknown as Record<string, unknown>).use = mockUse;
  return { default: mockDefault, use: mockUse };
});

vi.mock('cytoscape-cose-bilkent', () => ({ default: vi.fn() }));
vi.mock('cytoscape-node-html-label', () => ({ default: vi.fn() }));

vi.mock('../cy/adapter', () => ({
  modelGraphToCyElements: mockModelGraphToCyElements,
}));

describe('Canvas', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders a container div', () => {
    render(<Canvas graph={null} displayMode="just-names" activeSchema="er" viewports={{ db: { zoom: 1, panX: 0, panY: 0, displayMode: 'with-types' }, er: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' } }} nodePositions={{}} lspClient={null} projectRoot={null} onNodeSelect={vi.fn()} currentViewport={null} />);
    expect(document.querySelector('.bg-white')).toBeInTheDocument();
  });
});