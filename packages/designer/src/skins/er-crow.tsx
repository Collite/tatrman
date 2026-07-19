// er.crow — the crow's-foot ER notation (E-3a default for er). Entity cards with attribute
// rows; relations carry crow's-foot cardinality at each end (rendered by the kernel edge
// from CanvasEdge.cardinality). Migrated idiom from a-modeling-v0.html's er tab.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { RowCard, cardSize } from './card-rows.js';

function ErCrowNode({ node }: NodeRenderProps) {
  return <RowCard node={node} kindMark="◇" headerBg="#24405f" showTypes={false} />;
}

export const erCrow: DesignerSkin = {
  id: 'er.crow',
  face: 'modeling',
  modelKind: 'er',
  displayName: "crow's-foot",
  description: 'ER entities as cards; relations with crow’s-foot cardinality',
  flow: { orientation: 'LR', layout: { nodeSpacing: 60, layerSpacing: 120 } },
  canvas: { background: '#E9F0F8', grid: 'dots' },
  edgeStyle: () => ({ stroke: '#4A4B4D', width: 1.6, marker: 'none' }),
  portGeometry: (port) => ({ shape: 'circle', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' }),
  declareAnchors: (size) => ({
    chrome: { x: 12, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
  }),
  renderNode: ErCrowNode,
  containerStyle: {},
  nodeSize: (node: CanvasNode) => cardSize(node, 200),
};
