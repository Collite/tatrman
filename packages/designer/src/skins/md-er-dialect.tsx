// md.er-dialect — the same md graph in the er card idiom (the "cheap cognitive rent" skin: an
// analyst who knows the er canvas reads the cube here for free). Cube + dimensions render as
// bordered cards visually kin to er.crow, laid out by ELK. P-1 by construction: it renders the
// SAME MdCubeBody / MdDimBody as md.star-glyph — every measure, calc, and level is present; only
// the framing (cards vs star polygon + orbit) differs.

import type { CanvasNode, NodeRenderProps } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { MdCubeBody, MdDimBody, dimSize } from './md-body.js';

function ErDialectNode({ node }: NodeRenderProps) {
  const isCube = node.kind === 'cubelet';
  return (
    <div data-testid="md-er-card" style={{ width: '100%', height: '100%', background: '#fff', border: '1.3px solid #CBDDF4', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ height: 28, background: isCube ? '#24405f' : '#EAF1FB', color: isCube ? '#fff' : '#16283F', display: 'flex', alignItems: 'center', gap: 6, padding: '0 10px', fontWeight: 'bold', fontSize: 12 }}>
        <span data-testid="kind-mark" aria-hidden>{isCube ? '◈' : '◭'}</span>
        <span data-testid="node-label">{node.label}</span>
      </div>
      <div style={{ padding: '6px 8px' }}>
        {isCube ? <MdCubeBody node={node} /> : <MdDimBody node={node} />}
      </div>
    </div>
  );
}

const CUBE_CARD = { width: 176, height: 96 };

export const mdErDialect: DesignerSkin = {
  id: 'md.er-dialect',
  face: 'modeling',
  modelKind: 'md',
  displayName: 'ER-dialect',
  description: 'Cube + dimensions as er-style cards (same data as star-glyph, familiar framing)',
  flow: { orientation: 'LR', layout: { nodeSpacing: 60, layerSpacing: 120 } },
  canvas: { background: '#E9F0F8', grid: 'dots' },
  edgeStyle: () => ({ stroke: '#4A4B4D', width: 1.6, marker: 'none' }),
  portGeometry: (port) => ({ shape: 'circle', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' }),
  declareAnchors: (size) => ({
    chrome: { x: 12, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
  }),
  renderNode: ErDialectNode,
  containerStyle: {},
  nodeSize: (node: CanvasNode) => (node.kind === 'cubelet' ? CUBE_CARD : dimSize(node)),
};
