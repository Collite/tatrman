// SPDX-License-Identifier: Apache-2.0
/**
 * `SqlRefModel` — the dialect-agnostic extraction the Phase 3 resolver consumes
 * (embedded-sql contracts §4, DESIGN §12.4). Per-dialect adapters walk their own
 * ANTLR tree and emit THIS shape; the resolver reads only this, never the trees.
 */

/** Char/line span within the (masked) SQL `value`. Shared with the lexer service. */
export interface Span {
  offset: number; // 0-indexed char offset of the first char (into `value`)
  length: number; // char length
  line: number; // 1-indexed (ANTLR token line)
  column: number; // 0-indexed (ANTLR token column)
}

export interface SqlTableRef {
  /** Raw name parts as written, e.g. `['dbo', 'Orders']` (brackets/quotes stripped). */
  name: string[];
  alias?: string;
  span: Span;
  origin: 'base' | 'cte' | 'derived';
}

export interface SqlColumnRef {
  name: string;
  /** Table or alias qualifier as written (`a` in `a.col`); absent for a bare column. */
  qualifier?: string;
  span: Span;
}

export interface SqlCte {
  name: string;
  span: Span;
  columns?: string[];
}

/** A SQL parameter: `@p` (tsql), `$1`/`:name` (pg), or a TTR `{param}` (masked). */
export interface SqlParamRef {
  name: string;
  span: Span;
}

/** A name-resolution scope: the tables/aliases visible for resolving bare columns. */
export interface SqlScope {
  tables: SqlTableRef[];
  parent?: SqlScope;
  children?: SqlScope[];
}

export interface SqlParseErrorSpan {
  message: string;
  span: Span;
}

export interface SqlRefModel {
  tables: SqlTableRef[];
  columns: SqlColumnRef[];
  ctes: SqlCte[];
  params: SqlParamRef[];
  rootScope: SqlScope;
  /** Tolerated parse errors (best-effort — never fatal; DESIGN §12.3). */
  parseErrors: SqlParseErrorSpan[];
}
