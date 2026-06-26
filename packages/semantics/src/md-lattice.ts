/**
 * Pure graph algorithms over MD maps (contracts §6.1–6.3). No LSP/IO — the
 * caller lowers the model to nodes + edges (attribute maps already reduced to
 * their underlying domains) and consumes the results. Unit-testable in isolation.
 */

/** A directed map edge between two nodes (domains or attributes). */
export interface MapEdge {
  from: string;
  to: string;
  /** A 1:1 map connects co-leaves; an N:1 map coarsens (demotes leaf-ness). */
  oneToOne: boolean;
  /** The map's name, for hierarchy-step attribution / `via` matching. */
  mapName?: string;
}

/**
 * Leaves = nodes with **no incoming N:1 map** (§6.1). A 1:1 map does **not**
 * demote leaf-ness (co-leaves stay leaves).
 */
export function computeLeaves(nodes: readonly string[], edges: readonly MapEdge[]): Set<string> {
  const targetedByNto1 = new Set(edges.filter((e) => !e.oneToOne).map((e) => e.to));
  return new Set(nodes.filter((n) => !targetedByNto1.has(n)));
}

/**
 * Partition nodes into co-leaf classes via 1:1 maps (§6.2): two nodes are
 * co-leaves iff connected by a chain of 1:1 maps. Nodes sharing a domain but
 * with no 1:1 map are **not** co-leaves (so co-leaf-ness comes from maps).
 */
export function coLeafClasses(nodes: readonly string[], edges: readonly MapEdge[]): string[][] {
  const parent = new Map<string, string>(nodes.map((n) => [n, n]));
  const find = (x: string): string => {
    let r = x;
    while (parent.get(r) !== r) r = parent.get(r)!;
    return r;
  };
  const union = (a: string, b: string) => {
    if (!parent.has(a) || !parent.has(b)) return;
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent.set(ra, rb);
  };
  for (const e of edges) if (e.oneToOne) union(e.from, e.to);

  const groups = new Map<string, string[]>();
  for (const n of nodes) {
    const r = find(n);
    const g = groups.get(r) ?? [];
    g.push(n);
    groups.set(r, g);
  }
  return [...groups.values()];
}

/**
 * Reachability over the transitive closure of N:1 edges (§6.1): does `lower`
 * coarsen (eventually map) to `upper`? `lower === upper` is trivially true.
 */
export function grainReachable(edges: readonly MapEdge[]): (lower: string, upper: string) => boolean {
  const adj = new Map<string, Set<string>>();
  for (const e of edges) {
    if (e.oneToOne) continue;
    const s = adj.get(e.from) ?? new Set<string>();
    s.add(e.to);
    adj.set(e.from, s);
  }
  return (lower, upper) => {
    if (lower === upper) return true;
    const seen = new Set<string>([lower]);
    const stack = [lower];
    while (stack.length) {
      const x = stack.pop()!;
      for (const y of adj.get(x) ?? []) {
        if (y === upper) return true;
        if (!seen.has(y)) {
          seen.add(y);
          stack.push(y);
        }
      }
    }
    return false;
  };
}

/** The N:1 maps connecting `lower → upper` directly (§6.3 step candidates). */
export function connectingMaps(edges: readonly MapEdge[], lower: string, upper: string): MapEdge[] {
  return edges.filter((e) => !e.oneToOne && e.from === lower && e.to === upper);
}

export type StepResult =
  | { ok: true; mapName: string | undefined }
  | { error: 'none' | 'ambiguous' };

/**
 * Infer the connecting map for one consecutive `(lower, upper)` hierarchy step
 * (§6.3), without a `via:` override: a unique N:1 map is used; zero → `none`;
 * more than one → `ambiguous` (the author must add `via:`).
 */
export function inferStep(edges: readonly MapEdge[], lower: string, upper: string): StepResult {
  const conn = connectingMaps(edges, lower, upper);
  if (conn.length === 0) return { error: 'none' };
  if (conn.length > 1) return { error: 'ambiguous' };
  return { ok: true, mapName: conn[0].mapName };
}
