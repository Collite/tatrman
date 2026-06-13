import type {
  Document,
  EntityDef,
  MappingProperty,
  ObjectValue,
  Reference,
} from '@modeler/parser';
import { Resolver } from './resolver.js';
import { defaultSchemaForKind } from './default-schema.js';
import { enclosingQnameOf } from './reference-index.js';

/**
 * A navigable reference discovered inside a v2.1 inline `mapping:` property,
 * already resolved to the db column (or other target) it points at.
 *
 * Inline-mapping column references can't go through the generic
 * `collectReferences` + `resolveReference` path: the bare id (`mapping:
 * IDXXUKAZMU`) names a *db column*, but it's written in `er`-entity scope, so it
 * only resolves once you know the table the enclosing entity maps to. This
 * module computes that bridge.
 *
 * Increment A scope — attribute-level column mappings only:
 *   - `mapping: COL`                         (bare id)
 *   - `mapping: { target: COL }`             (target bare id)
 *   - `mapping: { target: { column: COL } }` (target object)
 * where the enclosing entity declares an inline `mapping { target: { table: …
 * } }`. Entity-level `columns:` maps, relation `fk`, and explicit-`er2db_entity`
 * targets are Increment B.
 */
export interface MappingReference {
  /** The column-name token (its source span drives highlight / hover / definition). */
  ref: Reference;
  /** Resolved db column symbol qname, e.g. `<pkg>.db.dbo.QXXUKAZMUHOD.IDXXUKAZMU`. */
  targetQname: string;
  /** The enclosing entity's qname (the referrer), or null if it can't be derived. */
  referrerQname: string | null;
}

/** Reference vs ObjectValue discriminator: ObjectValue carries a `kind`. */
function isReference(v: ObjectValue | Reference): v is Reference {
  return !('kind' in v);
}

/** Resolve the db-table qname an entity maps to via its inline `mapping.target`. */
function entityTargetTableQname(
  entity: EntityDef,
  resolver: Resolver,
  schemaCode: string,
  namespace: string,
  packageName: string,
): string | undefined {
  const m = entity.mapping;
  if (!m || m.kind !== 'block' || !m.target) return undefined;

  let tableRef: Reference | undefined;
  if (isReference(m.target)) {
    tableRef = m.target;
  } else {
    const tableEntry = m.target.entries.find((e) => e.key === 'table');
    if (tableEntry && tableEntry.value.kind === 'id') {
      tableRef = { path: tableEntry.value.path, parts: tableEntry.value.parts, source: tableEntry.value.source };
    }
  }
  if (!tableRef) return undefined;

  const res = resolver.resolveReference(
    { path: tableRef.path, parts: tableRef.parts },
    { schemaCode, namespace, packageName },
  );
  return res.resolved ? res.symbol.qname : undefined;
}

/** Extract the column-name reference from an attribute-level `mapping:` property. */
function attributeMappingColumnRef(m: MappingProperty | undefined): Reference | undefined {
  if (!m) return undefined;
  if (m.kind === 'bareId') return m.id;

  // block form: `mapping: { target: … }`
  if (!m.target) return undefined;
  if (isReference(m.target)) return m.target; // mapping: { target: COL }

  // mapping: { target: { column: COL } }
  const colEntry = m.target.entries.find((e) => e.key === 'column');
  if (colEntry && colEntry.value.kind === 'id') {
    return { path: colEntry.value.path, parts: colEntry.value.parts, source: colEntry.value.source };
  }
  return undefined;
}

/**
 * Collect every resolvable inline attribute-mapping column reference in a
 * document. Unresolvable ones (no entity target table, or the column doesn't
 * exist in the mapped table) are silently skipped — they behave like any other
 * dangling reference.
 */
export function collectMappingReferences(
  ast: Document,
  resolver: Resolver,
  schemaCode: string,
  namespace: string,
  packageName: string,
): MappingReference[] {
  const out: MappingReference[] = [];

  for (const def of ast.definitions) {
    if (def.kind !== 'entity') continue;
    const effSchema = schemaCode || defaultSchemaForKind(def.kind);

    const tableQname = entityTargetTableQname(def, resolver, effSchema, namespace, packageName);
    if (!tableQname) continue;

    const referrerQname = enclosingQnameOf(def, effSchema, namespace, packageName) ?? null;

    for (const attr of def.attributes ?? []) {
      const colRef = attributeMappingColumnRef(attr.mapping);
      if (!colRef) continue;
      const columnName = colRef.parts[colRef.parts.length - 1];
      const columnQname = `${tableQname}.${columnName}`;
      if (resolver.getSymbol(columnQname)) {
        out.push({ ref: colRef, targetQname: columnQname, referrerQname });
      }
    }
  }

  return out;
}
