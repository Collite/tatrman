// The lineage composition fixture channel (DS-P4.S2). Column-grain cross-face lineage needs
// links that today's grammar + backend can't provide:
//   · md↔er:   an md measure can't declare the er attribute it derives from (grammar gap — the
//              same family as the md/cnc gaps, feeds Q-5). Filled here.
//   · program: which program reads/writes which column comes from the ProcessingGraphSource; the
//              v1 impl is the hero fixture (monthly_sales.fixture.json), not a live server.
//   · runs:    run instances need a PlatformRunsSource (absent in v1 ⇒ γ degrades, DS-PERSP-001).
// The er↔db binds ARE live (from modeler/getBindings); the adapter overlays them onto this. EVERY
// entry corresponds to a line in samples/orders-hero/LINEAGE-GAPS.md — delete as the grammar /
// backend lands. Keyed by canonical qname so a live-selected object roots the lineage exactly.

import type { LineageObject, LineageLink } from '@tatrman/perspectives';

const Q = {
  dbNet: 'orders_hero.db.dbo.table.OrderLine.NetAmount',
  dbQty: 'orders_hero.db.dbo.table.OrderLine.Quantity',
  erNet: 'orders_hero.er.entity.OrderLine.net_amount',
  erQty: 'orders_hero.er.entity.OrderLine.quantity',
  mdNet: 'orders_hero.md.measure.net_amount',
  mdQty: 'orders_hero.md.measure.qty',
  mdMargin: 'orders_hero.md.measure.margin_pct', // grammar gap (derived measure) — fixture-filled
  program: 'orders_hero.program.monthly_sales',
} as const;

/** The hero md-face + program objects the grammar/backend can't yet materialize as lineage nodes. */
export const HERO_LINEAGE_OBJECTS: LineageObject[] = [
  { qname: Q.dbNet, kind: 'column', label: 'NetAmount', face: 'db' },
  { qname: Q.dbQty, kind: 'column', label: 'Quantity', face: 'db' },
  { qname: Q.erNet, kind: 'attribute', label: 'net_amount', face: 'er' },
  { qname: Q.erQty, kind: 'attribute', label: 'quantity', face: 'er' },
  { qname: Q.mdNet, kind: 'measure', label: 'net_amount', face: 'md' },
  { qname: Q.mdQty, kind: 'measure', label: 'qty', face: 'md' },
  { qname: Q.mdMargin, kind: 'calc', label: 'margin_pct', face: 'md' },
  { qname: Q.program, kind: 'program', label: 'monthly_sales', face: 'program' },
];

/** Cross-face links the grammar/backend can't state (md↔er derives, program provenance). The
 *  er↔db `binds` links are overlaid LIVE by the adapter from getBindings. */
export const HERO_LINEAGE_LINKS: LineageLink[] = [
  // md↔er derivation (grammar gap)
  { from: Q.erNet, to: Q.mdNet, relation: 'derives' },
  { from: Q.erQty, to: Q.mdQty, relation: 'derives' },
  // calc dependent (γ)
  { from: Q.mdNet, to: Q.mdMargin, relation: 'derives' },
  // program provenance (ProcessingGraphSource fixture): monthly_sales reads the order-line columns
  { from: Q.program, to: Q.dbNet, relation: 'reads' },
  { from: Q.program, to: Q.dbQty, relation: 'reads' },
];

export const HERO_LINEAGE_QNAMES = Q;
