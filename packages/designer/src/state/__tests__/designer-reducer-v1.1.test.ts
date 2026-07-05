import { describe, it, expect } from 'vitest';
import { designerReducer } from '../designer-reducer';
import { initialDesignerState, type DesignerState } from '../designer-state';
import type { GetGraphResponse, GraphMetadata, GraphLayoutOutput } from '@tatrman/lsp';

const mockGraph = (overrides: Partial<GetGraphResponse> = {}): GetGraphResponse => ({
  schema: 'er',
  nodes: [],
  edges: [],
  layout: { viewport: { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' }, nodes: {}, edges: {} } as GraphLayoutOutput,
  missingObjects: [],
  imports: [],
  ...overrides,
});

const mockGraphMetadata = (name: string, schema: 'er' | 'db' = 'er'): GraphMetadata => ({
  name,
  uri: `file:///project/graphs/${name}.ttrg`,
  schema,
  tags: [],
  objectCount: 0,
  missingObjectCount: 0,
});

describe('designerReducer — v1.1 graph lifecycle', () => {
  it('storeGraphList populates availableGraphs', () => {
    const graphs = [mockGraphMetadata('a'), mockGraphMetadata('b', 'db')];
    const state = designerReducer(initialDesignerState, { type: 'storeGraphList', graphs });
    expect(state.availableGraphs).toEqual(graphs);
  });

  it('openGraph sets currentGraphUri and clears nodePositions', () => {
    const withPositions: DesignerState = {
      ...initialDesignerState,
      nodePositions: { 'er.entity.foo': { x: 100, y: 200 } },
    };
    const state = designerReducer(withPositions, {
      type: 'openGraph',
      graphUri: 'file:///proj/graphs/a.ttrg',
    });
    expect(state.currentGraphUri).toBe('file:///proj/graphs/a.ttrg');
    expect(state.nodePositions).toEqual({});
  });

  it('openGraph clears currentGraph', () => {
    const withGraph: DesignerState = {
      ...initialDesignerState,
      currentGraph: mockGraph({ nodes: [{ qname: 'er.entity.artikl', kind: 'entity' as const, name: 'artikl', label: 'artikl', schemaCode: 'er', sourceUri: '', sourceLocation: { line: 1, column: 1 }, rows: [] }] }),
    };
    const state = designerReducer(withGraph, {
      type: 'openGraph',
      graphUri: 'file:///proj/graphs/a.ttrg',
    });
    expect(state.currentGraph).toBeNull();
  });

  it('closeGraph clears currentGraphUri and currentGraph', () => {
    const state: DesignerState = {
      ...initialDesignerState,
      currentGraphUri: 'file:///proj/graphs/a.ttrg',
      currentGraph: mockGraph(),
    };
    const next = designerReducer(state, { type: 'closeGraph' });
    expect(next.currentGraphUri).toBeNull();
    expect(next.currentGraph).toBeNull();
  });

  it('closeGraph does NOT clear availableGraphs', () => {
    const state: DesignerState = {
      ...initialDesignerState,
      availableGraphs: [mockGraphMetadata('a')],
      currentGraphUri: 'file:///proj/graphs/a.ttrg',
      currentGraph: mockGraph(),
    };
    const next = designerReducer(state, { type: 'closeGraph' });
    expect(next.availableGraphs).toHaveLength(1);
  });

  it('storeGraph sets currentGraph', () => {
    const graph = mockGraph({ schema: 'db' });
    const state = designerReducer(initialDesignerState, { type: 'storeGraph', graph });
    expect(state.currentGraph).toEqual(graph);
  });

  it('loadLayout restores nodePositions and currentViewport', () => {
    const layout: GraphLayoutOutput = {
      viewport: { zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' },
      nodes: { 'er.entity.artikl': { x: 320, y: 180 } },
      edges: {},
    };
    const state = designerReducer(initialDesignerState, { type: 'loadLayout', layout });
    expect(state.nodePositions).toEqual({ 'er.entity.artikl': { x: 320, y: 180 } });
    expect(state.currentViewport).toEqual({ zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' });
  });

  it('startCreateWizard sets creatingGraph to true', () => {
    const state = designerReducer(initialDesignerState, { type: 'startCreateWizard' });
    expect(state.creatingGraph).toBe(true);
  });

  it('cancelCreateWizard sets creatingGraph to false', () => {
    const state: DesignerState = { ...initialDesignerState, creatingGraph: true };
    const next = designerReducer(state, { type: 'cancelCreateWizard' });
    expect(next.creatingGraph).toBe(false);
  });

  it('cancelCreateWizard does NOT clear availableGraphs', () => {
    const state: DesignerState = {
      ...initialDesignerState,
      availableGraphs: [mockGraphMetadata('a')],
      creatingGraph: true,
    };
    const next = designerReducer(state, { type: 'cancelCreateWizard' });
    expect(next.availableGraphs).toHaveLength(1);
  });

  it('setViewport updates currentViewport', () => {
    const state = designerReducer(initialDesignerState, {
      type: 'setViewport',
      viewport: { zoom: 1.5, panX: 100, panY: 50 },
    });
    expect(state.currentViewport).toEqual({
      zoom: 1.5,
      panX: 100,
      panY: 50,
      displayMode: 'just-names',
    });
  });

  it('setDisplayMode updates currentViewport.displayMode', () => {
    const state = designerReducer(initialDesignerState, {
      type: 'setDisplayMode',
      mode: 'with-types',
    });
    expect(state.currentViewport?.displayMode).toBe('with-types');
  });

  it('setNodePosition upserts and overwrites', () => {
    let state = designerReducer(initialDesignerState, {
      type: 'setNodePosition',
      qname: 'er.entity.artikl',
      x: 100,
      y: 200,
    });
    expect(state.nodePositions['er.entity.artikl']).toEqual({ x: 100, y: 200 });

    state = designerReducer(state, {
      type: 'setNodePosition',
      qname: 'er.entity.artikl',
      x: 300,
      y: 400,
    });
    expect(state.nodePositions['er.entity.artikl']).toEqual({ x: 300, y: 400 });

    state = designerReducer(state, {
      type: 'setNodePosition',
      qname: 'er.entity.dobropis',
      x: 500,
      y: 600,
    });
    expect(state.nodePositions['er.entity.dobropis']).toEqual({ x: 500, y: 600 });
  });

  it('selectSymbol sets and clears selectedSymbol', () => {
    let state = designerReducer(initialDesignerState, {
      type: 'selectSymbol',
      qname: 'er.entity.artikl',
    });
    expect(state.selectedSymbol).toEqual({ qname: 'er.entity.artikl' });

    state = designerReducer(state, { type: 'selectSymbol', qname: null });
    expect(state.selectedSymbol).toBeNull();
  });

  it('storeSymbolDetail caches by qname', () => {
    const detail = {
      qname: 'er.entity.artikl',
      kind: 'entity' as const,
      name: 'artikl',
      label: 'Artikl',
      description: 'Article master data',
      tags: ['core'],
      sourceUri: 'file:///proj/a.ttrm',
      sourceLine: 10,
      perKindData: { kind: 'entity' as const, attributes: [] as unknown as [], nameAttributeQname: null, codeAttributeQname: null, roleQnames: [] },
      referencedBy: [],
    };
    const state = designerReducer(initialDesignerState, { type: 'storeSymbolDetail', detail });
    expect(state.symbolDetails['er.entity.artikl']).toEqual(detail);
  });

  it('loadProject resets project-level fields but preserves cross-project caches', () => {
    const state: DesignerState = {
      ...initialDesignerState,
      projectUri: 'file:///old',
      availableGraphs: [mockGraphMetadata('x')],
      currentGraphUri: 'file:///old/graphs/x.ttrg',
      currentGraph: mockGraph(),
      nodePositions: { 'er.entity.foo': { x: 1, y: 2 } },
      symbolDetails: { 'er.entity.artikl': { qname: 'er.entity.artikl', kind: 'entity' as const, name: 'artikl', label: 'Art', description: null, tags: [], sourceUri: '', sourceLine: 0, perKindData: { kind: 'other' }, referencedBy: [] } },
      creatingGraph: false,
      error: null,
    };
    const next = designerReducer(state, { type: 'loadProject', projectUri: 'file:///new' });
    expect(next.projectUri).toBe('file:///new');
    expect(next.availableGraphs).toEqual([]);
    expect(next.currentGraphUri).toBeNull();
    expect(next.currentGraph).toBeNull();
    expect(next.nodePositions).toEqual({});
    expect(next.symbolDetails).toEqual({});
  });

  it('setError sets and clears error', () => {
    let state = designerReducer(initialDesignerState, { type: 'setError', message: 'oops' });
    expect(state.error).toBe('oops');
    state = designerReducer(state, { type: 'setError', message: null });
    expect(state.error).toBeNull();
  });
});