// SPDX-License-Identifier: Apache-2.0
// Designer-side binding adapter (DS-P4.S1). Turns the LSP's canonicalized BindingMapData into
// the two shapes the render layer needs: the er-canvas `bindingHints` decoration (S-5) and the
// generator input for the binding ribbon (C-2). The generator itself lives in @tatrman/perspectives
// (pure); this adapter is the thin transport→view glue.

import type { BindingMapData } from '@tatrman/lsp';
import type { BindingHint } from '@tatrman/canvas-core';

/** Short target label for a ghost chip: `dbo.Customer` (schema.table) or the query/entity leaf.
 *  Exported for unit test — the qname shapes here are the adapter's most bug-prone surface. */
export function shortTarget(qname: string): string {
  const parts = qname.split('.');
  // canonical db table: <pkg>.db.<schema>.table.<Name> → show <schema>.<Name>. Use lastIndexOf so
  // an earlier segment that happens to be literally "table" can't hijack the split.
  const ti = parts.lastIndexOf('table');
  if (ti > 0 && parts[ti - 1]) return `${parts[ti - 1]}.${parts.slice(ti + 1).join('.')}`;
  return parts[parts.length - 1] ?? qname;
}

/**
 * Build the er-canvas show-bindings decoration: entity node qname → ghost chip. Keyed by the
 * canonical entity qname, which IS the er CanvasGraph node id (model-graph-map maps id=qname).
 */
export function buildBindingHints(bindings: BindingMapData): Record<string, BindingHint> {
  const hints: Record<string, BindingHint> = {};
  for (const e of bindings.entities) {
    if (e.target.kind === 'table') hints[e.entityQname] = { target: shortTarget(e.target.tableQname), kind: 'table' };
    else if (e.target.kind === 'query') hints[e.entityQname] = { target: shortTarget(e.target.queryQname), kind: 'query' };
    else hints[e.entityQname] = { target: e.target.raw ? shortTarget(e.target.raw) : 'unresolved', kind: 'unresolved' };
  }
  return hints;
}
