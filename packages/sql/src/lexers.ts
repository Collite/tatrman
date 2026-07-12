// SPDX-License-Identifier: Apache-2.0
/**
 * Browser-safe entry point (`@tatrman/sql/lexers`) — the dialect **lexers** only,
 * for the Web Worker / Designer where the heavy parsers must not be bundled
 * (E11 bundle split; SPIKE S0.3: both lexers ~+155 KB gz, both parsers ~+839 KB
 * gz). The `.` entry (`index.ts`) adds the parsers + adapters for the desktop
 * (Node) host. `maskPlaceholders` (stage 2.2) is re-exported here too.
 */
export { TSqlLexer } from './generated/tsql/TSqlLexer.js';
export { PostgreSQLLexer } from './generated/postgresql/PostgreSQLLexer.js';
export { maskPlaceholders } from './mask.js';
export type { MaskResult, MaskedSpan } from './mask.js';
export { lexSql, resolveDialect } from './lexer-service.js';
export type { SqlToken, Span, SqlConfig, SqlLexError } from './lexer-service.js';
export { classifyToken } from './token-classify.js';
export type { SqlSemanticType } from './token-classify.js';
