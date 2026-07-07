import { describe, it, expect } from 'vitest';
import { resetToAuto, snapshotToManual, isOrphaned } from '../layout-actions.js';

/** Auto→manual flip + reset-to-auto + orphan badge (T5.3.6). */
describe('layout actions', () => {
  it('snapshotToManual captures all rendered positions as a manual canvas', () => {
    const layout = snapshotToManual('program', { acc_prep: { x: 10, y: 20 }, crunch: { x: 230, y: 20 } }, 'alteryx-knime');
    expect(layout.mode).toBe('manual');
    expect(layout.skin).toBe('alteryx-knime');
    expect(layout.nodes).toContainEqual({ zeta: 'acc_prep', x: 10, y: 20 });
    expect(layout.nodes).toHaveLength(2);
  });

  it('resetToAuto drops node positions', () => {
    const layout = resetToAuto('crunch', 'enso');
    expect(layout.mode).toBe('auto');
    expect(layout.nodes).toHaveLength(0);
  });

  it('isOrphaned flags a reset ζ', () => {
    expect(isOrphaned(['crunch/sales#2'], 'crunch/sales#2')).toBe(true);
    expect(isOrphaned(['crunch/sales#2'], 'crunch/sales#1')).toBe(false);
  });
});
