import { DiagnosticCode, type SqlDialect } from '@modeler/parser';
import type { Span } from '@modeler/sql';
import { foldIdent } from './fold.js';
import type { SqlDiagnostic } from './resolve.js';

/** One SQL parameter usage: a masked `{param}` or a native `@p`/`:name`/`$1`. */
export interface SqlParamUsage {
  name: string;
  span: Span;
}

export interface SqlParamCheckResult {
  /** `sql-undeclared-param` (error) at each undeclared usage's span. */
  diagnostics: SqlDiagnostic[];
  /**
   * Declared parameter names never referenced by the SQL — the LSP emits
   * `sql-unused-param` (warning) at each parameter's TTR declaration span (which
   * lives in file coords, not the SQL value, so it can't be a SQL-span diag here).
   */
  unusedParamNames: string[];
}

const isPositional = (name: string): boolean => /^\d+$/.test(name) || name === '';

/**
 * Cross-check a query's embedded-SQL parameters against its declared
 * `parameters` (embedded-sql §3.5). Used params come from masked `{param}`
 * placeholders unioned with native bind params (`@p`/`:name`/`$1`); names are
 * folded per dialect (§6.2) before comparison.
 *
 * Conservative (Phase 3 DoD): positional params (`$1`/`?`) carry no name to
 * match, so they're never flagged undeclared, and their mere presence suppresses
 * *unused* warnings (a positional may bind any declared param).
 */
export function checkSqlParameters(args: {
  declared: ReadonlyArray<{ name: string }>;
  placeholders: ReadonlyArray<SqlParamUsage>;
  nativeParams: ReadonlyArray<SqlParamUsage>;
  dialect: SqlDialect;
}): SqlParamCheckResult {
  const { declared, placeholders, nativeParams, dialect } = args;
  const usages = [...placeholders, ...nativeParams];
  const declaredFolded = new Set(declared.map((d) => foldIdent(d.name, dialect)));
  const hasPositional = usages.some((u) => isPositional(u.name));

  const diagnostics: SqlDiagnostic[] = [];
  const usedFolded = new Set<string>();
  for (const u of usages) {
    if (isPositional(u.name)) continue; // can't match by name → never "undeclared"
    const folded = foldIdent(u.name, dialect);
    usedFolded.add(folded);
    if (!declaredFolded.has(folded)) {
      diagnostics.push({
        code: DiagnosticCode.SqlUndeclaredParam,
        message: `SQL parameter '${u.name}' is not declared in the query's parameters`,
        span: u.span,
        severity: 'error',
      });
    }
  }

  // Unused: a declared param the SQL never references. Suppressed entirely when a
  // positional param is present (it might bind any declaration).
  const unusedParamNames = hasPositional
    ? []
    : declared.filter((d) => !usedFolded.has(foldIdent(d.name, dialect))).map((d) => d.name);

  return { diagnostics, unusedParamNames };
}
