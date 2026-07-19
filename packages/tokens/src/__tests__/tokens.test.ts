// SPDX-License-Identifier: Apache-2.0
//
// FO-P1.S6.T1 — the token audit anchor: the shared palette is a stable contract,
// so a change here is a deliberate suite-wide restyle, not an accident.

import { describe, it, expect } from 'vitest';
import { color, space, radius, fontSize, tokens } from '../index.js';

describe('@tatrman/tokens', () => {
  it('exposes the semantic brand + chrome colors', () => {
    expect(color.brand).toBe('#16283F');
    expect(color.accent).toBe('#33506E');
    expect(color.accentBorder).toBe('#CBD8E6');
  });

  it('every color is a 6-digit hex', () => {
    for (const [name, value] of Object.entries(color)) {
      expect(value, name).toMatch(/^#[0-9A-F]{6}$/);
    }
  });

  it('exposes a monotonic spacing scale and the radii', () => {
    expect(space.xs < space.sm && space.sm < space.md && space.md < space.lg).toBe(true);
    expect(radius.pill).toBe(999);
    expect(fontSize.sm).toBe(12);
  });

  it('bundles the full set under `tokens`', () => {
    expect(tokens.color.brand).toBe(color.brand);
    expect(tokens.space).toBe(space);
  });
});
