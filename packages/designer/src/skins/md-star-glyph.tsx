// md.star-glyph — the star-schema notation (E-3a default for md). The cube sits at the centre
// as a polygon carrying its measures + `calc:` banner; dimensions orbit it as level-stack cards.
// The orbit is the skin's OWN deterministic layout (flow.layout.custom bypasses ELK). Geometry
// ported from a-modeling-v0.html's md tab.

import type { CanvasGraph, CanvasNode, NodeRenderProps, Positions } from '@tatrman/canvas-core';
import type { DesignerSkin } from '../canvas/skin-component.js';
import { MdCubeBody, MdDimBody, CUBE_SIZE, dimSize } from './md-body.js';

function StarGlyphNode({ node }: NodeRenderProps) {
  if (node.kind === 'cubelet') {
    return (
      <div data-testid="star-cube" style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 4,
        background: '#24405f', color: '#fff', clipPath: 'polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%)', padding: 12 }}>
        <span data-testid="node-label" style={{ fontWeight: 'bold', fontSize: 13 }}>{node.label}</span>
        <MdCubeBody node={node} />
      </div>
    );
  }
  // dimension: orbiting level-stack card
  return (
    <div data-testid="star-dim" style={{ width: '100%', height: '100%', background: '#fff', border: '1.3px solid #CBDDF4', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ height: 24, background: '#EAF1FB', color: '#16283F', display: 'flex', alignItems: 'center', gap: 6, padding: '0 8px', fontWeight: 'bold', fontSize: 12 }}>
        <span data-testid="kind-mark" aria-hidden>◭</span>
        <span data-testid="node-label">{node.label}</span>
      </div>
      <MdDimBody node={node} />
    </div>
  );
}

/**
 * Orbit layout (deterministic): the cube at the canvas centre, dimensions equally spaced on a
 * ring around it (sorted by id, fixed radius). Same graph in ⇒ same positions out.
 */
export function orbitLayout(graph: CanvasGraph, _sizeOf: (n: CanvasNode) => { width: number; height: number }): Positions {
  const CX = 420, CY = 320, R = 260;
  const pos: Positions = {};
  const cube = graph.nodes.find((n) => n.kind === 'cubelet');
  if (cube) pos[cube.id] = { x: CX, y: CY };
  const dims = graph.nodes.filter((n) => n.kind === 'dimension').sort((a, b) => a.id.localeCompare(b.id));
  const n = Math.max(1, dims.length);
  dims.forEach((d, i) => {
    const angle = (2 * Math.PI * i) / n - Math.PI / 2; // start at 12 o'clock
    pos[d.id] = { x: Math.round(CX + R * Math.cos(angle)), y: Math.round(CY + R * Math.sin(angle)) };
  });
  // any stray non-cube/dim node: park it below, deterministically by id order
  graph.nodes
    .filter((x) => x.kind !== 'cubelet' && x.kind !== 'dimension')
    .sort((a, b) => a.id.localeCompare(b.id))
    .forEach((x, i) => { pos[x.id] = { x: CX + i * 40, y: CY + R + 120 }; });
  return pos;
}

export const mdStarGlyph: DesignerSkin = {
  id: 'md.star-glyph',
  face: 'modeling',
  modelKind: 'md',
  displayName: 'star-glyph',
  description: 'Cube as a central polygon (measures + calc); dimensions orbit as level-stacks',
  flow: { orientation: 'LR', layout: { custom: orbitLayout } },
  canvas: { background: '#EEF4FB', grid: 'dots' },
  edgeStyle: () => ({ stroke: '#7C93AE', width: 1.4, marker: 'none' }),
  portGeometry: (port) => ({ shape: 'diamond', placement: port.direction === 'out' ? 'flow-out' : 'flow-in' }),
  declareAnchors: (size) => ({
    chrome: { x: 10, y: 4, align: 'tl' },
    status: { x: size.width - 4, y: 4, align: 'tr' },
    diagnostics: { x: 4, y: 4, align: 'tl' },
  }),
  renderNode: StarGlyphNode,
  containerStyle: {},
  nodeSize: (node: CanvasNode) => (node.kind === 'cubelet' ? CUBE_SIZE : dimSize(node)),
};
