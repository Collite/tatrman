import type { SourceLocation, Document, Definition } from '@modeler/parser';
import { defaultSchemaForKind } from './default-schema.js';

export interface SymbolEntry {
  qname: string;
  kind: Definition['kind'];
  name: string;
  source: SourceLocation;
  documentUri: string;
  parent?: string;
  packageName: string;
  schemaCode: string;
  /**
   * For er2db_* symbols only: distinguishes explicit `def er2db_*` declarations
   * from symbols synthesized from inline `mapping:` properties on def entity /
   * attribute / relation (v2.1). Used by the duplicate-mapping validator.
   * Undefined for all non-er2db_* kinds.
   */
  mappingSource?: 'explicit' | 'inline';
  /**
   * For explicit `def er2db_entity` symbols only: the dotted reference path of
   * the db table it targets (e.g. `db.dbo.QXXUKAZMUHOD`), taken from
   * `target: { table: … }`. Lets inline attribute/column mappings on an entity
   * with no inline mapping block resolve their columns against this table.
   */
  targetTableRef?: string;
}

export class DocumentSymbolTable {
  private entries: Map<string, SymbolEntry> = new Map();
  private documentUri: string;
  private schemaCode: string;
  private namespace: string;
  private packageName: string;

  /**
   * `schemaCode` is the file's explicit `schema` directive code, or `''` when
   * the file has no directive. In the empty case each entry's effective schema
   * is derived per-definition from its kind (see {@link effectiveSchema}).
   */
  constructor(documentUri: string, ast: Document, schemaCode: string, namespace: string) {
    this.documentUri = documentUri;
    this.schemaCode = schemaCode;
    this.namespace = namespace;
    this.packageName = ast.packageDecl?.name ?? '';

    for (const def of ast.definitions) {
      this.addEntry(def);
    }
  }

  private get isStockCnc(): boolean {
    return this.schemaCode === 'cnc' && !this.packageName && this.documentUri.startsWith('stock://');
  }

  /**
   * The file's directive schema, or — when the file has no `schema` directive —
   * the kind-derived default. Symmetric to the `namespace || def.kind` fallback.
   */
  private effectiveSchema(kind: string): string {
    return this.schemaCode || defaultSchemaForKind(kind);
  }

  private makeQname(parts: string[], namespaceOrKind: string, schema: string): string {
    // TODO(post-v1.1): the doubled `cnc.cnc.<ns-or-kind>.*` shape is a
    // transitional accommodation per v1-1-contracts §3.1 (open-question #10).
    // Revisit when the conceptual-model layer lands and we can model stock
    // cnc as an actual package.
    const segments: string[] = [];
    if (this.packageName) segments.push(this.packageName);
    if (this.isStockCnc) segments.push('cnc');
    segments.push(schema);
    if (namespaceOrKind) segments.push(namespaceOrKind);
    segments.push(...parts);
    return segments.join('.');
  }

  private makeQnameChild(parentEntry: SymbolEntry, childName: string): string {
    const segments: string[] = [];
    if (this.packageName) segments.push(this.packageName);
    if (this.isStockCnc) segments.push('cnc');
    // Children inherit the parent's effective schema verbatim.
    segments.push(parentEntry.schemaCode);
    if (this.namespace) {
      segments.push(this.namespace);
    } else {
      segments.push(parentEntry.kind);
    }
    segments.push(parentEntry.name, childName);
    return segments.join('.');
  }

  private addEntry(def: Definition, parentQname?: string): void {
    const schema = this.effectiveSchema(def.kind);
    const nsOrKind = this.namespace || def.kind;
    const qnameStr = this.makeQname([def.name], nsOrKind, schema);

    const entry: SymbolEntry = {
      qname: qnameStr,
      kind: def.kind,
      name: def.name,
      source: def.source,
      documentUri: this.documentUri,
      parent: parentQname,
      packageName: this.packageName,
      schemaCode: schema,
    };

    if (def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' || def.kind === 'er2dbRelation') {
      entry.mappingSource = 'explicit';
    }
    if (def.kind === 'er2dbEntity' && def.target) {
      const t = def.target;
      if ('kind' in t) {
        // ObjectValue: `target: { table: db.dbo.X }`
        const tableEntry = t.entries.find((e) => e.key === 'table');
        if (tableEntry && tableEntry.value.kind === 'id') entry.targetTableRef = tableEntry.value.path;
      } else {
        // Reference: `target: db.dbo.X`
        entry.targetTableRef = t.path;
      }
    }

    this.entries.set(qnameStr, entry);

    if (def.kind === 'entity' && def.attributes) {
      const entityEntry = entry;
      for (const attr of def.attributes) {
        const attrQnameStr = this.makeQnameChild(entityEntry, attr.name);
        this.entries.set(attrQnameStr, {
          qname: attrQnameStr,
          kind: 'attribute',
          name: attr.name,
          source: attr.source,
          documentUri: this.documentUri,
          parent: entityEntry.qname,
          packageName: this.packageName,
          schemaCode: entry.schemaCode,
        });
      }
    }

    if (def.kind === 'table' && def.columns) {
      const tableEntry = entry;
      for (const col of def.columns) {
        const colQnameStr = this.makeQnameChild(tableEntry, col.name);
        this.entries.set(colQnameStr, {
          qname: colQnameStr,
          kind: 'column',
          name: col.name,
          source: col.source,
          documentUri: this.documentUri,
          parent: tableEntry.qname,
          packageName: this.packageName,
          schemaCode: entry.schemaCode,
        });
      }
    }

    if (def.kind === 'view' && def.columns) {
      const viewEntry = entry;
      for (const col of def.columns) {
        const colQnameStr = this.makeQnameChild(viewEntry, col.name);
        this.entries.set(colQnameStr, {
          qname: colQnameStr,
          kind: 'column',
          name: col.name,
          source: col.source,
          documentUri: this.documentUri,
          parent: viewEntry.qname,
          packageName: this.packageName,
          schemaCode: entry.schemaCode,
        });
      }
    }

    if (def.kind === 'procedure' && def.resultColumns) {
      const procEntry = entry;
      for (const col of def.resultColumns) {
        const colQnameStr = this.makeQnameChild(procEntry, col.name);
        this.entries.set(colQnameStr, {
          qname: colQnameStr,
          kind: 'column',
          name: col.name,
          source: col.source,
          documentUri: this.documentUri,
          parent: procEntry.qname,
          packageName: this.packageName,
          schemaCode: entry.schemaCode,
        });
      }
    }
  }

  get(qname: string): SymbolEntry | undefined {
    return this.entries.get(qname);
  }

  all(): SymbolEntry[] {
    return Array.from(this.entries.values());
  }

  inDocument(): SymbolEntry[] {
    return this.all();
  }
}