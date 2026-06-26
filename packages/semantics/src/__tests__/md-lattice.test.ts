import { describe, it, expect } from 'vitest';
import {
  computeLeaves,
  coLeafClasses,
  grainReachable,
  inferStep,
  type MapEdge,
} from '../md-lattice.js';

const n1 = (from: string, to: string, mapName?: string): MapEdge => ({ from, to, oneToOne: false, mapName });
const o2o = (from: string, to: string): MapEdge => ({ from, to, oneToOne: true });

describe('computeLeaves (§6.1)', () => {
  it('the design Time chain leaves only Day', () => {
    const nodes = ['Day', 'Month', 'Quarter', 'Year'];
    const edges = [n1('Day', 'Month'), n1('Month', 'Quarter'), n1('Quarter', 'Year')];
    expect([...computeLeaves(nodes, edges)]).toEqual(['Day']);
  });

  it('a 1:1 map does NOT demote leaf-ness', () => {
    const nodes = ['code', 'id'];
    expect([...computeLeaves(nodes, [o2o('code', 'id')]).values()].sort()).toEqual(['code', 'id']);
  });

  it('the RAE 2:1 composite map: Account & CostCenter stay leaves, Activity does not', () => {
    const nodes = ['Account', 'CostCenter', 'Activity'];
    const edges = [n1('Account', 'Activity'), n1('CostCenter', 'Activity')];
    expect([...computeLeaves(nodes, edges)].sort()).toEqual(['Account', 'CostCenter']);
  });
});

describe('coLeafClasses (§6.2)', () => {
  it('1:1 maps merge co-leaves; shared-domain-without-1:1 stays separate', () => {
    const nodes = ['code', 'id', 'other'];
    const classes = coLeafClasses(nodes, [o2o('code', 'id')]).map((c) => c.sort());
    expect(classes).toContainEqual(['code', 'id']);
    expect(classes).toContainEqual(['other']);
  });
});

describe('grainReachable (§6.1 transitive closure)', () => {
  it('Day coarsens to Year transitively', () => {
    const reach = grainReachable([n1('Day', 'Month'), n1('Month', 'Quarter'), n1('Quarter', 'Year')]);
    expect(reach('Day', 'Year')).toBe(true);
    expect(reach('Year', 'Day')).toBe(false);
    expect(reach('Month', 'Month')).toBe(true);
  });
});

describe('inferStep (§6.3)', () => {
  it('a unique N:1 map is the step', () => {
    expect(inferStep([n1('Day', 'Month', 'd2m')], 'Day', 'Month')).toEqual({ ok: true, mapName: 'd2m' });
  });
  it('zero connecting maps → none', () => {
    expect(inferStep([n1('Day', 'Month')], 'Day', 'Year')).toEqual({ error: 'none' });
  });
  it('two connecting maps → ambiguous', () => {
    expect(inferStep([n1('Day', 'Month', 'a'), n1('Day', 'Month', 'b')], 'Day', 'Month')).toEqual({
      error: 'ambiguous',
    });
  });
});
