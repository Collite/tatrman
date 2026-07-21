// script — the text-forward processing skin (E-3, the other extreme from stage). Stage-Navy dark
// canvas, description-or-code pills, TD flow, dashed YELLOW control edge on the cross axis (D-4).
// Proving the contract: the SAME kernel model renders as text pills top-down here and icon-cards
// left-right in stage, base chrome identical. Geometry reference: b-processing-v0.html SKINS.script.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import { TOKENS } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)
import type { DesignerSkin } from '../canvas/skin-component.js';
import { ProcessingNodeBody, procNodeSize } from './processing-nodes.js';

function ScriptNode({ node }: NodeRenderProps) {
  return <ProcessingNodeBody node={node} variant="script" />;
}

export const script: DesignerSkin = {
  id: 'script',
  face: 'processing',
  displayName: 'Script',
  description: 'Text-forward pills on Stage-Navy — TD flow, description-or-code, control on cross axis',
  flow: { orientation: 'TD', layout: { nodeSpacing: 40, layerSpacing: 90 } },
  canvas: { background: TOKENS.stageNavy, grid: 'none' },
  edgeStyle: (edge) => {
    if (edge.role === 'control') return { stroke: TOKENS.yellow, width: 1.4, dash: '5 4', marker: 'arrow' };
    return { stroke: palette.edgeScript, width: edge.role === 'transfer' ? 2.2 : 1.7, marker: 'arrow' };
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
  renderNode: ScriptNode,
  containerStyle: { headerHeight: 30, ghostContent: true, drillAffordance: true },
  nodeSize: (node: CanvasNode) => procNodeSize(node, 'script'),
};
