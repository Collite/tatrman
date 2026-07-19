// The fixture-fill channel (DS-P3.S1.T4). Forward-design (Q-5): some notation the modeling
// skins WANT to show is not expressible in today's TTR.g4 (derived measures; conceptual roles
// and synonyms). The LSP never fabricates these — it extracts grammar-truth only. Instead the
// designer merges them here, into each node's `slotExtra`, which canvas-core spreads into
// slotData for the skins to read. EVERY fixture entry corresponds to a logged gap in
// samples/orders-hero/MD-GAPS.md / CNC-GAPS.md — when the grammar grows to express one, its
// entry is deleted here and the extraction moves into the LSP.

import type { ModelGraph } from '@tatrman/lsp';

export interface NodeFixture {
  /** md: derived/ratio measures the grammar can't author (MD-GAPS: margin_pct). */
  calcs?: string[];
  /** cnc: the conceptual role a concept plays — not inline-declarable today (CNC-GAPS: roles). */
  role?: string;
  /** cnc: alternate business names — no synonym metadata today (CNC-GAPS: synonyms). */
  synonyms?: string[];
}

export type GraphFixtures = Record<string, NodeFixture>;

/**
 * The orders-hero fixture fills. Keyed by node qname. Each entry is a logged grammar gap; see
 * the "Fixture-filled in the designer" section of the two GAPS docs.
 *
 * SCOPE: these are hero-corpus demo fills, matched by absolute qname. Before real multi-project
 * use they must be gated to the hero (or removed as each gap's grammar lands) so a user's model
 * can never accidentally match a key. The cnc entries are keyed on the qname a future `concept`
 * def kind would produce (`orders_hero.cnc.entity.*`); until that kind exists the cnc canvas is
 * blocked (DS-P3 review) and these do not attach — kept as the intended fill, forward-looking.
 */
export const HERO_FIXTURES: GraphFixtures = {
  // md — the Sales cube's derived margin measure (net_amount / cost), not expressible on a measure.
  'orders_hero.md.cubelet.Sales': { calcs: ['margin_pct'] },
  // cnc — the roles the hero concepts play, and a synonym or two, that cnc can't state inline.
  'orders_hero.cnc.entity.Customer': { role: 'master', synonyms: ['client'] },
  'orders_hero.cnc.entity.Order': { role: 'transaction' },
  'orders_hero.cnc.entity.Product': { role: 'master', synonyms: ['SKU'] },
};

/**
 * Merge fixture fills onto a model graph's nodes (by qname), landing them in `slotExtra`. Pure;
 * nodes without a fixture pass through untouched. db/er graphs have no hero fixtures → no-op.
 */
export function applyGraphFixtures(graph: ModelGraph, fixtures: GraphFixtures = HERO_FIXTURES): ModelGraph {
  const nodes = graph.nodes.map((n) => {
    const fx = fixtures[n.qname];
    return fx ? { ...n, slotExtra: fx } : n;
  });
  return { ...graph, nodes };
}
