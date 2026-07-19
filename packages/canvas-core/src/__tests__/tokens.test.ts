// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { TOKENS, SURFACE, STRUCTURAL_TOKENS } from '../tokens.js';
import { ALL_BADGES, BADGES } from '../badges.js';
import { contrast } from '../contrast.js';

const AA = 4.5;

describe('tokens — AA contrast (contracts §2, S-3)', () => {
  it('every status fg/bg pair meets WCAG AA (≥ 4.5:1)', () => {
    const pairs: Array<[string, string, string]> = [
      ['done', '#FFFFFF', TOKENS.status.done],
      ['running', TOKENS.stageNavy, TOKENS.status.running],
      ['warn', TOKENS.status.warnFg, TOKENS.status.warnBg],
      ['error', '#FFFFFF', TOKENS.status.error],
    ];
    for (const [name, fg, bg] of pairs) {
      expect(contrast(fg, bg), `${name}: ${fg} on ${bg}`).toBeGreaterThanOrEqual(AA);
    }
  });

  it('every badge glyph/fill pair meets AA on its own fill', () => {
    for (const b of ALL_BADGES) {
      expect(contrast(b.fg, b.bg), `badge ${b.id}: ${b.fg} on ${b.bg}`).toBeGreaterThanOrEqual(AA);
    }
  });

  it('every badge has a visible boundary against both theme surfaces (ring when the fill is pale)', () => {
    // the badge's boundary is its ring when present, else its fill — it must not vanish
    for (const b of ALL_BADGES) {
      const boundary = b.ring ?? b.bg;
      for (const theme of ['ice', 'stage-navy'] as const) {
        expect(contrast(boundary, SURFACE[theme]), `badge ${b.id} boundary vs ${theme}`).toBeGreaterThan(1.25);
      }
    }
  });
});

describe('yellow discipline (S-3: yellow = living/active only; structure is gray)', () => {
  it('no structural token is Tatrman Yellow', () => {
    for (const t of STRUCTURAL_TOKENS) {
      expect(t).not.toBe(TOKENS.yellow);
      expect(t).not.toBe(TOKENS.yellowDeep);
    }
  });

  it('the only living/active use of yellow among status tokens is `running`', () => {
    expect(TOKENS.status.running).toBe(TOKENS.yellow);
    expect(TOKENS.status.done).not.toBe(TOKENS.yellow);
    expect(TOKENS.status.error).not.toBe(TOKENS.yellow);
  });
});

describe('badge vocabulary — shape+color, never color alone (contracts §2)', () => {
  it('every badge carries a non-color discriminator (a glyph)', () => {
    for (const b of ALL_BADGES) {
      expect(b.glyph.length).toBeGreaterThan(0);
    }
  });

  it('badge glyphs are mutually distinct (color is never the sole signal)', () => {
    const glyphs = ALL_BADGES.map((b) => b.glyph);
    expect(new Set(glyphs).size).toBe(glyphs.length);
  });

  it('never-claimable badges (readOnly, orphan) are marked non-claimable (invariant 2)', () => {
    expect(BADGES.readOnly.claimable).toBe(false);
    expect(BADGES.orphan.claimable).toBe(false);
  });
});
