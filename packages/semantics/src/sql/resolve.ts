import { DiagnosticCode, type SqlDialect } from '@modeler/parser';
import type { SqlRefModel, SqlTableRef, Span } from '@modeler/sql';
import type { ProjectSymbolTable } from '../project-symbols.js';
import { type SqlConfig, defaultsFor, namespaceFor } from '../sql-config.js';
import { foldEq } from './fold.js';

/**
 * A SQL reference-resolution diagnostic (embedded-sql §3.4). Spans are in the
 * (masked) SQL **value** coordinate system — the LSP maps them to file positions
 * via the §8 source map (`sqlPosToFile`). Severity is `warning`: SQL analysis is
 * best-effort and config-dependent, so a stray finding must never be a hard error.
 */
export interface SqlDiagnostic {
  code: DiagnosticCode;
  message: string;
  span: Span;
  severity: 'error' | 'warning';
}

export interface SqlResolveContext {
  dialect: SqlDialect;
  config: SqlConfig;
  symbols: ProjectSymbolTable;
  /**
   * Masked `{param}` placeholder spans (from `maskPlaceholders`, coords into the
   * same value `extract` ran on). A masked placeholder lexes as a bare identifier,
   * so `extract` surfaces it as a phantom column ref — these are filtered out of
   * column resolution here (they're checked against declared `parameters` in §3.5
   * instead). Omit when there are none.
   */
  placeholders?: ReadonlyArray<{ offset: number; length: number }>;
}

/** A modelled `db` table/view with its column names (raw, pre-fold). */
interface DbTable {
  name: string;
  columns: string[];
}

/**
 * Index every modelled `db` table/view by its TTR namespace. A `db` table's
 * qname is `[pkg.]db.<namespace>.<name>` and its columns are
 * `<tableQname>.<column>` (see {@link import('../symbol-table.js')}), so the
 * namespace is the segment immediately before the (table-name) tail.
 */
function buildDbIndex(symbols: ProjectSymbolTable): Map<string, DbTable[]> {
  const all = symbols.all();
  const colsByParent = new Map<string, string[]>();
  for (const e of all) {
    if (e.kind === 'column' && e.parent) {
      const list = colsByParent.get(e.parent) ?? [];
      list.push(e.name);
      colsByParent.set(e.parent, list);
    }
  }
  const byNamespace = new Map<string, DbTable[]>();
  for (const e of all) {
    if ((e.kind !== 'table' && e.kind !== 'view') || e.schemaCode !== 'db') continue;
    const segs = e.qname.split('.');
    const namespace = segs[segs.length - 2];
    if (!namespace) continue;
    const list = byNamespace.get(namespace) ?? [];
    list.push({ name: e.name, columns: colsByParent.get(e.qname) ?? [] });
    byNamespace.set(namespace, list);
  }
  return byNamespace;
}

type TableResolution =
  | { status: 'resolved'; columns: string[] }
  | { status: 'unknown' }
  | { status: 'skip' };

/**
 * Resolve SQL table/column references against the TTR `db` symbol table and
 * report unresolved/ambiguous ones. Conservative by design (Phase 3 DoD — keep
 * the false-positive rate low):
 *
 * - A base table is only flagged `unknown` when its `(database, schema)` maps to
 *   a TTR namespace that is *actually modelled* (≥1 table). An unmapped or
 *   un-modelled namespace, or an under-qualified name with no `defaults`, is
 *   silently skipped — the project simply hasn't described that surface yet.
 * - Column checks run only on a *fully analyzable* query: no parse errors and no
 *   sub-queries (the flat `columns` list can't be attributed to a nested scope),
 *   and only against base tables whose columns are modelled.
 */
