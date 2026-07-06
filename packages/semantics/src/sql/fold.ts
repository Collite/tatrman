import type { SqlDialect } from '@tatrman/parser';

/**
 * Dialect identifier folding (embedded-sql contracts §6.2, DESIGN §12.8). To
 * compare a SQL identifier against a TTR `db` symbol name, **both** are folded by
 * the block's dialect rule before equality:
 *
 * | dialect  | unquoted        | quoting                                  |
 * |----------|-----------------|------------------------------------------|
 * | tsql     | case-insensitive| `[brackets]` / `"…"` (QUOTED_IDENTIFIER) |
 * | postgres | fold to lower   | `"double quotes"` preserve case          |
 * | duckdb   | case-insensitive, case-preserving | `"double quotes"`      |
 *
 * NOTE: the `SqlRefModel` strips quoting in the adapters (contracts §4 —
 * `name`/columns are bracket/quote-stripped), so quotedness is **not** available
 * here. We therefore fold case-insensitively for every supported dialect, which
 * errs toward *resolving* a reference (fewer false-positive "unknown" diagnostics
 * — the Phase 3 DoD) at the cost of not distinguishing a Postgres `"User"`
 * (case-sensitive) from `user`. Recorded as a known simplification in
 * `packages/sql/LAZY-PATCH.md`.
 */
export function foldIdent(raw: string, _dialect: SqlDialect): string {
  // All currently-supported dialects (tsql/postgres/duckdb, + mysql/bigquery
  // fallbacks) compare case-insensitively once quoting is stripped. Kept
  // dialect-parameterised so a future quoted-identifier-aware fold can diverge
  // per the table above without touching call sites.
  return raw.toLowerCase();
}

/** Whether two identifiers are equal under the dialect's folding rule. */
export function foldEq(a: string, b: string, dialect: SqlDialect): boolean {
  return foldIdent(a, dialect) === foldIdent(b, dialect);
}
