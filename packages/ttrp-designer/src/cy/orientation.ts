import type { AbstractCoord } from '../graph/types.js';

/** Edge-flow orientation: Alteryx/KNIME flow left→right; Enso flows top→down (C1-b). */
export type Orientation = 'LR' | 'TD';

const LAYER_GAP = 220;
const INDEX_GAP = 110;

/**
 * Maps the server's deterministic abstract `{layer, index}` (C1-b — one core, both
 * conventions) to pixels for the given orientation. LR ⇒ x = layer (data flows right);
 * TD ⇒ y = layer (transposed). Pure + unit-tested; no per-skin position sets (C1-b-ii).
 */
export function coordToPixels(coord: AbstractCoord, orientation: Orientation): { x: number; y: number } {
  if (orientation === 'LR') {
    return { x: coord.layer * LAYER_GAP, y: coord.index * INDEX_GAP };
  }
  return { x: coord.index * INDEX_GAP, y: coord.layer * LAYER_GAP };
}
