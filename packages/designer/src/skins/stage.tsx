// stage — the default processing skin (E-3a). Icon-card nodes on the ice dot-grid, LR flow,
// solid navy data/transfer edges, dashed gray control edge entering on the CROSS axis (D-4).
// The two extremes (stage/script) prove the processing contract (E-3). Geometry reference:
// b-processing-v0.html SKINS.stage.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import { TOKENS } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { ProcessingNodeBody, procNodeSize } from './processing-nodes.js';

function StageNode({ node }: NodeRenderProps) {
  return <ProcessingNodeBody node={node} variant="stage" />;
}

export const stage: DesignerSkin = {
  id: 'stage',
  face: 'processing',
  displayName: 'Stage',
  description: 'Icon-card pipeline on the ice grid — LR flow, control on the cross axis',
  flow: { orientation: 'LR', layout: { nodeSpacing: 48, layerSpacing: 120 } },
  canvas: { background: TOKENS.ice, grid: 'dots' },
  edgeStyle: (edge) => {
    if (edge.role === 'control') return { stroke: TOKENS.gray.structure, width: 1.4, dash: '5 4', marker: 'arrow' };
    // data + transfer: solid navy (transfer slightly heavier — the synthesized region hand-off)
    return { stroke: TOKENS.stageNavy, width: edge.role === 'transfer' ? 2.2 : 1.7, marker: 'arrow' };
  },
  portGeometry: (port) => {
    if (port.role === 'control') return { shape: 'diamond', placement: port.direction === 'out' ? 'cross-out' : 'cross-in' };
    if (port.role === 'rejects' || port.role === 'err') return { shape: 'triangle', placement: 'cross-out' };
    return { shape: 'circle', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' };
  },
  declareAnchors: (size) => ({
    chrome: { x: 10, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
    previewChip: { x: size.width / 2, y: size.height, align: 'edge' },
  }),
  renderNode: StageNode,
  containerStyle: { headerHeight: 30, ghostContent: true, drillAffordance: true },
  nodeSize: (node: CanvasNode) => procNodeSize(node, 'stage'),
};
