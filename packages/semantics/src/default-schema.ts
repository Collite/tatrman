/**
 * Default schema code derived from a definition's kind, applied only when a
 * file has no explicit `schema` directive. An explicit directive always wins
 * for the whole file. Mirrors the namespace fallback (`namespace || def.kind`)
 * but per the normative kind→schema map in
 * `docs/features/pkg-schema-defaults/INDEX.md`.
 *
 * `kind` is the camelCase `def.kind` string (e.g. `er2dbEntity`, `drillMap`).
 * Unknown kinds fall back to `db` — kept identical to the Kotlin twin
 * (`defaultSchemaForKind` in `Kinds.kt`).
 */
/**
 * Conventional default namespace for a schema, applied when a file declares a
 * `schema` but no explicit `namespace` (and, symmetrically, to the schema
 * derived for schema-less files). Mirrors the architecture's manifest default
 * `namespaces = { db = "dbo", … }` (architecture.md §5).
 *
 * Scoped to `db` (the SQL-default `dbo` schema): without it, a `schema db` file
 * with no namespace registers tables/columns under per-kind segments
 * (`db.table.<t>`, `db.table.<t>.<col>`), so the canonical fully-qualified
 * `db.dbo.<table>.<column>` references fail to resolve. Every other schema
 * returns '' so the caller keeps the per-kind fallback (`def.kind`): `er`
 * already coincides with its `entity` namespace, `binding` addresses symbols per
 * kind (`binding.er2dbEntity.…`, relied on by the inline-mapping synthesizer), and
 * `cnc`/`query` files carry explicit namespaces in practice.
 */
export function defaultNamespaceForSchema(schemaCode: string): string {
  return schemaCode === 'db' ? 'dbo' : '';
}

export function defaultSchemaForKind(kind: string): 'db' | 'er' | 'binding' | 'cnc' | 'query' {
  switch (kind) {
    case 'entity':
    case 'attribute':
    case 'relation':
      return 'er';
    case 'er2dbEntity':
    case 'er2dbAttribute':
    case 'er2dbRelation':
      return 'binding';
    case 'role':
    case 'er2cncRole':
      return 'cnc';
    case 'query':
    case 'drillMap':
      return 'query';
    case 'model':
    case 'table':
    case 'view':
    case 'column':
    case 'index':
    case 'constraint':
    case 'fk':
    case 'procedure':
      return 'db';
    default:
      return 'db';
  }
}
