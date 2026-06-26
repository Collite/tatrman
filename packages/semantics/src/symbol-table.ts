import type { SourceLocation, Document, Definition } from '@modeler/parser';
import { defaultSchemaForKind, defaultNamespaceForSchema, namespaceForKind } from './default-schema.js';

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
   * from symbols synthesized from inline `binding:` properties on def entity /
   * attribute / relation (v2.1). Used by the duplicate-mapping validator.
   * Undefined for all non-er2db_* kinds.
   */
  mappingSource?: 'explicit' | 'inline';
  /**
   * For MD dimension attributes only: the `domain:` ref the attribute ranges
   * over (opaque). Lets the leaf/grain lattice (2E) lower an attribute-level map
   * to the map over the attributes' underlying domains without re-walking the AST.
   */
  domainRef?: string;
  /**
   * For MD domain symbols (`mdDomain`) only: the domain's `type` name and (when a
   * `range` restrict is present) its bounds. Lets the calc-catalog type-check
   * (2D) verify a map's `from`/`to` against the entry signature cross-file.
   */
  domainType?: string;
  domainRange?: { lo: number; hi: number };
  /**
   * For explicit `def er2db_entity` symbols only: the dotted reference path of
   * the db table (or view) it targets (e.g. `db.dbo.QXXUKAZMUHOD`), taken from
   * `target: { table: … }` or `target: { view: … }`. Lets inline
   * attribute/column mappings on an entity with no inline mapping block resolve
   * their columns against this table/view.
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
   *
   * `packageName` is the file's **effective package** (PD1.3) — declaration if
   * present, else directory-derived with the configured root prefix. When
   * omitted it falls back to the in-file declaration (the pre-PD1 behaviour),
   * which keeps direct constructions in tests working.
   */
  constructor(
    documentUri: string,
    ast: Document,
    schemaCode: string,
    namespace: string,
    packageName?: string
  ) {
    this.documentUri = documentUri;
    this.schemaCode = schemaCode;
    this.namespace = namespace;
    this.packageName = packageName ?? ast.packageDecl?.name ?? '';

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
    // namespace → MD kind namespace → schema default (db ⇒ dbo) → parent kind.
    segments.push(this.namespace || namespaceForKind(parentEntry.kind) || defaultNamespaceForSchema(parentEntry.schemaCode) || parentEntry.kind);
    segments.push(parentEntry.name, childName);
    return segments.join('.');
  }

  private addEntry(def: Definition, parentQname?: string): void {
    const schema = this.effectiveSchema(def.kind);
    // namespace → MD kind namespace → schema's default namespace (db ⇒ dbo) → def kind.
    const nsOrKind = this.namespace || namespaceForKind(def.kind) || defaultNamespaceForSchema(schema) || def.kind;
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
    if (def.kind === 'mdDomain') {
      if (def.type) entry.domainType = def.type.kind === 'simple' ? def.type.name : def.type.typeName;
      const rangeClause = def.restrict?.find((c) => c.clause === 'range');
      if (rangeClause && !Array.isArray(rangeClause.value) && rangeClause.value.kind === 'rangeLiteral') {
        entry.domainRange = { lo: rangeClause.value.lo, hi: rangeClause.value.hi };
      }
    }
    if (def.kind === 'er2dbEntity' && def.target) {
      const t = def.target;
      if ('kind' in t) {
        // ObjectValue: `target: { table: db.dbo.X }` or `target: { view: db.dbo.V }`
        const tableEntry = t.entries.find((e) => e.key === 'table' || e.key === 'view');
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

    // v3.1 MD — dimension attributes register dimension-qualified
    // (`md.dimension.<Dim>.<attr>`), addressable both dotted (`Customer.code`)
    // and bare within the dimension. Mirrors the entity→attribute block.
    if (def.kind === 'dimension' && def.attributes) {
      const dimEntry = entry;
      for (const attr of def.attributes) {
        const attrQnameStr = this.makeQnameChild(dimEntry, attr.name);
        this.entries.set(attrQnameStr, {
          qname: attrQnameStr,
          kind: 'attribute',
          name: attr.name,
          source: attr.source,
          documentUri: this.documentUri,
          parent: dimEntry.qname,
          packageName: this.packageName,
          schemaCode: entry.schemaCode,
          domainRef: attr.domainRef,
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