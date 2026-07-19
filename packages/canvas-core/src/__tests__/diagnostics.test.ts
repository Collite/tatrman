// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { DIAGNOSTICS, ALL_DIAGNOSTICS, type DsId } from '../diagnostics.js';

const EXPECTED_IDS: DsId[] = [
  'DS-SKIN-001', 'DS-SKIN-002', 'DS-SKIN-003',
  'DS-CANV-001', 'DS-CANV-002',
  'DS-PERSP-001', 'DS-PERSP-002',
  'DS-SHELL-001', 'DS-RUN-001', 'DS-EDIT-001',
];

const SEVERITIES = new Set(['error', 'warning', 'info', 'hint', 'badge']);

describe('DS-* diagnostics registry (contracts §8)', () => {
  it('every contract id is present and keyed by itself', () => {
    for (const id of EXPECTED_IDS) {
      expect(DIAGNOSTICS[id], id).toBeDefined();
      expect(DIAGNOSTICS[id].id).toBe(id);
    }
    expect(ALL_DIAGNOSTICS).toHaveLength(EXPECTED_IDS.length);
  });

  it('every id has a typed severity', () => {
    for (const d of ALL_DIAGNOSTICS) {
      expect(SEVERITIES.has(d.severity), `${d.id}: ${d.severity}`).toBe(true);
    }
  });

  it('every id has a fixture stub rendering non-empty user-facing text', () => {
    for (const d of ALL_DIAGNOSTICS) {
      const t = d.text({ skin: 'test.skin', slot: 'selection', node: 'n1', ref: 'x', target: 't', fallback: 'er.crow' });
      expect(typeof t, d.id).toBe('string');
      expect(t.trim().length, `${d.id} text empty`).toBeGreaterThan(0);
    }
  });

  it('pending ids name the phase that makes them fire', () => {
    for (const d of ALL_DIAGNOSTICS) {
      if (d.pending) expect(d.phase, `${d.id} pending without phase`).toMatch(/^DS-P\d/);
    }
  });

  it('v1 (DS-P6 exit): every shipped diagnostic is un-pended; only DS-CANV-001 (orphan live-detection) remains', () => {
    const stillPending = ALL_DIAGNOSTICS.filter((d) => d.pending).map((d) => d.id);
    // orphan-layout live detection (layout entry lost its model object) is not wired yet — the mark
    // renders, but nothing populates orphanIds on the live canvases. Honest: it stays pending.
    expect(stillPending).toEqual(['DS-CANV-001']);
  });
});
