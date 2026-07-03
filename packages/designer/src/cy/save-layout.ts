import type { ViewportState, DisplayMode } from '@modeler/lsp';

export interface CyShim {
  nodes(): Array<{ position(): { x: number; y: number }; data(k: string): unknown }>;
  pan(): { x: number; y: number };
  zoom(): number;
}

export function buildLayout(
  cy: CyShim,
  currentViewport: ViewportState | null,
  displayMode: DisplayMode
): { nodes: Record<string, { x: number; y: number }>; viewport: ViewportState } {
  const nodes: Record<string, { x: number; y: number }> = {};
  cy.nodes().forEach((node) => {
    const pos = node.position();
    const qname = node.data('qname') as string;
    nodes[qname] = { x: pos.x, y: pos.y };
  });

  const pan = cy.pan();
  const zoom = cy.zoom();

  const viewport: ViewportState = {
    zoom,
    panX: pan.x,
    panY: pan.y,
    displayMode: displayMode ?? currentViewport?.displayMode ?? 'just-names',
  };

  return { nodes, viewport };
}

export function applyPositions(
  cy: { getElementById(id: string): { length: number; position(pos: { x: number; y: number }): void } },
  positions: Record<string, { x: number; y: number }>
): void {
  for (const [qname, pos] of Object.entries(positions)) {
    const node = cy.getElementById(qname);
    if (node.length > 0) {
      node.position({ x: pos.x, y: pos.y });
    }
  }
}