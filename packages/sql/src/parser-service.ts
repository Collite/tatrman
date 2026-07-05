import {
  CharStream,
  CommonTokenStream,
  type ANTLRErrorListener,
  type ATNSimulator,
  type ParseTree,
  type Recognizer,
  type Token,
} from 'antlr4ng';
import type { SqlDialect } from '@tatrman/parser';
import { TSqlLexer } from './generated/tsql/TSqlLexer.js';
import { TSqlParser } from './generated/tsql/TSqlParser.js';
import { PostgreSQLLexer } from './generated/postgresql/PostgreSQLLexer.js';
import { PostgreSQLParser } from './generated/postgresql/PostgreSQLParser.js';
import { maskPlaceholders } from './mask.js';
import type { SqlParseErrorSpan } from './refmodel.js';

export interface SqlParseResult {
  tree: ParseTree | null;
  parseErrors: SqlParseErrorSpan[];
}

class CollectingErrorListener implements ANTLRErrorListener {
  constructor(private readonly out: SqlParseErrorSpan[]) {}
  syntaxError(
    _recognizer: Recognizer<ATNSimulator>,
    offendingSymbol: unknown,
    line: number,
    column: number,
    msg: string,
  ): void {
    const tok = offendingSymbol as Token | null;
    const span =
      tok && typeof tok.start === 'number'
        ? { offset: tok.start, length: Math.max(0, tok.stop - tok.start + 1), line, column }
        : { offset: 0, length: 0, line, column };
    this.out.push({ message: msg, span });
  }
  reportAmbiguity(): void {}
  reportAttemptingFullContext(): void {}
  reportContextSensitivity(): void {}
}

/**
 * Parse embedded SQL best-effort (embedded-sql DESIGN §12.3): mask `{param}`
 * placeholders, then parse with the **default** error strategy (NOT
 * BailErrorStrategy) so a partly-broken query still yields a usable tree. Errors
 * are collected, never thrown. Not-yet-vendored dialects (mysql/bigquery) return
 * a null tree.
 */
export function parseSql(value: string, dialect: SqlDialect): SqlParseResult {
  const masked = maskPlaceholders(value).masked;
  const parseErrors: SqlParseErrorSpan[] = [];
  const listener = new CollectingErrorListener(parseErrors);

  if (dialect === 'tsql' || dialect === 'mysql' || dialect === 'bigquery') {
    if (dialect !== 'tsql') return { tree: null, parseErrors };
    const lexer = new TSqlLexer(CharStream.fromString(masked));
    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    const parser = new TSqlParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    return { tree: parser.tsql_file(), parseErrors };
  }

  // postgres + duckdb (DuckDB reuses the Postgres grammar; E11)
  const lexer = new PostgreSQLLexer(CharStream.fromString(masked));
  lexer.removeErrorListeners();
  lexer.addErrorListener(listener);
  const parser = new PostgreSQLParser(new CommonTokenStream(lexer));
  parser.removeErrorListeners();
  parser.addErrorListener(listener);
  return { tree: parser.root(), parseErrors };
}
