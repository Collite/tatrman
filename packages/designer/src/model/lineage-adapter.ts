// SPDX-License-Identifier: Apache-2.0
// Designer-side lineage composition (DS-P4.S2). Assembles the pure generator's LineageModel from
// the LIVE er↔db binds (modeler/getBindings) overlaid on the hero fixture channel (md↔er derives +
// program provenance, which the grammar/backend can't yet provide — see lineage-fixtures.ts).
// The generator (@tatrman/perspectives) does the scoping/direction/degradation; this is transport→
// model glue only.

import type { BindingMapData } from '@tatrman/lsp';
import type { LineageModel, LineageObject, LineageLink } from '@tatrman/perspectives';
import { HERO_LINEAGE_OBJECTS, HERO_LINEAGE_LINKS } from './lineage-fixtures.js';

function leaf(qname: string): string {
  return qname.split('.').pop() ?? qname;
}

/**
 * Compose the cross-face lineage model: the fixture objects/links (md↔er, program) + LIVE er↔db
 * `binds` links from the binding map. db columns / er attributes referenced only by a live bind
 * are materialized as objects too (so a bind chain never dangles). Pure.
 */
export function composeLineageModel(bindings: BindingMapData | null): LineageModel {
  const objByQname = new Map<string, LineageObject>(HERO_LINEAGE_OBJECTS.map((o) => [o.qname, o]));
  const links: LineageLink[] = [...HERO_LINEAGE_LINKS];

  for (const a of bindings?.attributes ?? []) {
    // live er↔db bind: db column feeds er attribute
    links.push({ from: a.columnQname, to: a.attributeQname, relation: 'binds' });
    if (!objByQname.has(a.columnQname)) objByQname.set(a.columnQname, { qname: a.columnQname, kind: 'column', label: leaf(a.columnQname), face: 'db' });
    if (!objByQname.has(a.attributeQname)) objByQname.set(a.attributeQname, { qname: a.attributeQname, kind: 'attribute', label: leaf(a.attributeQname), face: 'er' });
  }

  // dedupe links (a fixture bind + a live bind for the same pair collapse)
  const seen = new Set<string>();
  const deduped = links.filter((l) => {
    const k = `${l.from}→${l.to}:${l.relation}`;
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });

  return { objects: [...objByQname.values()], links: deduped };
}