export function resolveSqlReferences(model: SqlRefModel, ctx: SqlResolveContext): SqlDiagnostic[] {
  const { dialect, config } = ctx;
  const dbIndex = buildDbIndex(ctx.symbols);
  const diagnostics: SqlDiagnostic[] = [];
  const placeholders = ctx.placeholders ?? [];
  const isPlaceholder = (span: Span): boolean =>
    placeholders.some((p) => span.offset >= p.offset && span.offset < p.offset + p.length);

  const reduce = (parts: string[]): { database: string; schema: string; table: string } | undefined => {
    const n = parts.length;
    if (n === 0) return undefined;
    const def = defaultsFor(config, dialect);
    const table = parts[n - 1]!;
    const schema = parts[n - 2] ?? def?.schema;
    const database = parts[n - 3] ?? def?.database;
    if (!schema || !database) return undefined;
    return { database, schema, table };
  };

  const resolveTable = (ref: SqlTableRef): TableResolution => {
    if (ref.origin !== 'base') return { status: 'skip' }; // CTE/derived resolve in-query
    const reduced = reduce(ref.name);
    if (!reduced) return { status: 'skip' };
    const namespace = namespaceFor(config, reduced.database, reduced.schema);
    if (!namespace) return { status: 'skip' };
    const candidates = dbIndex.get(namespace);
    if (!candidates || candidates.length === 0) return { status: 'skip' }; // namespace not modelled
    const match = candidates.find((t) => foldEq(t.name, reduced.table, dialect));
    return match ? { status: 'resolved', columns: match.columns } : { status: 'unknown' };
  };

  // --- Table resolution (over every table ref, incl. nested) -----------------
  for (const ref of model.tables) {
    if (resolveTable(ref).status === 'unknown') {
      diagnostics.push({
        code: DiagnosticCode.SqlUnknownTable,
        message: `Table '${ref.name.join('.')}' is not defined in the model`,
        span: ref.span,
        severity: 'warning',
      });
    }
  }

  // --- Column resolution (analyzable queries only) ---------------------------
  const analyzable = model.parseErrors.length === 0 && model.tables.length === model.rootScope.tables.length;
  if (analyzable) {
    const scope = model.rootScope.tables.map((ref) => ({ ref, res: resolveTable(ref) }));
    // Bare columns need complete column knowledge for the whole scope.
    const allResolvedWithColumns =
      scope.length > 0 &&
      scope.every((s) => s.res.status === 'resolved' && s.res.columns.length > 0);

    for (const col of model.columns) {
      if (col.name === '*') continue;
      if (isPlaceholder(col.span)) continue; // a masked `{param}`, not a column
      if (col.qualifier) {
        const q = col.qualifier;
        const target = scope.find(
          (s) =>
            (s.ref.alias && foldEq(s.ref.alias, q, dialect)) ||
            (!s.ref.alias && s.ref.name.length > 0 && foldEq(s.ref.name[s.ref.name.length - 1]!, q, dialect)),
        );
        if (!target) continue; // qualifier not an outer-scope table → skip (FP-safe)
        if (target.res.status !== 'resolved' || target.res.columns.length === 0) continue;
        if (!target.res.columns.some((c) => foldEq(c, col.name, dialect))) {
          diagnostics.push({
            code: DiagnosticCode.SqlColumnNotOnAlias,
            message: `Column '${col.name}' is not defined on '${q}'`,
            span: col.span,
            severity: 'warning',
          });
        }
      } else {
        if (!allResolvedWithColumns) continue;
        const matches = scope.filter(
          (s) => s.res.status === 'resolved' && s.res.columns.some((c) => foldEq(c, col.name, dialect)),
        );
        if (matches.length === 0) {
          diagnostics.push({
            code: DiagnosticCode.SqlUnknownColumn,
            message: `Column '${col.name}' is not defined on any table in scope`,
            span: col.span,
            severity: 'warning',
          });
        } else if (matches.length >= 2) {
          diagnostics.push({
            code: DiagnosticCode.SqlAmbiguousColumn,
            message: `Column '${col.name}' is ambiguous — defined on more than one table in scope`,
            span: col.span,
            severity: 'warning',
          });
        }
      }
    }
  }

  return diagnostics;
}
