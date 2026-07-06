import type { AreaDef, SourceLocation } from '@tatrman/parser';
import type { ProjectSymbolTable } from './project-symbols.js';
import type { Resolver } from './resolver.js';
import { elideRoot } from './derivation.js';

/** A `def area` definition paired with the file it was declared in. */
export interface AreaEntry {
  area: AreaDef;
  documentUri: string;
}

export interface ResolvedArea {
  name: string;
  /** RECURSIVE closure of `packages:`, canonical (root-prefixed) names, sorted. */
  resolvedPackages: string[];
  /** Canonical qnames from `entities:`, sorted. */
  resolvedEntities: string[];
  source: SourceLocation;
  documentUri: string;
}

/**
 * Recursive package closure of a single area `packages:` member (design §14.3).
 *
 * THIS IS THE ONLY RECURSIVE PREFIX-MATCH IN THE CODEBASE. Area membership
 * ("load X") is recursive — it pulls package `X` and every `X.*` descendant — in
 * deliberate contrast to `import X.*`, which is non-recursive (top-level defs of
 * `X` only; see the resolver's wildcard-import step). Both the member and each
 * candidate package are normalised through the `[packages].root` elision rule
 * (PD1.4) before matching, so a member that omits the configured root still
 * matches root-prefixed canonical names. The returned names are canonical.
 */
export function areaPackageClosure(
  symbols: ProjectSymbolTable,
  member: string,
  root = ''
): string[] {
  const target = elideRoot(member, root);
  const out: string[] = [];
  for (const pkg of symbols.listPackages()) {
    if (pkg === '') continue; // the default (empty) package is never a area member
    const rel = elideRoot(pkg, root);
    if (rel === target || rel.startsWith(`${target}.`)) out.push(pkg);
  }
  return out.sort();
}

/** Resolve one area block to its recursive package closure + entity set. */
export function resolveArea(
  symbols: ProjectSymbolTable,
  resolver: Resolver,
  entry: AreaEntry,
  root = ''
): ResolvedArea {
  const { area, documentUri } = entry;

  const pkgSet = new Set<string>();
  for (const member of area.packages) {
    for (const pkg of areaPackageClosure(symbols, member, root)) pkgSet.add(pkg);
  }

  const entSet = new Set<string>();
  for (const member of area.entities) {
    const res = resolver.resolveReference(
      { path: member, parts: member.split('.') },
      { schemaCode: '', namespace: '' }
    );
    if (res.resolved) entSet.add(res.symbol.qname);
  }

  return {
    name: area.name,
    resolvedPackages: [...pkgSet].sort(),
    resolvedEntities: [...entSet].sort(),
    source: area.source,
    documentUri,
  };
}

/**
 * Builds the project-wide area table. Keyed by area name; on a duplicate
 * name the first occurrence wins (the duplicate is reported separately by the
 * `ttr/duplicate-area` validator rule).
 */
export class AreaTableBuilder {
  constructor(
    private symbols: ProjectSymbolTable,
    private resolver: Resolver,
    private root = ''
  ) {}

  build(entries: AreaEntry[]): Map<string, ResolvedArea> {
    const map = new Map<string, ResolvedArea>();
    for (const entry of entries) {
      const resolved = resolveArea(this.symbols, this.resolver, entry, this.root);
      if (!map.has(resolved.name)) map.set(resolved.name, resolved);
    }
    return map;
  }
}
