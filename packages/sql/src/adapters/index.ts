import type { ParseTree } from 'antlr4ng';
import type { SqlDialect } from '@modeler/parser';
import type { SqlRefModel } from '../refmodel.js';

/**
 * Extract the dialect-agnostic {@link SqlRefModel} from a parsed SQL tree
 * (embedded-sql DESIGN §12.4). Dispatches to the per-dialect adapter
 * (DuckDB → Postgres). Implemented in stage 3.2; declared here so the 3.1
 * extraction specs compile and fail on the missing logic, not on imports.
 */
export function extract(_tree: ParseTree, _dialect: SqlDialect): SqlRefModel {
  throw new Error('extract: not implemented (embedded-sql stage 3.2)');
}
