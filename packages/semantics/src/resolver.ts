import type { ImportDecl } from '@modeler/parser';
import type { SymbolEntry } from './symbol-table.js';
import { ProjectSymbolTable } from './project-symbols.js';

export type ResolutionStep =
  | 'lexical'
  | 'same-package'
  | 'named-import'
  | 'wildcard-import'
  | 'auto-import'
  | 'fully-qualified';

export interface ResolutionAttempt {
  step: ResolutionStep;
  candidate: string;
  reason?:
    | 'unknown-symbol'
    | 'not-imported'
    | 'wildcard-non-recursive'
    | 'shadowed-by-named-import'
    | 'lexical-scope-empty'
    | 'ambiguous';
}

export type ResolutionResult =
  | { resolved: true; symbol: SymbolEntry; viaStep: ResolutionStep }
  | { resolved: false; reason: 'not-found' | 'ambiguous'; tried: ResolutionAttempt[]; candidates?: SymbolEntry[] };

export interface LexicalScope {
  schemaCode: string;
  namespace: string;
  enclosing?: { kind: 'entity' | 'table' | 'view' | 'procedure'; qname: string };
}

export interface ResolutionContext {
  schemaCode: string;
  namespace: string;
  /**
   * Optional enclosing-def qname (e.g. `er.entity.artikl`). When present, a
   * bare-id reference is first tried as a child of this enclosing def (so
   * `nameAttribute: id` inside `entity artikl` resolves to
   * `er.entity.artikl.id`).
   */
  enclosingQname?: string;
  /**
   * Optional document-level imports. When present, steps 3 (named-import) and
   * 4 (wildcard-import) use these to resolve bare references.
   */
  imports?: ImportDecl[];
  /**
   * The package name of the document containing the reference. Used for step 2
   * (same-package) lookups.
   */
  packageName?: string;
}



function attempt(step: ResolutionStep, candidate: string, reason?: ResolutionAttempt['reason']): ResolutionAttempt {
  return { step, candidate, reason };
}

export class Resolver {
  /**
   * `root` is the configured `[packages].root` prefix (PD1.4). When set, a
   * reference is resolved by trying both its written form and its
   * root-normalised variant, so a reference may freely include or omit the
   * prefix (B17 elision) and still reach the canonical symbol.
   */
  constructor(private symbols: ProjectSymbolTable, private root = '') {}

  /** Direct symbol-table lookup by fully-qualified name. */
  getSymbol(qname: string): SymbolEntry | undefined {
    return this.symbols.get(qname);
  }

  /**
   * The root-elision variants of a dotted name: the name itself, plus the name
   * with the configured `root` stripped (if present) or prepended (if absent).
   * Order preserves the written form first so `tried[]` reflects intent.
   */
  private rootVariants(name: string): string[] {
    if (!this.root) return [name];
    const variants = [name];
    if (name === this.root || name.startsWith(`${this.root}.`)) {
      const stripped = name === this.root ? '' : name.slice(this.root.length + 1);
      if (stripped && stripped !== name) variants.push(stripped);
    } else {
      variants.push(`${this.root}.${name}`);
    }
    return variants;
  }

  /** Symbol-table lookup that also tries the root-elision variants of `qname`. */
  private getCanonical(qname: string): SymbolEntry | undefined {
    for (const cand of this.rootVariants(qname)) {
      const symbol = this.symbols.get(cand);
      if (symbol) return symbol;
    }
    return undefined;
  }

