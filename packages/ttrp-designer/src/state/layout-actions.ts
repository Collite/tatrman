// SPDX-License-Identifier: Apache-2.0
import type { CanvasLayout, NodePos } from '../graph/types.js';
import type { SkinId } from '../skins/index.js';

/**
 * The auto→manual flip (C1-b layout decision, T5.3.6): on the first drag of an auto
 * canvas, snapshot ALL rendered positions and emit a manual `CanvasLayout` for a wholesale
 * `setLayout`. Pure so the flip is deterministic + testable; the gesture wiring lives in
 * the Canvas component.
 */
export function snapshotToManual(
  canvasKey: string,
  positions: Record<string, { x: number; y: number }>,
  skin: SkinId | undefined,
): CanvasLayout {
  const nodes: NodePos[] = Object.entries(positions).map(([zeta, p]) => ({ zeta, x: p.x, y: p.y }));
  return { key: canvasKey, skin: skin ?? null, mode: 'manual', nodes, collapsed: [] };
}

/** Reset to auto (per-canvas): drops the manual node positions (C1-b, T5.3.6). */
export function resetToAuto(canvasKey: string, skin: SkinId | undefined): CanvasLayout {
  return { key: canvasKey, skin: skin ?? null, mode: 'auto', nodes: [], collapsed: [] };
}

/** True when a ζ was flagged orphaned by getLayout (→ "layout reset" badge, C1-c-i). */
export function isOrphaned(orphaned: string[], zeta: string): boolean {
  return orphaned.includes(zeta);
}
