// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { CharStream, CommonTokenStream } from 'antlr4ng';
import { TSqlLexer } from '../generated/tsql/TSqlLexer.js';
import { TSqlParser } from '../generated/tsql/TSqlParser.js';
import { PostgreSQLLexer } from '../generated/postgresql/PostgreSQLLexer.js';
import { PostgreSQLParser } from '../generated/postgresql/PostgreSQLParser.js';

/**
 * Generation smoke test (Phase 2.1.5, ported from SPIKE S0.1): the vendored
 * grammars generate a runnable antlr4ng lexer + parser for both dialects, and
 * the lexers' `caseInsensitive` option folds keyword casing. Proves the
 * `prebuild` → `generate.sh` toolchain produced working code, not just files.
 */
function lex(LexerClass: typeof TSqlLexer | typeof PostgreSQLLexer, sql: string) {
  const lexer = new LexerClass(CharStream.fromString(sql));
  const tokens = new CommonTokenStream(lexer);
  tokens.fill();
  return tokens;
}

/** Type of the first non-EOF token. */
function firstTokenType(
  LexerClass: typeof TSqlLexer | typeof PostgreSQLLexer,
  sql: string,
): number {
  const toks = lex(LexerClass, sql).getTokens().filter((t) => t.type !== TSqlLexer.EOF);
  return toks[0]!.type;
}

describe('@tatrman/sql generation smoke (S0.1)', () => {
  it('T-SQL lexes a non-empty token stream', () => {
    expect(lex(TSqlLexer, 'SELECT 1').getTokens().length).toBeGreaterThan(0);
  });

  it('PostgreSQL lexes a non-empty token stream', () => {
    expect(lex(PostgreSQLLexer, 'SELECT 1').getTokens().length).toBeGreaterThan(0);
  });

  it('T-SQL parses SELECT 1; into a non-empty tree', () => {
    const parser = new TSqlParser(lex(TSqlLexer, 'SELECT 1;'));
    const tree = parser.tsql_file();
    expect(tree).toBeDefined();
    expect(tree.getChildCount()).toBeGreaterThan(0);
  });

  it('PostgreSQL parses SELECT 1; into a non-empty tree', () => {
    const parser = new PostgreSQLParser(lex(PostgreSQLLexer, 'SELECT 1;'));
    const tree = parser.root();
    expect(tree).toBeDefined();
    expect(tree.getChildCount()).toBeGreaterThan(0);
  });

  it('T-SQL folds keyword casing (caseInsensitive option)', () => {
    expect(firstTokenType(TSqlLexer, 'select')).toBe(TSqlLexer.SELECT);
    expect(firstTokenType(TSqlLexer, 'SELECT')).toBe(TSqlLexer.SELECT);
    expect(firstTokenType(TSqlLexer, 'SeLeCt')).toBe(TSqlLexer.SELECT);
  });

  it('PostgreSQL folds keyword casing (caseInsensitive option)', () => {
    expect(firstTokenType(PostgreSQLLexer, 'select')).toBe(PostgreSQLLexer.SELECT);
    expect(firstTokenType(PostgreSQLLexer, 'SELECT')).toBe(PostgreSQLLexer.SELECT);
    expect(firstTokenType(PostgreSQLLexer, 'SeLeCt')).toBe(PostgreSQLLexer.SELECT);
  });
});
