// SPDX-License-Identifier: Apache-2.0
// View-state seam types (contracts §7). The persistable shape carries skin/mode/positions/
// collapsed — and NOTHING about the viewport (viewport is never persisted, C1-b wall).

import type { SkinId, Positions } from './contract.js';

export interface CanvasViewState {
  skin: SkinId;
  mode: 'auto' | 'manual';
  nodes: Positions; // manual positions (empty in auto mode)
  collapsed: string[]; // collapsed container ids
  /** where the skin field is actually stored — the truth chip surfaces 'prefs' honestly
   *  when the .ttrg layout block can't carry `skin` yet (GQ-1). Not persisted itself. */
  skinStorage?: 'ttrg' | 'prefs';
  // NO viewport field — by construction (invariant 6 / C1-b).
}

/** contracts §7 — impls: TtrgLayoutBlockStore (now) · TtrlSidecarStore (C1-f arc). */
export interface ViewStateStore {
  read(canvasKey: string): Promise<CanvasViewState>;
  write(canvasKey: string, vs: CanvasViewState): Promise<void>;
}
