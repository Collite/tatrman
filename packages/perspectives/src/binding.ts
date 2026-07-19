// SPDX-License-Identifier: Apache-2.0
import type {
  PerspectiveGenerator, PerspectiveResult,
  BindingInput, BindingMap, BindingRibbon, BindingRow, PModelGraph,
  EntityRef, TableRef, QueryCard,
} from './types.js';

export class NotImplementedYet extends Error {
  constructor(what: string, phase: string) {
    super(`${what} is not implemented yet — lands in ${phase}.`);
    this.name = 'NotImplementedYet';
  }
}

/** Last dot-separated segment — the human name inside a qname (`a.b.Customer` → `Customer`). */
function leaf(qname: string): string {
  const i = qname.lastIndexOf('.');
  return i === -1 ? qname : qname.slice(i + 1);
}

/** Label for a qname: the model-graph node's own label if we have it, else the qname leaf. */
function labelOf(qname: string, graph: PModelGraph): string {
  const node = graph.nodes.find((n) => n.qname === qname);
  return node?.label ?? leaf(qname);
}

function entityRef(qname: string, er: PModelGraph): EntityRef {
  return { qname, label: labelOf(qname, er) };
}
function tableRef(qname: string, db: PModelGraph): TableRef {
  return { qname, label: labelOf(qname, db) };
}

/**
 * Binding perspective (C-2, er↔db only). Pure: assembles the ribbon from the binding-model
 * data — one row per bound entity at rest, query-bound entities as first-class QueryCards, a
 * dangling bind kept as a DS-PERSP-002 `unresolved` row (present, never dropped). A selected
 * entity expands to its attribute→column pairs. No transport, no React.
 */
export function generateBindingRibbon(input: BindingInput): BindingRibbon {
  const { er, db, bindings, selectedEntity } = input;
  const queryByQname = new Map((bindings.queries ?? []).map((q) => [q.qname, q]));

  const rows: BindingRow[] = bindings.entities.map((b): BindingRow => {
    const entity = entityRef(b.entityQname, er);
    if (b.target.kind === 'table') {
      return { kind: 'table', entity, table: tableRef(b.target.tableQname, db) };
    }
    if (b.target.kind === 'query') {
      const meta = queryByQname.get(b.target.queryQname);
      const query: QueryCard = {
        qname: b.target.queryQname,
        predicate: meta?.predicate ?? '',
        provenance: (meta?.provenance ?? []).map((t) => tableRef(t, db)),
      };
      return { kind: 'query', entity, query };
    }
    // unresolved → shown on the ribbon with the warning treatment, not hidden (P-3 / G-5).
    return { kind: 'unresolved', entity, diagnostic: 'DS-PERSP-002', detail: b.target.raw };
  });

  const ribbon: BindingRibbon = { rows };

  if (selectedEntity) {
    const prefix = `${selectedEntity}.`;
    const pairs = bindings.attributes
      .filter((a) => a.attributeQname.startsWith(prefix))
      .map((a) => ({ attribute: leaf(a.attributeQname), column: leaf(a.columnQname) }));
    ribbon.expanded = { entity: selectedEntity, pairs };
  }

  return ribbon;
}

/**
 * Binding perspective generator (C-2). Wraps {@link generateBindingRibbon} in the pinned
 * `PerspectiveResult` (custom → the purpose-built `binding-ribbon` view; C-1 γ).
 */
export const bindingGenerator: PerspectiveGenerator<BindingInput, PerspectiveResult> = {
  id: 'binding',
  generate(input: BindingInput): PerspectiveResult {
    return { kind: 'custom', view: 'binding-ribbon', data: generateBindingRibbon(input) };
  },
};

// re-export for the (still-deferred) lineage generator until DS-P4.S2 lands its body
export type { BindingMap };