  resolveReference(ref: { path: string; parts: string[] }, context: ResolutionContext): ResolutionResult {
    const tried: ResolutionAttempt[] = [];
    const enclosingCandidate = context.enclosingQname ? `${context.enclosingQname}.${ref.path}` : undefined;

    if (enclosingCandidate) {
      tried.push(attempt('lexical', enclosingCandidate));
      const symbol = this.symbols.get(enclosingCandidate);
      if (symbol) return { resolved: true, symbol, viaStep: 'lexical' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    const fullQname = `${context.schemaCode}.${context.namespace}.${ref.path}`;
    if (fullQname !== context.enclosingQname) {
      tried.push(attempt('same-package', fullQname));
      const symbol = this.getCanonical(fullQname);
      if (symbol) return { resolved: true, symbol, viaStep: 'same-package' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    if (context.packageName) {
      const pkgSymbols = this.symbols.getByPackage(context.packageName);
      for (const entry of pkgSymbols) {
        if (entry.name === ref.path) {
          const candidate = entry.qname;
          if (candidate !== enclosingCandidate && candidate !== fullQname) {
            return { resolved: true, symbol: entry, viaStep: 'same-package' };
          }
        }
      }
    }

    if (context.imports) {
      for (const imp of context.imports) {
        if (imp.wildcard) continue;
        if (imp.target.endsWith(`.${ref.path}`)) {
          const symbol = this.getCanonical(imp.target);
          if (symbol) return { resolved: true, symbol, viaStep: 'named-import' };
        }
      }

      const wildcardMatches: SymbolEntry[] = [];
      for (const imp of context.imports) {
        if (!imp.wildcard) continue;
        const pkg = imp.target;
        const matches = this.symbols.getByPackage(pkg).filter(
          (e) => e.name === ref.path && e.qname !== fullQname
        );
        for (const m of matches) {
          if (!wildcardMatches.find((w) => w.qname === m.qname)) {
            wildcardMatches.push(m);
          }
        }
      }
      if (wildcardMatches.length > 1) {
        for (const m of wildcardMatches) {
          tried.push(attempt('wildcard-import', m.qname, 'ambiguous'));
        }
        return { resolved: false, reason: 'ambiguous', tried, candidates: wildcardMatches };
      }
      if (wildcardMatches.length === 1) {
        tried.push(attempt('wildcard-import', wildcardMatches[0].qname));
        return { resolved: true, symbol: wildcardMatches[0], viaStep: 'wildcard-import' };
      }
    }

    const cncQname = `cnc.cnc.role.${ref.path}`;
    if (cncQname !== fullQname && cncQname !== context.enclosingQname) {
      tried.push(attempt('auto-import', cncQname));
      const cncSymbol = this.symbols.get(cncQname);
      if (cncSymbol) return { resolved: true, symbol: cncSymbol, viaStep: 'auto-import' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    // Stock CNC symbols (stock:// URI with schema cnc) are stored under
    // `cnc.cnc.role.<name>` by DocumentSymbolTable.makeQname (isStockCnc flag).
    // Non-stock CNC files write `schema cnc namespace role` directly and their
    // symbols are stored under `cnc.role.<name>` — that case is handled by
    // the fully-qualified step 6 lookup, not a separate auto-import branch.
    // Fully-qualified: try the written form and its root-elision variants
    // (B17). The first variant that yields a unique match wins; an exact
    // canonical lookup short-circuits ahead of suffix matching.
    for (const cand of this.rootVariants(ref.path)) {
      const exact = this.symbols.get(cand);
      if (exact && exact.qname !== fullQname && exact.qname !== cncQname) {
        tried.push(attempt('fully-qualified', exact.qname));
        return { resolved: true, symbol: exact, viaStep: 'fully-qualified' };
      }
      const uniqueMatches = this.symbols.getBySuffix(cand).filter(
        (e) => e.qname !== fullQname && e.qname !== cncQname
      );
      if (uniqueMatches.length === 1) {
        tried.push(attempt('fully-qualified', uniqueMatches[0].qname));
        return { resolved: true, symbol: uniqueMatches[0], viaStep: 'fully-qualified' };
      }
    }

    return { resolved: false, reason: 'not-found', tried };
  }

  resolveBareId(name: string, scope: LexicalScope): ResolutionResult {
    const tried: ResolutionAttempt[] = [];

    if (scope.enclosing) {
      const withEnclosing = `${scope.enclosing.qname}.${name}`;
      tried.push(attempt('lexical', withEnclosing));
      const symbol = this.symbols.get(withEnclosing);
      if (symbol) return { resolved: true, symbol, viaStep: 'lexical' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    const withSchema = `${scope.schemaCode}.${scope.namespace}.${name}`;
    if (withSchema !== scope.enclosing?.qname) {
      tried.push(attempt('same-package', withSchema));
      const symbol = this.symbols.get(withSchema);
      if (symbol) return { resolved: true, symbol, viaStep: 'same-package' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    const cncQname = `cnc.cnc.role.${name}`;
    if (cncQname !== withSchema) {
      tried.push(attempt('auto-import', cncQname));
      const cncSymbol = this.symbols.get(cncQname);
      if (cncSymbol) return { resolved: true, symbol: cncSymbol, viaStep: 'auto-import' };
      tried[tried.length - 1].reason = 'unknown-symbol';
    }

    return { resolved: false, reason: 'not-found', tried };
  }
}
