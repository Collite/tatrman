// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { manifestToProgramGraph, type BundleManifestV2 } from '../manifest-program-graph.js';

const hero = (): BundleManifestV2 =>
  JSON.parse(
    readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'manifest-v2-hero.json'), 'utf8'),
  );

describe('manifestToProgramGraph (E-5 manifest → program graph, S9.T1)', () => {
  it('islands become nodes labeled engine/executor', () => {
    const g = manifestToProgramGraph(hero());
    expect(g.nodes.map((n) => n.id).sort()).toEqual(['acc_prep', 'notify_failure', 'summarize']);
    const accPrep = g.nodes.find((n) => n.id === 'acc_prep')!;
    expect(accPrep.engine).toBe('erp_pg');
    expect(accPrep.executor).toBe('bash');
    expect(accPrep.label).toContain('erp_pg/bash');
  });

  it('transfers become edges via staging', () => {
    const g = manifestToProgramGraph(hero());
    const transfers = g.edges.filter((e) => e.kind === 'transfer');
    expect(transfers).toEqual([
      { id: 'transfer:x0', from: 'acc_prep', to: 'summarize', kind: 'transfer', via: 'stage' },
    ]);
  });

  it('waves render as columns/levels (each node maps to its wave index; transfer waves are spacers)', () => {
    const g = manifestToProgramGraph(hero());
    expect(g.waves).toEqual([['acc_prep'], ['x0'], ['summarize'], ['notify_failure']]);
    expect(g.nodes.find((n) => n.id === 'acc_prep')!.wave).toBe(0);
    expect(g.nodes.find((n) => n.id === 'summarize')!.wave).toBe(2);
    expect(g.nodes.find((n) => n.id === 'notify_failure')!.wave).toBe(3);
  });

  it('onFailureOf renders as a distinct error edge', () => {
    const g = manifestToProgramGraph(hero());
    const errors = g.edges.filter((e) => e.kind === 'error');
    expect(errors).toEqual([{ id: 'error:acc_prep->notify_failure', from: 'acc_prep', to: 'notify_failure', kind: 'error' }]);
  });

  it('the lineage section is NOT rendered here (separate panel, PL-P2 feeds it)', () => {
    const g = manifestToProgramGraph(hero());
    // No lineage-derived node (e.g. the upstream `shop.sales.db.dbo.ORDER_LINE`) leaks into the graph.
    expect(g.nodes.some((n) => n.id.includes('ORDER_LINE'))).toBe(false);
    expect(g.edges.every((e) => e.kind === 'transfer' || e.kind === 'error')).toBe(true);
  });

  it('is deterministic (same manifest ⇒ same graph)', () => {
    expect(manifestToProgramGraph(hero())).toEqual(manifestToProgramGraph(hero()));
  });
});
