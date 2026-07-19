// cnc.cards — the same conceptual graph in the card idiom (for readers who prefer the modeling
// card look to the ontology bubble). Concept = card with its properties shown at rest (no focus
// gate). P-1 by construction: reuses the SAME RoleChip / PropertyChips as cnc.bubbles, so every
// property, role, and synonym the bubbles skin shows on focus is present here — only the framing
// (card vs ellipse) and the reveal timing (at-rest vs on-focus) differ.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { RoleChip, PropertyChips, conceptSize } from './cnc-body.js';

function ConceptCard({ node }: NodeRenderProps) {
  return (
    <div data-testid="cnc-card" style={{ width: '100%', height: '100%', background: '#fff', border: '1.3px solid #CBD8E6', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ height: 26, background: '#33506e', color: '#fff', display: 'flex', alignItems: 'center', gap: 6, padding: '0 10px', fontWeight: 'bold', fontSize: 12 }}>
        <span data-testid="kind-mark" aria-hidden>◕</span>
        <span data-testid="node-label" style={{ flex: 1 }}>{node.label}</span>
        <RoleChip node={node} />
      </div>
      <div style={{ padding: '6px 8px' }}>
        <PropertyChips node={node} />
      </div>
    </div>
  );
}

export const cncCards: DesignerSkin = {
  id: 'cnc.cards',
  face: 'modeling',
  modelKind: 'cnc',
  displayName: 'cards',
  description: 'Concepts as cards with properties + role at rest (same data as bubbles)',
  flow: { orientation: 'LR', layout: { nodeSpacing: 60, layerSpacing: 120 } },
  canvas: { background: '#E9F0F8', grid: 'dots' },
  edgeStyle: () => ({ stroke: '#4A4B4D', width: 1.6, marker: 'arrow' }),
  portGeometry: (port) => ({ shape: 'circle', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' }),
  declareAnchors: (size) => ({
    chrome: { x: 12, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
  }),
  renderNode: ConceptCard,
  containerStyle: {},
  nodeSize: (node: CanvasNode) => conceptSize(node),
};
