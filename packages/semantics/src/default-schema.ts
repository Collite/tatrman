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
export function defaultSchemaForKind(kind: string): 'db' | 'er' | 'map' | 'cnc' | 'query' {
  switch (kind) {
    case 'entity':
    case 'attribute':
    case 'relation':
      return 'er';
    case 'er2dbEntity':
    case 'er2dbAttribute':
    case 'er2dbRelation':
      return 'map';
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
