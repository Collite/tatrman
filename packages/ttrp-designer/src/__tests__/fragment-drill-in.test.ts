import { describe, it, expect } from 'vitest';
import hero from './fixtures/hero-getGraph.json';
import type { ContainerView, GetGraphResult } from '../graph/types.js';
import { containerByPath, isContainerReadOnly, readOnlyBanner } from '../graph/read-only.js';

const result = hero as unknown as GetGraphResult;

/**
 * Fragment drill-in (T5.3.7): a `"""sql` container is read-only + auto-only with a
 * "derived from <dialect> fragment" banner; a canonical container is editable.
 */
describe('fragment drill-in read-only', () => {
  it('acc_prep (a """sql fragment container) is read-only with a dialect banner', () => {
    const accPrep = containerByPath(result, 'acc_prep')!;
    expect(accPrep.fragment).toBe('sql');
    expect(isContainerReadOnly(accPrep)).toBe(true);
    expect(readOnlyBanner(accPrep)).toContain('sql');
  });

  it('crunch (canonical container) is editable', () => {
    const crunch = containerByPath(result, 'crunch')!;
    expect(isContainerReadOnly(crunch)).toBe(false);
    expect(readOnlyBanner(crunch)).toBeNull();
  });

  it('a synthetic derived sub-graph is read-only regardless of fragment tag', () => {
    const derived: ContainerView = { path: 'x', target: 't', derived: true, fragment: null, ports: {}, nodes: [], edges: [] };
    expect(isContainerReadOnly(derived)).toBe(true);
    expect(readOnlyBanner(derived)).toContain('read-only');
  });
});
