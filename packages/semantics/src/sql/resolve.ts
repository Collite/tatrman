import { DiagnosticCode, type SqlDialect } from '@modeler/parser';
import type { SqlRefModel, SqlTableRef, SqlColumnRef, Span } from '@modeler/sql';
import type { ProjectSymbolTable } from '../project-symbols.js';
import type { SymbolEntry } from './../symbol-table.js';
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

/** A modelled `db` table/view with its column symbol entries. */
export interface DbTableInfo {
  entry: SymbolEntry;
  columns: SymbolEntry[];
}

/** Namespace → modelled `db` tables/views. */
export type SqlDbIndex = Map<string, DbTableInfo[]>;

/**
 * Index every modelled `db` table/view by its db schema handle. A `db` table's
 * v4.0 qname is `[pkg.]db.<schema>.<kind>.<name>` and its columns are
 * `<tableQname>.<column>` (see {@link import('../symbol-table.js')}), so the
 * schema is the segment immediately after the `db` model segment. Exported so
 * the SQL reference index (§4.3) can reuse a single build per project pass.
 */
export function buildSqlDbIndex(symbols: ProjectSymbolTable): SqlDbIndex {
  const all = symbols.all();
  const colsByParent = new Map<string, SymbolEntry[]>();
  for (const e of all) {
    if (e.kind === 'column' && e.parent) {
      const list = colsByParent.get(e.parent) ?? [];
      list.push(e);
      colsByParent.set(e.parent, list);
    }
  }
  const byNamespace: SqlDbIndex = new Map();
  for (const e of all) {
    if ((e.kind !== 'table' && e.kind !== 'view') || e.schemaCode !== 'db') continue;
    // Strip any package prefix → `db.<schema>.<kind>.<name>`; the schema handle
    // is the segment right after the `db` model segment.
    const rest = e.packageName && e.qname.startsWith(e.packageName + '.')
      ? e.qname.slice(e.packageName.length + 1)
      : e.qname;
    const segs = rest.split('.');
    const namespace = segs[0] === 'db' ? segs[1] : undefined;
    if (!namespace) continue;
    const list = byNamespace.get(namespace) ?? [];
    list.push({ entry: e, columns: colsByParent.get(e.qname) ?? [] });
    byNamespace.set(namespace, list);
  }
  return byNamespace;
}

type TableResolution =
  | { status: 'resolved'; info: DbTableInfo }
  | { status: 'unknown' }
  | { status: 'skip' };

function reduceName(
  parts: readonly string[],
  dialect: SqlDialect,
  config: SqlConfig,
): { database: string; schema: string; table: string } | undefined {
  const n = parts.length;
  if (n === 0) return undefined;
  const def = defaultsFor(config, dialect);
  const table = parts[n - 1]!;
  const schema = parts[n - 2] ?? def?.schema;
  const database = parts[n - 3] ?? def?.database;
  if (!schema || !database) return undefined;
  return { database, schema, table };
}

/** Resolve a single base table ref to its modelled `db` table info (or skip/unknown). */
function resolveTableInfo(ref: SqlTableRef, ctx: SqlResolveContext, index: SqlDbIndex): TableResolution {
  if (ref.origin !== 'base') return { status: 'skip' }; // CTE/derived resolve in-query
  const reduced = reduceName(ref.name, ctx.dialect, ctx.config);
  if (!reduced) return { status: 'skip' };
  const namespace = namespaceFor(ctx.config, reduced.database, reduced.schema);
  if (!namespace) return { status: 'skip' };
  const candidates = index.get(namespace);
  if (!candidates || candidates.length === 0) return { status: 'skip' }; // namespace not modelled
  const match = candidates.find((t) => foldEq(t.entry.name, reduced.table, ctx.dialect));
  return match ? { status: 'resolved', info: match } : { status: 'unknown' };
}

