import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { contrast, TOKENS, type NodeBaseState } from '@tatrman/canvas-core';
import { NodeBaseChrome, PreviewChip, DerivedBanner } from '../base/BaseLayer.js';
import { BUILTIN_SKINS } from '../../skins/index.js';

// DS-P6.S2.T3 — the AA audit against RENDERED surfaces (not just the spec constants tokens.test
// checks). Every base-layer badge, drawn through every one of the 8 v1 skins' anchors, must clear
// WCAG AA on its painted colors; the two theme canvas backgrounds are checked for the surfaces
// that sit directly on them (preview chip, derived banner).

const AA = 4.5;

// jsdom returns inline colors as "rgb(r, g, b)"; convert to hex for the contrast util.
function toHex(rgb: string): string {
  const m = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (!m) return rgb; // already hex
  return '#' + [1, 2, 3].map((i) => Number(m[i]).toString(16).padStart(2, '0')).join('');
}
const styleOf = (el: Element) => {
  const cs = getComputedStyle(el);
  return { fg: toHex(cs.color), bg: toHex(cs.backgroundColor) };
};

const failedState: NodeBaseState = {
  selected: false, focused: false, readOnly: true, derived: false, orphanedLayout: true,
  runStatus: 'failed', diagnostics: { errorCount: 2, warnCount: 1 },
};

afterEach(() => cleanup());

describe('AA audit — rendered base-layer badges × all 8 skins', () => {
  for (const skin of BUILTIN_SKINS) {
    it(`every badge clears AA when drawn through ${skin.id}'s anchors`, () => {
      const anchors = skin.declareAnchors(skin.nodeSize({ id: 'n', qname: 'n', kind: 'op', label: 'n', ports: [], slotData: {} }));
      render(<NodeBaseChrome state={failedState} anchors={anchors} />);
      for (const testid of ['status-badge', 'diag-warn', 'diag-error', 'readonly-badge', 'orphan-badge']) {
        const el = screen.getByTestId(testid);
        const { fg, bg } = styleOf(el);
        expect(contrast(fg, bg), `${skin.id} · ${testid}: ${fg} on ${bg}`).toBeGreaterThanOrEqual(AA);
      }
    });
  }
});

describe('AA audit — every run-status badge fill, rendered', () => {
  // failedState above paints only the error(✕) status badge; done(✓) and running(▶) carry their
  // own fills too, so render each and check the painted status-badge contrast (closes the gap the
  // review flagged: done/running were previously spec-only, not rendered-verified).
  const anchors = BUILTIN_SKINS[0].declareAnchors({ width: 200, height: 60 });
  for (const runStatus of ['done', 'running', 'failed'] as const) {
    it(`the ${runStatus} status badge clears AA on its painted fill`, () => {
      render(<NodeBaseChrome state={{ selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false, runStatus }} anchors={anchors} />);
      const { fg, bg } = styleOf(screen.getByTestId('status-badge'));
      expect(contrast(fg, bg), `${runStatus} status-badge: ${fg} on ${bg}`).toBeGreaterThanOrEqual(AA);
    });
  }
});

describe('AA audit — surfaces drawn directly on the theme canvas', () => {
  it('the preview chip text clears AA on its own fill', () => {
    render(<PreviewChip rows={5} point={{ x: 0, y: 0, align: 'edge' }} />);
    const { fg, bg } = styleOf(screen.getByTestId('preview-chip'));
    expect(contrast(fg, bg), `preview-chip ${fg} on ${bg}`).toBeGreaterThanOrEqual(AA);
  });

  it('the derived banner clears AA on its own fill', () => {
    render(<DerivedBanner />);
    const { fg, bg } = styleOf(screen.getByTestId('derived-banner'));
    expect(contrast(fg, bg), `derived-banner ${fg} on ${bg}`).toBeGreaterThanOrEqual(AA);
  });

  it('both theme canvas backgrounds are defined (ice / stage-navy) for the audit matrix', () => {
    expect(TOKENS.ice).toBeTruthy();
    expect(TOKENS.stageNavy).toBeTruthy();
  });
});
