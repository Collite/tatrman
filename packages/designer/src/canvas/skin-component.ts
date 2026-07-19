// Designer-side skin shape. canvas-core's SkinDefinition types renderNode as an OPAQUE token
// (it never renders React); the designer refines it to a concrete React component and adds
// node sizing (needed by the layout seam + anchor placement). A DesignerSkin IS a
// SkinDefinition (structural superset), so the SkinRegistry stores it and enforces the
// contract; the kernel casts resolved skins back to DesignerSkin to read renderNode/nodeSize.

import type { ComponentType } from 'react';
import type { CanvasNode, NodeRenderProps, NodeSize, SkinDefinition } from '@tatrman/canvas-core';

export type SkinComponent = ComponentType<NodeRenderProps>;

export interface DesignerSkin extends Omit<SkinDefinition, 'renderNode'> {
  renderNode: SkinComponent;
  /** deterministic node sizing (feeds ELK layout + base-chrome anchors) */
  nodeSize: (node: CanvasNode) => NodeSize;
}

/** widen back to the canvas-core contract for registration (enforcement runs there). */
export const asSkinDefinition = (s: DesignerSkin): SkinDefinition => s as unknown as SkinDefinition;