/** The scope table whose alias (or, unaliased, last name part) matches `qualifier`. */
function scopeTableForQualifier(
  scope: readonly SqlTableRef[],
  qualifier: string,
  dialect: SqlDialect,
): SqlTableRef | undefined {
  return scope.find(
    (t) =>
      (t.alias && foldEq(t.alias, qualifier, dialect)) ||
      (!t.alias && t.name.length > 0 && foldEq(t.name[t.name.length - 1]!, qualifier, dialect)),
  );
}

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
  const { dialect } = ctx;
  const index = buildSqlDbIndex(ctx.symbols);
  const diagnostics: SqlDiagnostic[] = [];
  const placeholders = ctx.placeholders ?? [];
  const isPlaceholder = (span: Span): boolean =>
    placeholders.some((p) => span.offset >= p.offset && span.offset < p.offset + p.length);

  // --- Table resolution (over every table ref, incl. nested) -----------------
  for (const ref of model.tables) {
    if (resolveTableInfo(ref, ctx, index).status === 'unknown') {
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
    const scope = model.rootScope.tables.map((ref) => ({ ref, res: resolveTableInfo(ref, ctx, index) }));
    const colNames = (r: TableResolution): string[] =>
      r.status === 'resolved' ? r.info.columns.map((c) => c.name) : [];
    // Bare columns need complete column knowledge for the whole scope.
    const allResolvedWithColumns =
      scope.length > 0 && scope.every((s) => s.res.status === 'resolved' && colNames(s.res).length > 0);

    for (const col of model.columns) {
      if (col.name === '*') continue;
      if (isPlaceholder(col.span)) continue; // a masked `{param}`, not a column
      if (col.qualifier) {
        const q = col.qualifier;
        const target = scopeTableForQualifier(model.rootScope.tables, q, dialect);
        const res = target ? scope.find((s) => s.ref === target)!.res : undefined;
        if (!target || !res || res.status !== 'resolved') continue; // not an outer table / unmodelled → skip
        const cols = colNames(res);
        if (cols.length === 0) continue;
        if (!cols.some((c) => foldEq(c, col.name, dialect))) {
          diagnostics.push({
            code: DiagnosticCode.SqlColumnNotOnAlias,
            message: `Column '${col.name}' is not defined on '${q}'`,
            span: col.span,
            severity: 'warning',
          });
        }
      } else {
        if (!allResolvedWithColumns) continue;
        const matches = scope.filter((s) => colNames(s.res).some((c) => foldEq(c, col.name, dialect)));
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

/** A hit-tested SQL reference (from the LSP's position → `SqlRefModel` hit-test). */
export type SqlRefHit =
  | { kind: 'table'; ref: SqlTableRef }
  | { kind: 'column'; ref: SqlColumnRef };

/**
 * Resolve a single hit-tested SQL ref to its TTR `db` symbol(s) — the shared
 * engine for hover / go-to-definition / find-references (§4.1–4.3). Unlike
 * {@link resolveSqlReferences} this is *lenient* (no analyzable gate): it returns
 * whatever resolves, and `[]` when nothing does (callers degrade quietly).
 *
 * - table → the table/view symbol (0 or 1).
 * - qualified column (`a.col`) → the column on alias `a`'s table (0 or 1).
 * - bare column → every in-scope table's matching column (0 = unknown, 1 =
 *   unique, ≥2 = ambiguous; callers may return all).
 */
export function resolveSqlRefAt(hit: SqlRefHit, model: SqlRefModel, ctx: SqlResolveContext): SymbolEntry[] {
  const index = buildSqlDbIndex(ctx.symbols);
  const { dialect } = ctx;

  if (hit.kind === 'table') {
    const res = resolveTableInfo(hit.ref, ctx, index);
    return res.status === 'resolved' ? [res.info.entry] : [];
  }

  const col = hit.ref;
  const matchOn = (info: DbTableInfo): SymbolEntry[] =>
    info.columns.filter((c) => foldEq(c.name, col.name, dialect));

  if (col.qualifier) {
    const target = scopeTableForQualifier(model.rootScope.tables, col.qualifier, dialect);
    if (!target) return [];
    const res = resolveTableInfo(target, ctx, index);
    return res.status === 'resolved' ? matchOn(res.info) : [];
  }

  const out: SymbolEntry[] = [];
  for (const ref of model.rootScope.tables) {
    const res = resolveTableInfo(ref, ctx, index);
    if (res.status === 'resolved') out.push(...matchOn(res.info));
  }
  return out;
}

/**
 * Resolve a raw SQL table name (parts, e.g. `['dbo','Orders']`) to its `db`
 * symbol, independent of a `SqlRefModel` — for lexer-derived completion scopes
 * (§4.4) where no parse tree exists. Returns null when unresolved.
 */
export function resolveSqlTableName(name: string[], ctx: SqlResolveContext): SymbolEntry | null {
  const index = buildSqlDbIndex(ctx.symbols);
  const ref = { name, alias: undefined, origin: 'base', span: { offset: 0, length: 0, line: 1, column: 0 } } as SqlTableRef;
  const res = resolveTableInfo(ref, ctx, index);
  return res.status === 'resolved' ? res.info.entry : null;
}

/** A root-scope table ref paired with its resolved `db` symbol (or null). */
export interface SqlScopeTable {
  ref: SqlTableRef;
  symbol: SymbolEntry | null;
}

/**
 * The outer-query (root-scope) tables with their resolved `db` symbols — the
 * basis for column completion (§4.4). CTE/derived refs and unresolved base
 * tables come back with `symbol: null`.
 */
export function sqlScopeTables(model: SqlRefModel, ctx: SqlResolveContext): SqlScopeTable[] {
  const index = buildSqlDbIndex(ctx.symbols);
  return model.rootScope.tables.map((ref) => {
    const res = resolveTableInfo(ref, ctx, index);
    return { ref, symbol: res.status === 'resolved' ? res.info.entry : null };
  });
}
