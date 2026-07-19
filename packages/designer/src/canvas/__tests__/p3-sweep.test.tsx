import { describe, it, expect } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { NodeBaseState } from '@tatrman/canvas-core';
import { NodeBaseChrome } from '../base/BaseLayer.js';
import { BUILTIN_SKINS } from '../../skins/index.js';

// THE cross-skin invariant (P-3): same NodeBaseState ⇒ same base chrome elements in EVERY
// skin. Grows every roster phase (DS-P3/P5 add skins to BUILTIN_SKINS only).

const base = { selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false } as const;
const MATRIX: Array<{ name: string; state: NodeBaseState; expect: string[] }> = [
  { name: 'selected', state: { ...base, selected: true }, expect: ['selection-ring'] },
  { name: 'read-only', state: { ...base, readOnly: true }, expect: ['readonly-badge'] },
  { name: 'orphaned', state: { ...base, orphanedLayout: true }, expect: ['orphan-badge'] },
  { name: 'failed+diags', state: { ...base, runStatus: 'failed', diagnostics: { errorCount: 1, warnCount: 1 } }, expect: ['status-badge', 'diag-warn', 'diag-error'] },
  { name: 'done', state: { ...base, runStatus: 'done' }, expect: ['status-badge'] },
];

describe('P-3 sweep — base chrome identical across every registered skin', () => {
  for (const cell of MATRIX) {
    it(`state "${cell.name}" produces the same chrome in all ${BUILTIN_SKINS.length} skins`, () => {
      for (const skin of BUILTIN_SKINS) {
        const anchors = skin.declareAnchors(skin.nodeSize({ id: 'n', qname: 'n', kind: 'entity', label: 'n', ports: [], slotData: { rows: [{}] } }));
        const { container } = render(<NodeBaseChrome state={cell.state} anchors={anchors} claims={skin.claims} />);
        for (const testid of cell.expect) {
          expect(container.querySelector(`[data-testid="${testid}"]`), `${skin.id} missing ${testid} for ${cell.name}`).not.toBeNull();
        }
        cleanup();
      }
    });
  }

  it('never-claimable chrome is drawn even when a skin (illegally cast) claims it', () => {
    // Construct the pathological input the name promises: a claims object that ILLEGALLY names
    // never-claimable slots (selection/chrome/orphan). The registry rejects this at registration
    // (DS-SKIN-001, proven in the registry suite), but even if it slipped through, the base layer
    // OWNS never-claimable chrome and draws it regardless of any claim.
    const illegalClaims = { status: true, diagnostics: true, selection: true, chrome: true, orphanedLayout: true } as unknown as { status?: true; diagnostics?: true };
    const anchors = BUILTIN_SKINS[0].declareAnchors({ width: 200, height: 60 });
    render(<NodeBaseChrome state={{ ...base, selected: true, readOnly: true, orphanedLayout: true }} anchors={anchors} claims={illegalClaims} />);
    // never-claimable slots render despite the (illegal) claim
    expect(screen.getByTestId('selection-ring')).toBeInTheDocument();
    expect(screen.getByTestId('readonly-badge')).toBeInTheDocument();
    expect(screen.getByTestId('orphan-badge')).toBeInTheDocument();
    cleanup();
  });
});
