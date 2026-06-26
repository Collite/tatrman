import type { CrossRef } from '@modeler/parser';
import type { ProjectSymbolTable } from './project-symbols.js';
import type { SymbolEntry } from './symbol-table.js';

/**
 * Role → target namespace for an MD cross-reference (contracts §5). `grain`
 * resolves to a dimension attribute (`md.dimension.<Dim>.<attr>`).
 */
const ROLE_NS: Record<CrossRef['role'], string> = {
  domain: 'domain',
  map: 'map',
  dimension: 'dimension',
  measure: 'measure',
  hierarchy: 'hierarchy',
  grain: 'dimension',
};

/**
 * Candidate qnames for an authored MD ref under a target namespace. Authors omit
 * the namespace segment (`from: md.Day`, `grain: [Customer.code]`); symbols are
 * registered with it (`md.domain.Day`, `md.dimension.Customer.code`). We insert
 * the namespace after the `md` schema prefix (or prepend `md.<ns>` for a
 * namespace-less ref). The last candidate is the canonical form used for the
 * package-qualified suffix fallback.
 */
function candidates(path: string, ns: string): string[] {
  const parts = path.split('.');
  const out: string[] = [];
  if (parts[0] === 'md' && parts[1] === ns) out.push(path); // already canonical
  const canonical =
    parts[0] === 'md' ? ['md', ns, ...parts.slice(1)].join('.') : ['md', ns, ...parts].join('.');
  out.push(canonical);
  return [...new Set(out)];
}

/**
 * Resolve a single MD cross-reference to its symbol, or undefined. Tries the
 * role's namespace; for a `domain`-role ref that is dotted (the attribute→domain
 * map sugar, design §5.3) it also accepts a dimension attribute. Falls back to a
 * package-qualified suffix match (`<pkg>.md.<ns>.<rest>`).
 */
export function resolveMdRef(
  symbols: ProjectSymbolTable,
  path: string,
  role: CrossRef['role']
): SymbolEntry | undefined {
  const namespaces = [ROLE_NS[role]];
  // map `from`/`to` may name an attribute instead of a domain (the sugar).
  if (role === 'domain' && path.includes('.') && !path.startsWith('md.')) namespaces.push('dimension');

  for (const ns of namespaces) {
    const cands = candidates(path, ns);
    for (const c of cands) {
      const hit = symbols.get(c);
      if (hit) return hit;
    }
    const want = cands[cands.length - 1];
    for (const e of symbols.all()) {
      if (e.qname === want || e.qname.endsWith(`.${want}`)) return e;
    }
  }
  return undefined;
}

/**
 * The underlying **domain** an MD ref ranges over, for the leaf/grain lattice
 * (2E): a domain ref resolves to itself; an attribute ref (the sugar) lowers to
 * its `domain:`. Returns the resolved domain symbol, or undefined if neither the
 * ref nor its domain resolves.
 */
export function underlyingDomainOf(
  symbols: ProjectSymbolTable,
  path: string,
  role: CrossRef['role'] = 'domain'
): SymbolEntry | undefined {
  const sym = resolveMdRef(symbols, path, role);
  if (!sym) return undefined;
  if (sym.kind === 'mdDomain') return sym;
  if (sym.kind === 'attribute' && sym.domainRef) {
    return resolveMdRef(symbols, sym.domainRef, 'domain');
  }
  return sym;
}
