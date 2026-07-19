// cnc.bubbles — the property-graph notation (E-3a default for cnc). Concepts are ellipses with
// labeled directed relations; properties COLLAPSE at rest and EXPAND as chips on focus (the
// Ontology Playground inheritance). Focus is the kernel's selection/focus state (state.focused),
// NOT a skin-private mechanism (P-2). The role chip (fixture) shows at rest.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { RoleChip, PropertyChips, conceptSize } from './cnc-body.js';

function BubbleNode({ node, state }: NodeRenderProps) {
  return (
    <div data-testid="cnc-bubble" style={{ width: '100%', height: '100%', background: '#fff', border: '1.6px solid #7C93AE', borderRadius: '50% / 42%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 3, padding: 10, textAlign: 'center' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
        <span data-testid="node-label" style={{ fontWeight: 'bold', fontSize: 13, color: '#16283F' }}>{node.label}</span>
        <RoleChip node={node} />
      </div>
      {/* properties expand only on focus (P-2: driven by the kernel's focus state) */}
      {state.focused && <PropertyChips node={node} />}
    </div>
  );
}

export const cncBubbles: DesignerSkin = {
  id: 'cnc.bubbles',
  face: 'modeling',
  modelKind: 'cnc',
  displayName: 'bubbles',
  description: 'Concepts as property-graph ellipses; properties expand as chips on focus',
  flow: { orientation: 'LR', layout: { nodeSpacing: 90, layerSpacing: 140 } },
  canvas: { background: '#F3F7FC', grid: 'dots' },
  edgeStyle: () => ({ stroke: '#5B7EA6', width: 1.5, marker: 'arrow' }),
  portGeometry: (port) => ({ shape: 'circle', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' }),
  declareAnchors: (size) => ({
    chrome: { x: 10, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
  }),
  renderNode: BubbleNode,
  containerStyle: {},
  nodeSize: (node: CanvasNode) => conceptSize(node),
};
