import { describe, it, expect } from 'vitest';
import hero from './fixtures/hero-getGraph.json';
import type { GetGraphResult } from '../graph/types.js';
import { deriveOrchestration } from '../graph/derive-orchestration.js';
import { toElements } from '../cy/adapter.js';
import { coordToPixels } from '../cy/orientation.js';
import { alteryxKnime } from '../skins/alteryx-knime.js';
import { enso } from '../skins/enso.js';

const result = hero as unknown as GetGraphResult;

/**
 * Binary layout modes (T5.3.2, C1-b): auto canvases render from the server `autoLayout`
 * (orientation-mapped), manual positions override, and orientation transposes the mapping.
 */
describe('layout modes', () => {
  it('auto: node positions come from the server autoLayout, orientation-mapped (LR)', () => {
    const els = toElements({ elements: deriveOrchestration(result), skin: alteryxKnime, autoLayout: result.autoLayout.program });
    const crunch = els.find((e) => e.data.id === 'crunch')!;
    // crunch is {layer:1,index:0} → LR ⇒ x = layer*220, y = index*110.
    expect(crunch.position).toEqual(coordToPixels(result.autoLayout.program.crunch, 'LR'));
    expect(crunch.position!.x).toBeGreaterThan(0);
  });

  it('orientation transposes the same abstract coord (LR x-major vs TD y-major)', () => {
    const coord = result.autoLayout.program.crunch;
    const lr = coordToPixels(coord, 'LR');
    const td = coordToPixels(coord, 'TD');
    expect(lr.x).toBe(td.y);
    expect(lr.y).toBe(td.x);
  });

  it('manual override wins over autoLayout', () => {
    const manual = { crunch: { zeta: 'crunch', x: 5, y: 7 } };
    const els = toElements({ elements: deriveOrchestration(result), skin: enso, autoLayout: result.autoLayout.program, manual });
    expect(els.find((e) => e.data.id === 'crunch')!.position).toEqual({ x: 5, y: 7 });
  });
});
