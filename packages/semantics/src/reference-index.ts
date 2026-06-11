import type { Definition, Document, SourceLocation } from '@modeler/parser';
import { collectAllReferences } from './references.js';
import { defaultSchemaForKind } from './default-schema.js';
import type { Resolver } from './resolver.js';

/**
 * `schemaCode` is the file's `schema` directive code, or `''` when the file has
 * no directive — in which case the schema is derived from the def's kind,
 * symmetric to the `namespace || def.kind` fallback.
 */
export function enclosingQnameOf(def: Definition, schemaCode: string, namespace: string, packageName?: string): string | undefined {
  if (
    def.kind === 'entity' || def.kind === 'table' || def.kind === 'view' || def.kind === 'procedure' ||
    def.kind === 'relation' || def.kind === 'query' || def.kind === 'role' ||
    def.kind === 'er2dbEntity' || def.kind === 'er2dbAttribute' ||
    def.kind === 'er2dbRelation' || def.kind === 'er2cncRole'
  ) {
    const schema = schemaCode || defaultSchemaForKind(def.kind);
    const nsOrKind = namespace || def.kind;
    const segments: string[] = [];
    if (packageName) segments.push(packageName);
    segments.push(schema);
    if (nsOrKind) segments.push(nsOrKind);
    segments.push(def.name);
    return segments.join('.');
  }
  return undefined;
}

export interface ReferenceLocation {
  documentUri: string;
  source: SourceLocation;
  /** The canonical qname this reference resolved to. */
  targetQname: string;
  /** The qname of the def that contains the reference (null when the ref is not inside a def). */
  referrerQname: string | null;
}

/**
 * Reverse index: for each target qname, the list of reference locations that
 * resolve to it. Built incrementally as documents are upserted.
 */
export class ReferenceIndex {
  private byDocument: Map<string, ReferenceLocation[]> = new Map();
  private byTargetQname: Map<string, ReferenceLocation[]> = new Map();

  upsertDocument(
    uri: string,
    ast: Document,
    schemaCode: string,
    namespace: string,
    resolver: Resolver,
    packageName?: string
  ): void {
    this.removeDocument(uri);
    const locations: ReferenceLocation[] = [];

    for (const { ref, ownerDef } of collectAllReferences(ast)) {
      // When the file has no `schema` directive, `schemaCode` is '' and the
      // effective schema is derived from the referring def's kind.
      const effSchema = schemaCode || defaultSchemaForKind(ownerDef.kind);
      const referrerQname = enclosingQnameOf(ownerDef, effSchema, namespace, packageName) ?? null;
      const res = resolver.resolveReference(
        { path: ref.path, parts: ref.parts },
        { schemaCode: effSchema, namespace, enclosingQname: referrerQname ?? undefined, packageName }
      );
      if (!res.resolved) continue;
      const loc: ReferenceLocation = {
        documentUri: uri,
        source: ref.source,
        targetQname: res.symbol.qname,
        referrerQname,
      };
      locations.push(loc);
      const list = this.byTargetQname.get(res.symbol.qname) ?? [];
      list.push(loc);
      this.byTargetQname.set(res.symbol.qname, list);
    }

    this.byDocument.set(uri, locations);
  }

  removeDocument(uri: string): void {
    const old = this.byDocument.get(uri);
    if (!old) return;
    for (const loc of old) {
      const list = this.byTargetQname.get(loc.targetQname);
      if (!list) continue;
      const filtered = list.filter((l) => l.documentUri !== uri);
      if (filtered.length === 0) {
        this.byTargetQname.delete(loc.targetQname);
      } else {
        this.byTargetQname.set(loc.targetQname, filtered);
      }
    }
    this.byDocument.delete(uri);
  }

  findByQname(qname: string): ReferenceLocation[] {
    return this.byTargetQname.get(qname) ?? [];
  }

  /** All resolved references that occur within `uri` (for semantic tokens). */
  getForDocument(uri: string): ReferenceLocation[] {
    return this.byDocument.get(uri) ?? [];
  }
}
