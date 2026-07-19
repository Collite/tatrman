// SPDX-License-Identifier: Apache-2.0
//
// @tatrman/tokens — the shared Studio design tokens (FO-P1.S6, PF F).
//
// One dependency-free source of the suite's semantic palette/spacing/radii so
// the Studio Viewer, the authoring extension, and the launcher render as one
// product ("suite coherence is earned", P-2). Values are the palette already in
// use across the shell chrome, lifted to named semantic tokens — adopt these
// instead of inlining hex. Cross-repo surfaces (launcher, authoring extension)
// adopt this once it is published (RO-6/⚑2); see skin-deltas-fo-p1.md.

/** Semantic colors — named by role, not by hue. */
export const color = {
  /** the Studio brand navy — active affordances, headings. */
  brand: '#16283F',
  /** text/icon on a brand-filled surface. */
  onBrand: '#FFFFFF',
  /** the chrome accent (button text, active slate). */
  accent: '#33506E',
  /** the chrome accent border. */
  accentBorder: '#CBD8E6',
  /** app surface + neutral borders. */
  surface: '#FFFFFF',
  surfaceMuted: '#F9FAFB',
  border: '#E5E7EB',
  /** text roles. */
  textPrimary: '#16283F',
  textMuted: '#6B7280',
  /** feedback — warn / danger / success (bg + fg pairs). */
  warnBg: '#FEF3C7',
  warnFg: '#92400E',
  warnBorder: '#FDE68A',
  dangerBg: '#FEE2E2',
  dangerFg: '#B91C1C',
  successBg: '#DCFCE7',
  successFg: '#15803D',
} as const;

/** Spacing scale (px). */
export const space = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
} as const;

/** Corner radii (px). `pill` = fully rounded. */
export const radius = {
  sm: 6,
  md: 10,
  pill: 999,
} as const;

/** Font sizes (px). */
export const fontSize = {
  xs: 11,
  sm: 12,
  md: 13,
  lg: 16,
  xl: 24,
} as const;

/** The full token set, for a single-import consumer. */
export const tokens = { color, space, radius, fontSize } as const;

export type ColorToken = keyof typeof color;
export type SpaceToken = keyof typeof space;
export type RadiusToken = keyof typeof radius;
