// SPDX-License-Identifier: Apache-2.0
import type { LanguageKind, SqlDialect } from './ast.js';

/**
 * Tag → language/dialect registry (embedded-sql DESIGN §5, contracts §3).
 *
 * Conceptually a shared table (the Kotlin walker mirrors it in stage 1.4). It
 * lives in the parser because the walker is its only TS consumer and resolution
 * to `language`/`dialect` happens during the walk to populate `TaggedBlockValue`
 * — a static table lookup mirrored in both parsers, not cross-reference
 * resolution (which stays in `@tatrman/semantics`).
 */
export interface TagEntry {
  language: LanguageKind;
  dialect: SqlDialect | null; // null for a bare `sql` (→ modeler.toml default) or a non-SQL language
}

export const TAG_REGISTRY: Record<string, TagEntry> = {
  // Bare SQL → dialect deferred to the project default in modeler.toml.
  sql: { language: 'SQL', dialect: null },
  // T-SQL aliases.
  'ms-sql': { language: 'SQL', dialect: 'tsql' },
  tsql: { language: 'SQL', dialect: 'tsql' },
  mssql: { language: 'SQL', dialect: 'tsql' },
  // PostgreSQL aliases.
  postgres: { language: 'SQL', dialect: 'postgres' },
  postgresql: { language: 'SQL', dialect: 'postgres' },
  pg: { language: 'SQL', dialect: 'postgres' },
  // DuckDB (postgres-derived grammar + patches).
  duckdb: { language: 'SQL', dialect: 'duckdb' },
  // Other SQL dialects (grammars land later).
  mysql: { language: 'SQL', dialect: 'mysql' },
  bigquery: { language: 'SQL', dialect: 'bigquery' },
  bq: { language: 'SQL', dialect: 'bigquery' },
  // Non-SQL embedded languages.
  transform: { language: 'TRANSFORMATION_DSL', dialect: null },
  dataframe: { language: 'DATAFRAME_DSL', dialect: null },
  relnode: { language: 'REL_NODE', dialect: null },
};

/** Resolve a tag to its registry entry, or `undefined` if unknown (caller diagnoses). */
export function resolveTag(tag: string): TagEntry | undefined {
  return TAG_REGISTRY[tag];
}
