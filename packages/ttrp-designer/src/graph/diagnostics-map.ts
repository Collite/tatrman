// SPDX-License-Identifier: Apache-2.0
import type { GetGraphResult, NodeView } from './types.js';
import type { PublishedDiagnostic } from '../lsp/ws-client.js';

export interface NodeBadge {
  zeta: string;
  count: number;
  maxSeverity: number;
  messages: string[];
}

/** True when a diagnostic's range overlaps a node's source range. */
function overlaps(d: PublishedDiagnostic, n: NodeView): boolean {
  if (!n.range) return false;
  const ds = d.range.start.line;
  const de = d.range.end.line;
  return de >= n.range.line && ds <= n.range.endLine;
}

/**
 * Map published diagnostics to node badges via getGraph source ranges (T5.4.7). Per-node
 * badges in a drill-in; aggregated per-container badge in the orchestration view. Severity 1
 * = Error > 2 = Warning (LSP), so the badge's `maxSeverity` is the numeric MIN.
 */
export function badgeNodes(result: GetGraphResult, diagnostics: PublishedDiagnostic[]): Map<string, NodeBadge> {
  const byZeta = new Map<string, NodeBadge>();
  const add = (n: NodeView, d: PublishedDiagnostic) => {
    const sev = d.severity ?? 1;
    const cur = byZeta.get(n.zeta) ?? { zeta: n.zeta, count: 0, maxSeverity: 4, messages: [] };
    cur.count += 1;
    cur.maxSeverity = Math.min(cur.maxSeverity, sev);
    cur.messages.push(d.message);
    byZeta.set(n.zeta, cur);
  };
  for (const c of result.graph.containers) {
    for (const n of c.nodes) for (const d of diagnostics) if (overlaps(d, n)) add(n, d);
  }
  for (const n of result.graph.leaves) for (const d of diagnostics) if (overlaps(d, n)) add(n, d);
  return byZeta;
}

/** Aggregate per-node badges to a per-container badge for the orchestration view (count + max severity). */
export function aggregateToContainers(result: GetGraphResult, nodeBadges: Map<string, NodeBadge>): Map<string, NodeBadge> {
  const byContainer = new Map<string, NodeBadge>();
  for (const c of result.graph.containers) {
    let count = 0;
    let maxSeverity = 4;
    const messages: string[] = [];
    for (const n of c.nodes) {
      const b = nodeBadges.get(n.zeta);
      if (!b) continue;
      count += b.count;
      maxSeverity = Math.min(maxSeverity, b.maxSeverity);
      messages.push(...b.messages);
    }
    if (count > 0) byContainer.set(c.path, { zeta: c.path, count, maxSeverity, messages });
  }
  return byContainer;
}
