import type { DomainBlock, SourceLocation } from '@modeler/parser';
import type { ProjectSymbolTable } from './project-symbols.js';
import type { Resolver } from './resolver.js';
import { elideRoot } from './derivation.js';

/** A `.ttrd` domain block paired with the file it was declared in. */
export interface DomainEntry {
  block: DomainBlock;
  documentUri: string;
}

export interface ResolvedDomain {
  name: string;
  /** RECURSIVE closure of `packages:`, canonical (root-prefixed) names, sorted. */
  resolvedPackages: string[];
  /** Canonical qnames from `entities:`, sorted. */
  resolvedEntities: string[];
  source: SourceLocation;
  documentUri: string;
}

/**
 * Recursive package closure of a single domain `packages:` member (design §14.3).
 *
 * THIS IS THE ONLY RECURSIVE PREFIX-MATCH IN THE CODEBASE. Domain membership
 * ("load X") is recursive — it pulls package `X` and every `X.*` descendant — in
 * deliberate contrast to `import X.*`, which is non-recursive (top-level defs of
 * `X` only; see the resolver's wildcard-import step). Both the member and each
 * candidate package are normalised through the `[packages].root` elision rule
 * (PD1.4) before matching, so a member that omits the configured root still
 * matches root-prefixed canonical names. The returned names are canonical.
 */
export function domainPackageClosure(
  symbols: ProjectSymbolTable,
  member: string,
  root = ''
): string[] {
  const target = elideRoot(member, root);
  const out: string[] = [];
  for (const pkg of symbols.listPackages()) {
    if (pkg === '') continue; // the default (empty) package is never a domain member
    const rel = elideRoot(pkg, root);
    if (rel === target || rel.startsWith(`${target}.`)) out.push(pkg);
  }
  return out.sort();
}

/** Resolve one domain block to its recursive package closure + entity set. */
export function resolveDomain(
  symbols: ProjectSymbolTable,
  resolver: Resolver,
  entry: DomainEntry,
  root = ''
): ResolvedDomain {
  const { block, documentUri } = entry;

  const pkgSet = new Set<string>();
  for (const member of block.packages) {
    for (const pkg of domainPackageClosure(symbols, member, root)) pkgSet.add(pkg);
  }

  const entSet = new Set<string>();
  for (const member of block.entities) {
    const res = resolver.resolveReference(
      { path: member, parts: member.split('.') },
      { schemaCode: '', namespace: '' }
    );
    if (res.resolved) entSet.add(res.symbol.qname);
  }

  return {
    name: block.name,
    resolvedPackages: [...pkgSet].sort(),
    resolvedEntities: [...entSet].sort(),
    source: block.source,
    documentUri,
  };
}

/**
 * Builds the project-wide domain table. Keyed by domain name; on a duplicate
 * name the first occurrence wins (the duplicate is reported separately by the
 * `ttr/duplicate-domain` validator rule).
 */
export class DomainTableBuilder {
  constructor(
    private symbols: ProjectSymbolTable,
    private resolver: Resolver,
    private root = ''
  ) {}

  build(entries: DomainEntry[]): Map<string, ResolvedDomain> {
    const map = new Map<string, ResolvedDomain>();
    for (const entry of entries) {
      const resolved = resolveDomain(this.symbols, this.resolver, entry, this.root);
      if (!map.has(resolved.name)) map.set(resolved.name, resolved);
    }
    return map;
  }
}
