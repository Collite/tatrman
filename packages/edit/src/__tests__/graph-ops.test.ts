// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { buildInsertNodeOnEdge, insertionNodeId, type EdgeInsertionTarget } from '../graph-ops.js';

// DS-P6.S1 — the C1-d structured graph-op vocabulary. The insertion builder is PURE and
// DETERMINISTIC: the same (target, nodeKind) always yields byte-identical ops with the same
// synthesized node id — this is what makes the three insertion doors (palette / on-edge + / ⌘K)
// emit identical sequences (the D-6 one-vocabulary guarantee lives in this single builder).

const target: EdgeInsertionTarget = {
  edgeId: 'e_transfer',
  from: { node: 'extract', port: 'extract.orders' },
  to: { node: 'crunch', port: 'crunch.in_orders' },
  role: 'transfer',
};

describe('buildInsertNodeOnEdge', () => {
  it('splits the edge: insert-node, disconnect, connect(before), connect(after) in a fixed order', () => {
    const ops = buildInsertNodeOnEdge(target, 'filter');
    const id = insertionNodeId(target, 'filter');
    expect(ops).toEqual([
      { op: 'insert-node', nodeKind: 'filter', id },
      { op: 'disconnect', edgeId: 'e_transfer' },
      { op: 'connect', from: { node: 'extract', port: 'extract.orders' }, to: { node: id, port: `${id}::in` }, role: 'transfer' },
      { op: 'connect', from: { node: id, port: `${id}::out` }, to: { node: 'crunch', port: 'crunch.in_orders' }, role: 'transfer' },
    ]);
  });

  it('is deterministic — same inputs ⇒ byte-identical ops + node id (the one-vocabulary basis)', () => {
    expect(buildInsertNodeOnEdge(target, 'filter')).toEqual(buildInsertNodeOnEdge(target, 'filter'));
    expect(insertionNodeId(target, 'filter')).toBe(insertionNodeId(target, 'filter'));
  });

  it('derives a distinct id per (edge, kind); the SAME (edge, kind) is intentionally identical', () => {
    expect(insertionNodeId(target, 'filter')).not.toBe(insertionNodeId(target, 'map')); // different kind
    expect(insertionNodeId({ ...target, edgeId: 'e_other' }, 'filter')).not.toBe(insertionNodeId(target, 'filter')); // different edge
    // same (edge, kind) → same id BY DESIGN (byte-identical across doors); a real insert consumes
    // the edge (disconnect), so the same edgeId cannot be re-targeted after apply → no live collision.
    expect(insertionNodeId(target, 'filter')).toBe(insertionNodeId(target, 'filter'));
  });

  it('preserves the split edge role (a control edge stays control)', () => {
    const ctl: EdgeInsertionTarget = { ...target, edgeId: 'e_after', role: 'control' };
    const ops = buildInsertNodeOnEdge(ctl, 'gate');
    expect(ops.filter((o) => o.op === 'connect').every((o) => (o as { role: string }).role === 'control')).toBe(true);
  });
});
