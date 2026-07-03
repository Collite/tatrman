import type { SqlRefModel, Span } from '@modeler/sql';
import { lexSql } from '@modeler/sql/lexers';
import type { SqlRefHit } from '@modeler/semantics';
import type { SqlDialect } from '@modeler/parser';

const covers = (span: Span, offset: number): boolean =>
  offset >= span.offset && offset < span.offset + span.length;

/**
 * Hit-test a SQL-local char offset against a `SqlRefModel`: return the
 * column/table ref whose span covers it, preferring the **smallest** covering
 * span (columns are tighter than the whole `table_source_item`, so a cursor on
 * `email` in `FROM users u` picks the column, not the table). Returns `undefined`
 * when the offset is on a keyword, literal, whitespace, or `{param}` — callers
 * degrade quietly. Shared by hover (§4.1) and go-to-definition (§4.2).
 */
export function findSqlRefAtOffset(model: SqlRefModel, offset: number): SqlRefHit | undefined {
  let best: SqlRefHit | undefined;
  let bestLen = Infinity;
  for (const ref of model.columns) {
    if (ref.name !== '*' && covers(ref.span, offset) && ref.span.length < bestLen) {
      best = { kind: 'column', ref };
      bestLen = ref.span.length;
    }
  }
  for (const ref of model.tables) {
    if (covers(ref.span, offset) && ref.span.length < bestLen) {
      best = { kind: 'table', ref };
      bestLen = ref.span.length;
    }
  }
  return best;
}

/** What kind of identifier the cursor expects, for SQL completion (§4.4). */
export type SqlCompletionContext = { kind: 'table' } | { kind: 'column'; qualifier?: string };

const IS_IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/;
const TABLE_KEYWORDS = new Set(['FROM', 'JOIN', 'INTO', 'UPDATE']);
const COLUMN_KEYWORDS = new Set(['SELECT', 'WHERE', 'ON', 'AND', 'OR', 'HAVING', 'SET', 'BY']);

/**
 * Best-effort completion context at a SQL-local offset (§4.4.2). Lexer-first
 * (tolerates broken/partial SQL — no parse required):
 *
 * - an `alias.` prefix (identifier immediately followed by `.`) → column context
 *   qualified by that alias/table.
 * - otherwise the nearest preceding clause keyword decides: `FROM`/`JOIN`/… →
 *   table; `SELECT`/`WHERE`/`ON`/… → column. Commas and `(` are transparent so
 *   `SELECT a, |` stays a column position and `FROM a, |` a table position.
 *
 * Returns `undefined` when no context is recognisable (e.g. before any keyword).
 * Heuristic by design — subqueries/quoted identifiers may be mis-scoped.
 */
export function sqlCompletionContext(
  value: string,
  dialect: SqlDialect,
  offset: number,
): SqlCompletionContext | undefined {
  const toks = lexSql(value, dialect)
    .tokens.map((t) => ({ span: t.span, text: value.slice(t.span.offset, t.span.offset + t.span.length) }))
    .filter((t) => !t.text.startsWith('--') && !t.text.startsWith('/*'));

  const pre = toks.filter((t) => t.span.offset < offset);
  const last = pre[pre.length - 1];
  // Cursor sitting in the middle/end of an identifier ⇒ that's the partial word
  // being typed; the context is decided by what precedes it.
  const inWord = !!last && offset <= last.span.offset + last.span.length && IS_IDENT.test(last.text);
  const ctx = inWord ? pre.slice(0, -1) : pre;

  const tail = ctx[ctx.length - 1];
  if (tail && tail.text === '.') {
    const qual = ctx[ctx.length - 2];
    return { kind: 'column', qualifier: qual && IS_IDENT.test(qual.text) ? qual.text : undefined };
  }

  for (let i = ctx.length - 1; i >= 0; i--) {
    const t = ctx[i];
    if (t.text === ',' || t.text === '(') continue; // transparent within a clause
    const u = t.text.toUpperCase();
    if (TABLE_KEYWORDS.has(u)) return { kind: 'table' };
    if (COLUMN_KEYWORDS.has(u)) return { kind: 'column' };
  }
  return undefined;
}

/** A table reference scraped from a FROM/JOIN clause by the token scanner. */
export interface SqlTokenScopeTable {
  name: string[];
  alias?: string;
}

// Words that can follow a table name but are NOT an alias (clause/join/logical).
const NON_ALIAS = new Set([
  'FROM', 'JOIN', 'INNER', 'LEFT', 'RIGHT', 'FULL', 'OUTER', 'CROSS', 'WHERE', 'GROUP', 'ORDER',
  'BY', 'HAVING', 'ON', 'AND', 'OR', 'SELECT', 'SET', 'AS', 'UNION', 'EXCEPT', 'INTERSECT',
  'LIMIT', 'OFFSET', 'WITH', 'VALUES', 'INTO', 'USING',
]);

/**
 * Lexer-first in-scope tables for column completion (§4.4.4): scan FROM/JOIN
 * clauses for `[schema.]table [AS] [alias]`, comma-separated. Works on partial /
 * unparseable SQL (the common completion case `SELECT | FROM t`), where a full
 * parse yields no scope. Heuristic — ignores sub-query nesting.
 */
export function sqlScopeFromTokens(value: string, dialect: SqlDialect): SqlTokenScopeTable[] {
  const toks = lexSql(value, dialect)
    .tokens.map((t) => value.slice(t.span.offset, t.span.offset + t.span.length))
    .filter((t) => !t.startsWith('--') && !t.startsWith('/*'));

  const readName = (start: number): { parts: string[]; next: number } | undefined => {
    if (!IS_IDENT.test(toks[start] ?? '') || NON_ALIAS.has((toks[start] ?? '').toUpperCase())) return undefined;
    const parts = [toks[start]!];
    let k = start + 1;
    while (toks[k] === '.' && IS_IDENT.test(toks[k + 1] ?? '')) {
      parts.push(toks[k + 1]!);
      k += 2;
    }
    return { parts, next: k };
  };

  const tables: SqlTokenScopeTable[] = [];
  for (let i = 0; i < toks.length; i++) {
    const u = toks[i].toUpperCase();
    if (u !== 'FROM' && u !== 'JOIN') continue;
    let j = i + 1;
    // FROM may list comma-separated tables; JOIN introduces exactly one.
    for (;;) {
      const ref = readName(j);
      if (!ref) break;
      let k = ref.next;
      if (toks[k]?.toUpperCase() === 'AS') k++;
      let alias: string | undefined;
      if (IS_IDENT.test(toks[k] ?? '') && !NON_ALIAS.has((toks[k] ?? '').toUpperCase())) {
        alias = toks[k];
        k++;
      }
      tables.push({ name: ref.parts, alias });
      if (u === 'FROM' && toks[k] === ',') {
        j = k + 1;
        continue;
      }
      break;
    }
  }
  return tables;
}
