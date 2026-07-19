// SPDX-License-Identifier: Apache-2.0
// The layout seam (contracts §3 / C1-b wall). Deterministic auto-layout until first drag,
// then manual; "reset to auto"; viewport never persisted. ELK layered for flow-shaped
// graphs; a skin may declare a custom layout (star-glyph orbit, cnc bubbles) that bypasses
// ELK. Pure TS — elkjs is a plain-JS dependency (no React).

import ElkBundled from 'elkjs/lib/elk.bundled.js';
import type { CanvasGraph, CanvasNode } from './types.js';
import type { Positions, NodeSize, Orientation, LayoutParams } from './contract.js';

// elkjs's bundled build is browser-safe. Its .d.ts `export default` mismatches the Node16 CJS
// interop type (the default binds to the namespace, not a constructor), so we cast — the
// runtime default IS the ELK constructor in both Node (tsc ESM) and the browser (vite).
interface ElkGraph {
  id: string;
  layoutOptions?: Record<string, string>;
  children: Array<{ id: string; width: number; height: number }>;
  edges: Array<{ id: string; sources: string[]; targets: string[] }>;
}
interface ElkResult {
  children?: Array<{ id: string; x?: number; y?: number }>;
}
interface ElkInstance {
  layout(graph: ElkGraph): Promise<ElkResult>;
}
const ELK = ElkBundled as unknown as { new (): ElkInstance };

// Lazy — the elkjs bundled build is a large compiled blob; construct it only the first time
// auto-layout actually runs (keeps `import '@tatrman/canvas-core'` cheap in test envs).
let elkInstance: ElkInstance | null = null;
function getElk(): ElkInstance {
  if (!elkInstance) elkInstance = new ELK();
  return elkInstance;
}

export interface LayoutInput {
  orientation: Orientation;
  params?: LayoutParams;
  /** deterministic node sizing (the kernel measures or defaults per skin) */
  sizeOf: (node: CanvasNode) => NodeSize;
}

/**
 * Deterministic auto-layout: same graph + same input ⇒ same positions. ELK is deterministic
 * given a fixed option set and sorted inputs, so nodes/edges are sorted by id. A skin's
 * custom layout (params.custom) bypasses ELK entirely.
 */
export async function layoutAuto(graph: CanvasGraph, input: LayoutInput): Promise<Positions> {
  if (input.params?.custom) {
    return input.params.custom(graph, input.sizeOf);
  }

  const children = [...graph.nodes]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((n) => {
      const { width, height } = input.sizeOf(n);
      return { id: n.id, width, height };
    });

  const edges = [...graph.edges]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((e) => ({ id: e.id, sources: [e.from.node], targets: [e.to.node] }));

  const res = await getElk().layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': input.orientation === 'LR' ? 'RIGHT' : 'DOWN',
      'elk.spacing.nodeNode': String(input.params?.nodeSpacing ?? 48),
      'elk.layered.spacing.nodeNodeBetweenLayers': String(input.params?.layerSpacing ?? 80),
      'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
      'elk.randomSeed': '1',
    },
    children,
    edges,
  });

  const pos: Positions = {};
  for (const c of res.children ?? []) pos[c.id] = { x: c.x ?? 0, y: c.y ?? 0 };
  return pos;
}

/**
 * Per-canvas layout mode (C1-b wall): auto (deterministic) until first drag, then manual.
 * `resetToAuto()` discards manual overrides. Viewport is NOT held here — never persisted.
 */
export class LayoutController {
  private _mode: 'auto' | 'manual' = 'auto';
  private auto: Positions;
  private manual: Positions = {};

  constructor(autoPositions: Positions) {
    this.auto = autoPositions;
  }

  get mode(): 'auto' | 'manual' {
    return this._mode;
  }

  /** effective positions: auto, overlaid with manual drags when in manual mode */
  get positions(): Positions {
    return this._mode === 'auto' ? { ...this.auto } : { ...this.auto, ...this.manual };
  }

  /** first drag flips the canvas to manual and preserves the dragged position */
  nodeDragged(id: string, pos: { x: number; y: number }): void {
    this._mode = 'manual';
    this.manual[id] = pos;
  }

  /** re-run of auto-layout gave new positions (skin switch, graph change) */
  setAuto(autoPositions: Positions): void {
    this.auto = autoPositions;
  }

  resetToAuto(): void {
    this._mode = 'auto';
    this.manual = {};
  }

  /** the persistable manual overrides (feeds CanvasViewState.nodes) */
  get manualPositions(): Positions {
    return { ...this.manual };
  }
}
