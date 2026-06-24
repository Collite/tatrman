import type {
  Document,
  EntityDef,
  RelationDef,
} from '@modeler/parser';
import { ProjectSymbolTable } from './project-symbols.js';
import type { SymbolEntry } from './symbol-table.js';

export function synthesizeMappings(
  symbols: ProjectSymbolTable,
  uri: string,
  ast: Document
): void {
  const packageName = ast.packageDecl?.name ?? '';
  const entries: SymbolEntry[] = [];

  for (const def of ast.definitions) {
    if (def.kind === 'entity') {
      collectFromEntity(def, packageName, uri, entries);
} else if (def.kind === 'attribute') {
    // Top-level `def attribute X { mapping: ... }` (outside any entity) is
    // silently skipped — the synthesized qname would have no entity qualifier
    // and the use case is `def attribute` *inside* `entity.attributes`. Per
    // design (Section D.3 spec): silent skip is acceptable for v2.1.
  } else if (def.kind === 'relation') {
      collectFromRelation(def, packageName, uri, entries);
    }
  }

  symbols.upsertSynthesizedSymbols(uri, entries);
}

function collectFromEntity(
  entity: EntityDef,
  packageName: string,
  uri: string,
  entries: SymbolEntry[]
): void {
  if (entity.mapping) {
    if (entity.mapping.kind !== 'block') {
      return;
    }
    const block = entity.mapping;

    entries.push({
      qname: synthQname(packageName, 'er2dbEntity', entity.name),
      kind: 'er2dbEntity',
      name: entity.name,
      source: block.source,
      documentUri: uri,
      packageName,
      schemaCode: 'binding',
      mappingSource: 'inline',
    });

    for (const col of block.columns ?? []) {
      entries.push({
        qname: synthQname(packageName, 'er2dbAttribute', `${entity.name}.${col.name}`),
        kind: 'er2dbAttribute',
        name: `${entity.name}.${col.name}`,
        source: col.source,
        documentUri: uri,
        packageName,
        schemaCode: 'binding',
        mappingSource: 'inline',
        parent: synthQname(packageName, 'er2dbEntity', entity.name),
      });
    }
  }

  for (const attr of entity.attributes ?? []) {
    if (!attr.mapping) continue;
    entries.push({
      qname: synthQname(packageName, 'er2dbAttribute', `${entity.name}.${attr.name}`),
      kind: 'er2dbAttribute',
      name: `${entity.name}.${attr.name}`,
      source: attr.mapping.source,
      documentUri: uri,
      packageName,
      schemaCode: 'binding',
      mappingSource: 'inline',
      parent: synthQname(packageName, 'er2dbEntity', entity.name),
    });
  }
}

function collectFromRelation(
  rel: RelationDef,
  packageName: string,
  uri: string,
  entries: SymbolEntry[]
): void {
  if (!rel.mapping) return;
  entries.push({
    qname: synthQname(packageName, 'er2dbRelation', rel.name),
    kind: 'er2dbRelation',
    name: rel.name,
    source: rel.mapping.source,
    documentUri: uri,
    packageName,
    schemaCode: 'binding',
    mappingSource: 'inline',
  });
}

/**
 * Builds the synthesized er2db_* qname using the host file's package and the
 * camelCase AST `kind` token (e.g. `er2dbEntity`) — matching what `addEntry`
 * produces for an explicit `def er2db_entity X` in a `schema binding` file WITH NO
 * NAMESPACE. If a project's binding-schema file declares a namespace
 * (`schema binding namespace <X>`), `addEntry` produces `<pkg>.binding.<X>.<name>`
 * and synthesized symbols will live at a different qname; `duplicates()` and the
 * Section E validator will not see those as collisions. The production
 * convention (see `samples/v1.1-metadata/billing/map.ttrm`) is no namespace on
 * binding-schema files, so this is acceptable for v2.1; if it ever changes,
 * synthQname must consult the explicit binding file's namespace (or `makeQname`
 * must be made kind-stable for er2db_* defs).
 */
function synthQname(pkg: string, kindToken: string, name: string): string {
  const segments: string[] = [];
  if (pkg) segments.push(pkg);
  segments.push('binding', kindToken, name);
  return segments.join('.');
}
