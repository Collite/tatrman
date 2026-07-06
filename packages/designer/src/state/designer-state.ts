import type { GraphMetadata, GetGraphResponse, SymbolDetail } from '@tatrman/lsp';

export type DisplayMode = 'just-names' | 'with-types' | 'with-constraints';

export interface ViewportState {
  zoom: number;
  panX: number;
  panY: number;
  displayMode: DisplayMode;
}

export interface DesignerState {
  projectUri: string | null;
  availableGraphs: GraphMetadata[];
  currentGraphUri: string | null;
  currentGraph: GetGraphResponse | null;
  currentViewport: ViewportState | null;
  nodePositions: Record<string, { x: number; y: number }>;
  symbolDetails: Record<string, SymbolDetail>;
  selectedSymbol: { qname: string } | null;
  creatingGraph: boolean;
  error: string | null;
}

export const initialDesignerState: DesignerState = {
  projectUri: null,
  availableGraphs: [],
  currentGraphUri: null,
  currentGraph: null,
  currentViewport: null,
  nodePositions: {},
  symbolDetails: {},
  selectedSymbol: null,
  creatingGraph: false,
  error: null,
};