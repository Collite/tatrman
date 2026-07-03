import type { ParseTree } from 'antlr4ng';
import type { SqlDialect } from '@modeler/parser';
import type { SqlRefModel } from '../refmodel.js';
import { extractTsql } from './tsql.js';
import { extractPostgres } from './postgres.js';

const EMPTY: SqlRefModel = { tables: [], columns: [], ctes: [], params: [], rootScope: { tables: [] }, parseErrors: [] };

/**
 * Extract the dialect-agnostic {@link SqlRefModel} from a parsed SQL tree
 * (embedded-sql DESIGN §12.4). DuckDB reuses the Postgres adapter (E11);
 * not-yet-vendored dialects (mysql/bigquery) yield an empty model.
 */
export function extract(tree: ParseTree, dialect: SqlDialect): SqlRefModel {
  switch (dialect) {
    case 'tsql':
      return extractTsql(tree);
    case 'postgres':
    case 'duckdb':
      return extractPostgres(tree);
    default:
      return EMPTY;
  }
}
