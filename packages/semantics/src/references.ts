import type {
  Definition,
  Document,
  ImportDecl,
  PropertyValue,
  Reference,
} from '@tatrman/parser';

/**
 * Returns all explicit `Reference` AST nodes attached to a definition.
 *
 * Covers every property whose declared type is `Reference` on any def kind:
 *   - EntityDef.nameAttribute, EntityDef.codeAttribute
 *   - Er2dbEntityDef.entity, Er2dbAttributeDef.attribute
 *   - Er2dbRelationDef.relation, Er2dbRelationDef.fk
 *   - Er2cncRoleDef.entity, Er2cncRoleDef.role
 *
 * `IdValue` occurrences inside `from`/`to`/`join` on relation/fk defs are
 * also returned, wrapped as synthetic References (parser exposes those as
 * generic PropertyValue, but they behave like references for navigation).
 */
export function collectReferences(def: Definition): Reference[] {
  const refs: Reference[] = [];

  switch (def.kind) {
    case 'entity':
      if (def.nameAttribute) refs.push(def.nameAttribute);
      if (def.codeAttribute) refs.push(def.codeAttribute);
      break;
    case 'er2dbEntity':
      if (def.entity) refs.push(def.entity);
      break;
    case 'er2dbAttribute':
      if (def.attribute) refs.push(def.attribute);
      break;
    case 'er2dbRelation':
      if (def.relation) refs.push(def.relation);
      if (def.fk) refs.push(def.fk);
      break;
    case 'er2cncRole':
      if (def.entity) refs.push(def.entity);
      if (def.role) refs.push(def.role);
      break;
    case 'relation':
      pushIdValueAsReference(def.from, refs);
      pushIdValueAsReference(def.to, refs);
      break;
    case 'fk':
      pushIdValueAsReference(def.from, refs);
      pushIdValueAsReference(def.to, refs);
      break;
    // v3.1 MD — span-carrying cross-references collected by the walker.
    case 'attribute':
    case 'dimension':
    case 'mdMap':
    case 'hierarchy':
    case 'measure':
    case 'cubelet':
      for (const c of def.crossRefs ?? []) {
        refs.push({ path: c.path, parts: c.path.split('.'), source: c.source });
      }
      break;
    default:
      break;
  }

  return refs;
}

function pushIdValueAsReference(
  value: PropertyValue | undefined,
  out: Reference[]
): void {
  if (!value) return;
  if (value.kind === 'id') {
    out.push({ path: value.path, parts: value.parts, source: value.source });
  } else if (value.kind === 'list') {
    for (const item of value.items) {
      pushIdValueAsReference(item, out);
    }
  } else if (value.kind === 'object') {
    for (const entry of value.entries) {
      pushIdValueAsReference(entry.value, out);
    }
  }
}

export interface CollectedReference {
  ref: Reference;
  /** The top-level def this reference is attached to (or its containing parent). */
  ownerDef: Definition;
}

/**
 * Walk every definition in a document (including nested attribute / column /
 * index / constraint / resultColumn defs) and collect every `Reference`
 * reachable from them, paired with the def it came from. The owner is the
 * top-level def for nested children (so `nameAttribute` on `entity artikl`
 * is owned by `artikl` itself, not by an inner attribute).
 */
export function collectAllReferences(ast: Document): CollectedReference[] {
  const out: CollectedReference[] = [];
  for (const def of ast.definitions) {
    for (const ref of collectReferences(def)) out.push({ ref, ownerDef: def });
    for (const child of nestedDefs(def)) {
      for (const ref of collectReferences(child)) out.push({ ref, ownerDef: def });
    }
  }
  return out;
}

/**
 * Returns the nested per-def children (attributes, columns, indices, ...).
 * Top-level relations / queries / roles are at file scope, not nested here.
 */
export function nestedDefs(def: Definition): Definition[] {
  const out: Definition[] = [];
  switch (def.kind) {
    case 'entity':
      if (def.attributes) out.push(...def.attributes);
      break;
    case 'table':
      if (def.columns) out.push(...def.columns);
      if (def.indices) out.push(...def.indices);
      if (def.constraints) out.push(...def.constraints);
      break;
    case 'view':
      if (def.columns) out.push(...def.columns);
      break;
    case 'procedure':
      if (def.resultColumns) out.push(...def.resultColumns);
      break;
    case 'dimension':
      // Inline MD attributes carry their own `domain:` cross-references.
      out.push(...def.attributes);
      break;
    default:
      break;
  }
  return out;
}

export function packageOfImport(imp: ImportDecl): string {
  if (imp.wildcard) return imp.target;
  const parts = imp.target.split('.');
  return parts.length >= 2 ? parts.slice(0, -1).join('.') : '';
}
