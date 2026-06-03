import type { GraphMetadata, GetGraphResponse, SymbolDetail, GraphLayoutOutput } from '@modeler/lsp';
import type { DesignerState, ViewportState, DisplayMode } from './designer-state';

export interface GraphLayoutInput {
  version: number;
  viewports?: Record<string, { zoom: number; panX: number; panY: number; displayMode: string }>;
  nodes: Record<string, { x: number; y: number }>;
  edges: Record<string, { bendPoints?: [number, number][] }>;
}

export type DesignerAction =
  | { type: 'loadProject'; projectUri: string }
  | { type: 'storeGraphList'; graphs: GraphMetadata[] }
  | { type: 'openGraph'; graphUri: string }
  | { type: 'closeGraph' }
  | { type: 'storeGraph'; graph: GetGraphResponse }
  | { type: 'loadLayout'; layout: GraphLayoutOutput }
  | { type: 'startCreateWizard' }
  | { type: 'cancelCreateWizard' }
  | { type: 'setViewport'; viewport: Omit<ViewportState, 'displayMode'> }
  | { type: 'setDisplayMode'; mode: DisplayMode }
  | { type: 'setNodePosition'; qname: string; x: number; y: number }
  | { type: 'selectSymbol'; qname: string | null }
  | { type: 'storeSymbolDetail'; detail: SymbolDetail }
  | { type: 'setError'; message: string | null };

export function designerReducer(state: DesignerState, action: DesignerAction): DesignerState {
  switch (action.type) {
    case 'loadProject':
      return {
        ...state,
        projectUri: action.projectUri,
        availableGraphs: [],
        currentGraphUri: null,
        currentGraph: null,
        nodePositions: {},
        symbolDetails: {},
        currentViewport: null,
        creatingGraph: false,
        error: null,
      };
    case 'storeGraphList':
      return { ...state, availableGraphs: action.graphs };
    case 'openGraph':
      return {
        ...state,
        currentGraphUri: action.graphUri,
        currentGraph: null,
        nodePositions: {},
        currentViewport: null,
      };
    case 'closeGraph':
      return { ...state, currentGraphUri: null, currentGraph: null };
    case 'storeGraph':
      return { ...state, currentGraph: action.graph };
    case 'loadLayout':
      return {
        ...state,
        nodePositions: action.layout.nodes ?? {},
        currentViewport: action.layout.viewport
          ? { zoom: action.layout.viewport.zoom, panX: action.layout.viewport.panX, panY: action.layout.viewport.panY, displayMode: action.layout.viewport.displayMode as DisplayMode }
          : null,
      };
    case 'startCreateWizard':
      return { ...state, creatingGraph: true };
    case 'cancelCreateWizard':
      return { ...state, creatingGraph: false };
    case 'setViewport':
      return {
        ...state,
        currentViewport: {
          ...state.currentViewport ?? { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
          ...action.viewport,
        },
      };
    case 'setDisplayMode':
      return {
        ...state,
        currentViewport: {
          ...state.currentViewport ?? { zoom: 1, panX: 0, panY: 0, displayMode: 'just-names' },
          displayMode: action.mode,
        },
      };
    case 'setNodePosition':
      return {
        ...state,
        nodePositions: {
          ...state.nodePositions,
          [action.qname]: { x: action.x, y: action.y },
        },
      };
    case 'selectSymbol':
      return {
        ...state,
        selectedSymbol: action.qname !== null ? { qname: action.qname } : null,
      };
    case 'storeSymbolDetail':
      return {
        ...state,
        symbolDetails: {
          ...state.symbolDetails,
          [action.detail.qname]: action.detail,
        },
      };
    case 'setError':
      return { ...state, error: action.message };
    default:
      return state;
  }
}