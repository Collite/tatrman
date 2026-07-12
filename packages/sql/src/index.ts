// SPDX-License-Identifier: Apache-2.0
/**
 * Desktop (Node) entry point (`@tatrman/sql`) — re-exports the browser-safe
 * lexers plus the full dialect **parsers**. Parsers are desktop-only (SPIKE
 * S0.3 bundle budget); browser hosts import `@tatrman/sql/lexers` instead.
 * Per-dialect ref adapters + `SqlRefModel` land in Phase 3.
 */
export * from './lexers.js';
export { TSqlParser } from './generated/tsql/TSqlParser.js';
export { PostgreSQLParser } from './generated/postgresql/PostgreSQLParser.js';
export { extract } from './adapters/index.js';
export { parseSql } from './parser-service.js';
export type { SqlParseResult } from './parser-service.js';
export type {
  SqlRefModel,
  SqlTableRef,
  SqlColumnRef,
  SqlCte,
  SqlParamRef,
  SqlScope,
  SqlParseErrorSpan,
} from './refmodel.js';
