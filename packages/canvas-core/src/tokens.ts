// SPDX-License-Identifier: Apache-2.0
// Base-layer visual tokens (contracts §2 / sweep S-3). Pure data — no React (PF wall).
// ice = light surface; Stage Navy = THE dark theme; Tatrman Yellow strictly living/active.

export const TOKENS = {
  ice: '#E9F0F8',
  card: '#FFFFFF',
  stageNavy: '#16283F',
  yellow: '#FFCB2E', // living / active ONLY — never structure
  yellowDeep: '#F2A200', // living / active ONLY
  gray: { structure: '#4A4B4D', muted: '#96989B', line: '#CBD8E6' },
  status: {
    done: '#3E7D4E',
    running: '#FFCB2E', // running IS an active state — yellow allowed here
    warnBg: '#FBF2DA',
    warnFg: '#8a6a10',
    error: '#B3261E',
  },
} as const;

export type Theme = 'ice' | 'stage-navy'; // Stage Navy = THE dark theme (S-3)

/** Surface color per theme — badges/chrome are drawn over one of these. */
export const SURFACE: Record<Theme, string> = {
  ice: TOKENS.ice,
  'stage-navy': TOKENS.stageNavy,
};

/**
 * Tokens that carry *living/active* meaning — the only place Tatrman Yellow may appear
 * (S-3). Everything else structural is gray/ice/navy. Enforced by tokens.test.ts.
 */
export const LIVING_TOKENS = [TOKENS.yellow, TOKENS.yellowDeep, TOKENS.status.running] as const;

/** Structural tokens — must never be yellow (S-3: structure is gray). */
export const STRUCTURAL_TOKENS = [
  TOKENS.ice,
  TOKENS.card,
  TOKENS.stageNavy,
  TOKENS.gray.structure,
  TOKENS.gray.muted,
  TOKENS.gray.line,
] as const;
