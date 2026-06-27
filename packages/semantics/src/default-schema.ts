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

export function defaultSchemaForKind(kind: string): 'db' | 'er' | 'binding' | 'cnc' | 'query' | 'md' {
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
    // v3.1 MD logical kinds → `schema md`; binding kinds → `schema binding`.
    case 'mdDomain':
    case 'dimension':
    case 'mdMap':
    case 'hierarchy':
    case 'measure':
    case 'cubelet':
      return 'md';
    case 'md2dbCubelet':
    case 'md2dbDomain':
    case 'md2dbMap':
    case 'md2erCubelet':
      return 'binding';
    case 'project':
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

/**
 * The symbol-table namespace segment for a def kind, where it differs from the
 * camelCase `def.kind`. MD logical/binding kinds map to the contracts §5
 * namespaces (`md.domain.*`, `md.map.*`, `binding.md2db_cubelet.*`, …) rather
 * than the raw kind (`mdDomain`/`mdMap`). Returns `''` for every other kind so
 * the caller keeps the existing `def.kind` fallback.
 */
export function namespaceForKind(kind: string): string {
  switch (kind) {
    case 'mdDomain':
      return 'domain';
    case 'mdMap':
      return 'map';
    case 'dimension':
      return 'dimension';
    case 'hierarchy':
      return 'hierarchy';
    case 'measure':
      return 'measure';
    case 'cubelet':
      return 'cubelet';
    case 'md2dbCubelet':
      return 'md2db_cubelet';
    case 'md2dbDomain':
      return 'md2db_domain';
    case 'md2dbMap':
      return 'md2db_map';
    case 'md2erCubelet':
      return 'md2er_cubelet';
    default:
      return '';
  }
}
