// SPDX-License-Identifier: Apache-2.0
// Fixed badge vocabulary (contracts §2 / S-3): ✓ ▶ ⚠ ✕ 🔒 + orphan.
// Shape + color, NEVER color alone — every badge carries a glyph (non-color discriminator).
// The base layer draws these at skin-declared anchors; a skin may CLAIM status/diagnostics
// and re-render them but may never redefine the vocabulary (invariant 5).

import { TOKENS } from './tokens.js';

export type BadgeId = 'done' | 'running' | 'warn' | 'error' | 'readOnly' | 'orphan';
export type BadgeShape = 'circle' | 'lock' | 'decay';

export interface BadgeSpec {
  id: BadgeId;
  glyph: string; // the non-color discriminator (never rely on color alone)
  shape: BadgeShape;
  fg: string; // glyph color
  bg: string; // fill color
  /** boundary stroke — the discriminator against the canvas surface when the fill is pale
   *  (warn's cream, read-only's tint). Absent ⇒ the fill itself carries the boundary. */
  ring?: string;
  meaning: string;
  /** claimable badges map to the fixed run-status / diagnostics vocabulary */
  claimable: boolean;
}

export const BADGES: Record<BadgeId, BadgeSpec> = {
  done: { id: 'done', glyph: '✓', shape: 'circle', fg: '#FFFFFF', bg: TOKENS.status.done, meaning: 'run done', claimable: true },
  running: { id: 'running', glyph: '▶', shape: 'circle', fg: TOKENS.stageNavy, bg: TOKENS.status.running, meaning: 'run running', claimable: true },
  warn: { id: 'warn', glyph: '⚠', shape: 'circle', fg: TOKENS.status.warnFg, bg: TOKENS.status.warnBg, ring: TOKENS.yellowDeep, meaning: 'diagnostics: warnings', claimable: true },
  error: { id: 'error', glyph: '✕', shape: 'circle', fg: '#FFFFFF', bg: TOKENS.status.error, meaning: 'run failed / diagnostics: errors', claimable: true },
  readOnly: { id: 'readOnly', glyph: '🔒', shape: 'lock', fg: '#33506e', bg: '#EDF2F9', ring: '#5B7EA6', meaning: 'read-only / derived — NEVER claimable', claimable: false },
  orphan: { id: 'orphan', glyph: '⚑', shape: 'decay', fg: '#FFFFFF', bg: TOKENS.gray.structure, meaning: 'orphaned layout — model object lost — NEVER claimable', claimable: false },
};

export const ALL_BADGES: BadgeSpec[] = Object.values(BADGES);
