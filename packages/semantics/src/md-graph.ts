import type { MdMapDef } from '@tatrman/parser';
import type { ProjectSymbolTable } from './project-symbols.js';
import { resolveMdRef, underlyingDomainOf } from './md-resolve.js';
import type { MapEdge } from './md-lattice.js';

export interface MdMapGraph {
  nodes: string[];
  edges: MapEdge[];
}

/**
 * Lower a set of `def map`s to the domain-level edge graph the lattice operates
 * on (contracts §6.1): each `from` domain → `to` domain, with `oneToOne` from
 * the map's cardinality (a calc map is implicitly N:1). Attribute refs in
 * `from`/`to` are reduced to their underlying domain (the §5.3 sugar). A
 * multi-`from` map contributes one edge per `from` domain.
 */
export function buildMdMapGraph(symbols: ProjectSymbolTable, maps: readonly MdMapDef[]): MdMapGraph {
  const edges: MapEdge[] = [];
  const nodes = new Set<string>();
  for (const map of maps) {
    const oneToOne = !map.calc && map.cardinality === '1:1';
    const toDom = map.to[0] ? underlyingDomainOf(symbols, map.to[0])?.qname ?? map.to[0] : undefined;
    if (!toDom) continue;
    nodes.add(toDom);
    for (const fromRef of map.from) {
      const fromDom = underlyingDomainOf(symbols, fromRef)?.qname ?? fromRef;
      nodes.add(fromDom);
      edges.push({ from: fromDom, to: toDom, oneToOne, mapName: map.name });
    }
  }
  return { nodes: [...nodes], edges };
}

export interface LevelDomain {
  name: string;
  /** The level attribute's underlying domain qname (undefined if unresolved). */
  domain?: string;
  /** Whether the level attribute belongs to the hierarchy's dimension. */
  inDim: boolean;
}

/**
 * Resolve a hierarchy's bare level attribute names to their underlying domains,
 * within its `dimension:`. `inDim` is false when the named attribute isn't a
 * member of that dimension (→ `md/level-not-in-dim`).
 */
export function resolveLevelDomains(
  symbols: ProjectSymbolTable,
  dimensionRef: string | undefined,
  levels: readonly string[]
): LevelDomain[] {
  const dimQname = dimensionRef ? resolveMdRef(symbols, dimensionRef, 'dimension')?.qname : undefined;
  return levels.map((name) => {
    const attr = dimQname ? symbols.get(`${dimQname}.${name}`) : undefined;
    if (!attr) return { name, inDim: false };
    const domain = attr.domainRef ? resolveMdRef(symbols, attr.domainRef, 'domain')?.qname : undefined;
    return { name, domain, inDim: true };
  });
}
