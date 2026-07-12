// SPDX-License-Identifier: Apache-2.0
import {
  CharStream,
  CommonTokenStream,
  Token,
  type ANTLRErrorListener,
  type ATNSimulator,
  type Lexer,
  type RecognitionException,
  type Recognizer,
} from 'antlr4ng';
import type { SqlDialect, TaggedBlockValue } from '@tatrman/parser';
import { TSqlLexer } from './generated/tsql/TSqlLexer.js';
import { PostgreSQLLexer } from './generated/postgresql/PostgreSQLLexer.js';
import { maskPlaceholders, type MaskResult } from './mask.js';
import type { Span } from './refmodel.js';

export type { Span } from './refmodel.js';

/**
 * One lexed SQL token. `typeName`/`literalName` come straight from the dialect
 * vocabulary so {@link classifyToken} can map them to a semantic-token type
 * without hard-coded integers (regen-safe).
 */
export interface SqlToken {
  type: number;
  typeName: string | null;
  literalName: string | null;
  span: Span;
}

/** A lexer-level syntax error (line 1-indexed, column 0-indexed). */
export interface SqlLexError {
  line: number;
  column: number;
  message: string;
}

class CollectingErrorListener implements ANTLRErrorListener {
  constructor(private readonly out: SqlLexError[]) {}
  syntaxError(
    _recognizer: Recognizer<ATNSimulator>,
    _offendingSymbol: unknown,
    line: number,
    column: number,
    msg: string,
    _e: RecognitionException | null,
  ): void {
    this.out.push({ line, column, message: msg });
  }
  reportAmbiguity(): void {}
  reportAttemptingFullContext(): void {}
  reportContextSensitivity(): void {}
}

type LexerCtor = new (input: CharStream) => Lexer;

/**
 * The lexer class for a dialect, or `null` if its grammar is not yet vendored
 * (mysql/bigquery — Phase 2 ships tsql + postgres only). DuckDB reuses the
 * PostgreSQL lexer (E11: postgres-derived grammar + lazy patches).
 */
function lexerCtorFor(dialect: SqlDialect): LexerCtor | null {
  switch (dialect) {
    case 'tsql':
      return TSqlLexer as unknown as LexerCtor;
    case 'postgres':
    case 'duckdb':
      return PostgreSQLLexer as unknown as LexerCtor;
    case 'mysql':
    case 'bigquery':
      return null; // grammars land later — graceful no-highlight rather than throw
    default:
      return null;
  }
}

/**
 * Lex an embedded-SQL `value` for a dialect. Masks `{param}` placeholders first
 * (so braces don't break raw lexing — SPIKE S0.2), then runs the dialect lexer.
 * Returns every non-EOF token (incl. hidden-channel comments, which still need
 * highlighting) plus the {@link MaskResult} (its `placeholders` feed the
 * `parameter` semantic tokens in stage 2.4). For a not-yet-supported dialect the
 * token list is empty (no highlighting), never a throw.
 */
export function lexSql(
  value: string,
  dialect: SqlDialect,
): { tokens: SqlToken[]; masked: MaskResult; errors: SqlLexError[] } {
  const masked = maskPlaceholders(value);
  const errors: SqlLexError[] = [];
  const Ctor = lexerCtorFor(dialect);
  if (!Ctor) return { tokens: [], masked, errors };

  const lexer = new Ctor(CharStream.fromString(masked.masked));
  // Replace the default console listener (the SQL lexer is best-effort; broken /
  // partial SQL is expected) with one that collects errors for the caller.
  lexer.removeErrorListeners();
  lexer.addErrorListener(new CollectingErrorListener(errors));
  const stream = new CommonTokenStream(lexer);
  stream.fill();
  const vocab = lexer.vocabulary;

  const tokens: SqlToken[] = [];
  for (const t of stream.getTokens()) {
    if (t.type === Token.EOF) continue;
    tokens.push({
      type: t.type,
      typeName: vocab.getSymbolicName(t.type),
      literalName: vocab.getLiteralName(t.type),
      span: { offset: t.start, length: t.stop - t.start + 1, line: t.line, column: t.column },
    });
  }
  return { tokens, masked, errors };
}

/** Phase 2 SQL config surface (the full schema + loader land in Phase 3). */
export interface SqlConfig {
  defaultDialect?: SqlDialect;
}

/**
 * Resolve the dialect to lex a block with: the tag's resolved dialect, else the
 * project default, else a hard `tsql` fallback.
 *
 * TODO(Phase 3 / task 3.3): `sqlConfig.defaultDialect` will come from
 * `modeler.toml`; until that loader exists the hard default applies.
 */
export function resolveDialect(
  block: Pick<TaggedBlockValue, 'dialect'>,
  sqlConfig?: SqlConfig,
): SqlDialect {
  return block.dialect ?? sqlConfig?.defaultDialect ?? 'tsql';
}
