// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import hero from './fixtures/hero-getGraph.json';
import type { GetGraphResult } from '../graph/types.js';
import { deriveContainer } from '../graph/derive-orchestration.js';
import { toElements } from '../cy/adapter.js';
import { alteryxKnime } from '../skins/alteryx-knime.js';
import { enso } from '../skins/enso.js';

const result = hero as unknown as GetGraphResult;

/**
 * Per-canvas skin switch (T5.3.2, C1-b-ii/iii): alteryx-knime ⇄ enso changes node
 * labels/classes but keeps the element count identical and manual positions unchanged.
 */
describe('skins', () => {
  const elements = deriveContainer(result, 'crunch');
  const autoLayout = result.autoLayout.crunch;

  it('element count is identical across skins', () => {
    const a = toElements({ elements, skin: alteryxKnime, autoLayout });
    const e = toElements({ elements, skin: enso, autoLayout });
    expect(a.length).toBe(e.length);
  });

  it('labels differ (glyph-prefixed vs text-forward) and orientation flips', () => {
    const a = toElements({ elements, skin: alteryxKnime, autoLayout });
    const e = toElements({ elements, skin: enso, autoLayout });
    const aLoad = a.find((el) => el.data.id === 'crunch/sales#1')!;
    const eLoad = e.find((el) => el.data.id === 'crunch/sales#1')!;
    expect(String(aLoad.data.label)).toContain('sales#1');
    expect(aLoad.data.label).not.toBe(eLoad.data.label); // glyph prefix vs plain/provenance
    expect(alteryxKnime.orientation).toBe('LR');
    expect(enso.orientation).toBe('TD');
  });

  it('manual positions are identical across skins (skin never moves nodes)', () => {
    const manual = { 'crunch/sales#1': { zeta: 'crunch/sales#1', x: 42, y: 99 } };
    const a = toElements({ elements, skin: alteryxKnime, autoLayout, manual });
    const e = toElements({ elements, skin: enso, autoLayout, manual });
    const posA = a.find((el) => el.data.id === 'crunch/sales#1')!.position;
    const posE = e.find((el) => el.data.id === 'crunch/sales#1')!.position;
    expect(posA).toEqual({ x: 42, y: 99 });
    expect(posE).toEqual({ x: 42, y: 99 });
  });
});
