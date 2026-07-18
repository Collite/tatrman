// SPDX-License-Identifier: Apache-2.0
// Minimal valid SkinDefinition builder for registry/contract tests. Renderer is an opaque
// token (a string stands in for the React component canvas-core never touches).

import type { SkinDefinition, AnchorDeclaration, NodeSize } from '../contract.js';

const fullAnchors = (size: NodeSize): AnchorDeclaration => ({
  chrome: { x: 12, y: 2, align: 'tl' },
  status: { x: size.width - 2, y: 2, align: 'tr' },
  diagnostics: { x: 2, y: 2, align: 'tl' },
});

export function fakeSkin(overrides: Partial<SkinDefinition> = {}): SkinDefinition {
  return {
    id: 'test.skin',
    face: 'modeling',
    modelKind: 'er',
    displayName: 'Test',
    description: 'a valid minimal skin',
    flow: { orientation: 'LR', layout: {} },
    canvas: { background: '#E9F0F8' },
    edgeStyle: () => ({ stroke: '#16283F', width: 2 }),
    portGeometry: () => ({ shape: 'circle', placement: 'flow-out' }),
    declareAnchors: fullAnchors,
    renderNode: 'OPAQUE_RENDERER_TOKEN',
    containerStyle: {},
    ...overrides,
  };
}
