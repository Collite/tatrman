// SPDX-License-Identifier: Apache-2.0
import cytoscape, { type Core, type ElementDefinition } from 'cytoscape';
import type { CanvasElements } from '../graph/derive-orchestration.js';
import type { AbstractCoord, NodePos } from '../graph/types.js';
import type { Skin } from '../skins/types.js';
import { coordToPixels } from './orientation.js';

export interface RenderInput {
  elements: CanvasElements;
  skin: Skin;
  /** Server auto-layout for this canvas: ζ → abstract coord (used when no manual position). */
  autoLayout: Record<string, AbstractCoord>;
  /** Manual positions (ζ-keyed) from the `.ttrl` sidecar; override auto when present. */
  manual?: Record<string, NodePos>;
}

/** Map the canvas element set to Cytoscape element definitions (nodes carry ζ/kind/flags). */
export function toElements(input: RenderInput): ElementDefinition[] {
  const { elements, skin, autoLayout, manual } = input;
  const nodeDefs: ElementDefinition[] = elements.nodes.map((n) => {
    const pos =
      manual?.[n.zeta] != null
        ? { x: manual[n.zeta].x, y: manual[n.zeta].y }
        : autoLayout[n.zeta]
          ? coordToPixels(autoLayout[n.zeta], skin.orientation)
          : { x: 0, y: 0 };
    return {
      group: 'nodes',
      data: {
        id: n.zeta,
        label: skin.nodeLabel(n),
        zeta: n.zeta,
        kind: n.kind,
        containerPath: n.containerPath ?? null,
        derived: n.derived,
        synthesized: n.synthesized,
      },
      classes: skin.nodeClasses(n).join(' '),
      position: pos,
    };
  });
  const edgeDefs: ElementDefinition[] = elements.edges.map((e) => ({
    group: 'edges',
    data: { id: e.id, source: e.from, target: e.to, via: e.via ?? null },
    classes: e.type.startsWith('control') ? 'control' : 'data',
  }));
  return [...nodeDefs, ...edgeDefs];
}

/** Build (or refresh) a headless-capable Cytoscape core for a canvas. */
export function renderCanvas(input: RenderInput, container?: HTMLElement): Core {
  return cytoscape({
    container: container ?? undefined,
    headless: container == null,
    elements: toElements(input),
    style: input.skin.style,
  });
}
