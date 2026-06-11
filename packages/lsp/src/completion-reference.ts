import {
  CompletionItem,
  CompletionItemKind,
  CompletionList,
  TextEdit,
} from 'vscode-languageserver';
import type {
  Document,
  Definition,
  ImportDecl,
  PropertyValue,
  Reference,
} from '@modeler/parser';
import { packageOfImport } from '@modeler/semantics';
import type { ProjectSymbolTable, SymbolEntry } from '@modeler/semantics';
import { buildImportTextEdit } from '@modeler/edit';

export interface ReferenceCompletionOptions {
  position: { line: number; character: number };
  content: string;
  doc: Document;
  projectSymbols: ProjectSymbolTable;
  autoImport: boolean;
  /**
   * Optional query string (the partial identifier being typed, before the cursor).
   * When provided, used as a relevance tiebreaker within each bucket.
   */
  query?: string;
  /**
   * Maximum number of candidates to return. Default 50.
   */
  limit?: number;
}

type ReferenceProperty =
  | 'from'
  | 'to'
  | 'nameAttribute'
  | 'codeAttribute'
  | 'entity'
  | 'attribute'
  | 'relation'
  | 'fk'
  | 'role';

const REFERENCE_PROPERTIES: ReferenceProperty[] = [
  'from',
  'to',
  'nameAttribute',
  'codeAttribute',
  'entity',
  'attribute',
  'relation',
  'fk',
  'role',
];

const PROPERTY_ACCEPTS: Record<ReferenceProperty, string[]> = {
  from: ['entity', 'table', 'view'],
  to: ['entity', 'table', 'view'],
  nameAttribute: ['attribute', 'column'],
  codeAttribute: ['attribute', 'column'],
  entity: ['entity', 'table', 'view'],
  attribute: ['attribute', 'column'],
  relation: ['relation'],
  fk: ['fk'],
  role: ['role'],
};

const BUCKET_RANK: Record<Bucket, string> = {
  'same-package': '0',
  'named-import': '1',
  'wildcard-import': '2',
  'unimported': '3',
};

type Bucket = 'same-package' | 'named-import' | 'wildcard-import' | 'unimported';

interface ContextInfo {
  property: ReferenceProperty;
  enclosingDef: Definition;
  packageName: string;
}

interface Candidate {
  entry: SymbolEntry;
  bucket: Bucket;
  importDecl?: ImportDecl;
}

export function detectReferenceProperty(
  doc: Document,
  position: { line: number; character: number }
): ContextInfo | null {
  const line = position.line + 1;
  const char = position.character;

  for (const def of doc.definitions) {
    if (!isInRange(line, char, def.source)) continue;

    const info = matchReferencePropertyPosition(def, line, char);
    if (info) return { ...info, packageName: doc.packageDecl?.name ?? '' };

    for (const child of nestedDefArray(def)) {
      if (!isInRange(line, char, child.source)) continue;
      const childInfo = matchReferencePropertyPosition(child, line, char);
      if (childInfo) return { ...childInfo, packageName: doc.packageDecl?.name ?? '' };
    }
  }

  if (doc.schemaDirective?.schemaCode) {
    for (const def of doc.definitions) {
      const emptyInfo = detectEmptyReferencePosition(def, line, char, doc.schemaDirective.schemaCode);
      if (emptyInfo) return { ...emptyInfo, packageName: doc.packageDecl?.name ?? '' };
    }
  }

  return null;
}

function detectEmptyReferencePosition(
  def: Definition,
  line: number,
  char: number,
  _schemaCode: string
): Omit<ContextInfo, 'packageName'> | null {
  for (const prop of REFERENCE_PROPERTIES) {
    const val = (def as unknown as Record<string, unknown>)[prop] as
      | Reference
      | PropertyValue
      | undefined;
    if (!val) continue;

    if (prop === 'from' || prop === 'to') {
      const pv = val as PropertyValue;
      if (pv.kind === 'id') {
        const propLineIdx = pv.source.line - 1;
        if (line === propLineIdx && char >= pv.source.column && char <= pv.source.endColumn) {
          return { property: prop, enclosingDef: def };
        }
      } else {
        const propLine = findPropertyLine(def, prop);
        if (propLine !== null && line === propLine && char > findPropertyColonIndex(def, prop)) {
          return { property: prop, enclosingDef: def };
        }
      }
    }
  }
  return null;
}

function findPropertyLine(def: Definition, prop: string): number | null {
  const val = (def as unknown as Record<string, unknown>)[prop];
  if (!val) return null;
  if (typeof val === 'object' && 'source' in val) {
    return (val as Reference).source.line - 1;
  }
  return null;
}

function findPropertyColonIndex(def: Definition, prop: string): number {
  const val = (def as unknown as Record<string, unknown>)[prop];
  if (!val) return 0;
  const source = (val as Reference).source;
  return source.column;
}

function matchReferencePropertyPosition(
  def: Definition,
  line: number,
  char: number
): Omit<ContextInfo, 'packageName'> | null {
  for (const prop of REFERENCE_PROPERTIES) {
    const val = (def as unknown as Record<string, unknown>)[prop] as
      | Reference
      | PropertyValue
      | undefined;
    if (!val) continue;

    if (prop === 'from' || prop === 'to') {
      const pv = val as PropertyValue;
      if (pv.kind === 'id' && isInRange(line, char, pv.source)) {
        return { property: prop, enclosingDef: def };
      }
      if (pv.kind === 'list') {
        for (const item of pv.items) {
          if (item.kind === 'id' && isInRange(line, char, item.source)) {
            return { property: prop, enclosingDef: def };
          }
        }
      }
      if (pv.kind === 'object') {
        for (const entry of pv.entries) {
          if (
            entry.value.kind === 'id' &&
            isInRange(line, char, entry.value.source)
          ) {
            return { property: prop, enclosingDef: def };
          }
        }
      }
    } else if (
      val &&
      typeof val === 'object' &&
      'source' in val &&
      isInRange(line, char, (val as Reference).source)
    ) {
      return { property: prop, enclosingDef: def };
    }
  }

  return null;
}

function nestedDefArray(def: Definition): Definition[] {
  switch (def.kind) {
    case 'entity':
      return def.attributes ?? [];
    case 'table':
      return [...(def.columns ?? []), ...(def.indices ?? []), ...(def.constraints ?? [])];
    case 'view':
      return def.columns ?? [];
    case 'procedure':
      return def.resultColumns ?? [];
    default:
      return [];
  }
}

function isInRange(
  line: number,
  char: number,
  loc: { line: number; column: number; endLine: number; endColumn: number }
): boolean {
  if (line < loc.line || line > loc.endLine) return false;
  if (line === loc.line && char < loc.column) return false;
  if (line === loc.endLine && char > loc.endColumn) return false;
  return true;
}

export function getReferenceCompletions(
  opts: ReferenceCompletionOptions
): CompletionList | null {
  const ctx = detectReferenceProperty(opts.doc, opts.position);
  if (!ctx) return null;

  const allowedKinds = PROPERTY_ACCEPTS[ctx.property];
  if (!allowedKinds) return null;

  const candidates = buildCandidates(opts.projectSymbols, opts.doc);

  const filtered = candidates.filter((c) => allowedKinds.includes(c.entry.kind));
  if (filtered.length === 0) return null;

  const limit = opts.limit ?? 50;
  const query = opts.query ?? '';
  const items = applySortOrderAndLimit(filtered, query, opts, limit);

  return {
    items,
    isIncomplete: filtered.length > limit,
  };
}

function applySortOrderAndLimit(
  candidates: Candidate[],
  query: string,
  opts: Pick<ReferenceCompletionOptions, 'content' | 'doc' | 'autoImport'>,
  limit: number
): CompletionItem[] {
  if (!query) {
    return candidates.slice(0, limit).map((c) =>
      formatCandidate(c, opts.content, opts.doc, opts.autoImport)
    );
  }

  const buckets: Record<Bucket, Candidate[]> = {
    'same-package': [],
    'named-import': [],
    'wildcard-import': [],
    'unimported': [],
  };
  for (const c of candidates) buckets[c.bucket].push(c);

  const result: CompletionItem[] = [];
  const bucketOrder: Bucket[] = ['same-package', 'named-import', 'wildcard-import', 'unimported'];
  for (const bucket of bucketOrder) {
    const bucketCandidates = buckets[bucket];
    if (bucketCandidates.length === 0) continue;
    const scored = bucketCandidates;
    result.push(
      ...scored
        .slice(0, limit - result.length)
        .map((c: Candidate) => formatCandidate(c, opts.content, opts.doc, opts.autoImport))
    );
    if (result.length >= limit) break;
  }

  return result.slice(0, limit);
}

function buildCandidates(
  symbols: ProjectSymbolTable,
  doc: Document
): Candidate[] {
  const result: Candidate[] = [];
  const seen = new Set<string>();
  const imports = doc.imports ?? [];
  const currentPackage = doc.packageDecl?.name ?? '';

  const samePkgSymbols = symbols.getByPackage(currentPackage);
  for (const entry of samePkgSymbols) {
    if (seen.has(entry.qname)) continue;
    seen.add(entry.qname);
    result.push({ entry, bucket: 'same-package' });
  }

  for (const imp of imports) {
    if (imp.wildcard) continue;
    const pkg = packageOfImport(imp);
    const pkgSymbols = symbols.getByPackage(pkg);
    for (const entry of pkgSymbols) {
      if (seen.has(entry.qname)) continue;
      seen.add(entry.qname);
      result.push({ entry, bucket: 'named-import', importDecl: imp });
    }
  }

  for (const imp of imports) {
    if (!imp.wildcard) continue;
    const pkg = imp.target;
    const pkgSymbols = symbols.getByPackage(pkg);
    for (const entry of pkgSymbols) {
      if (seen.has(entry.qname)) continue;
      seen.add(entry.qname);
      result.push({ entry, bucket: 'wildcard-import', importDecl: imp });
    }
  }

  for (const entry of symbols.all()) {
    if (seen.has(entry.qname)) continue;
    seen.add(entry.qname);
    result.push({ entry, bucket: 'unimported' });
  }

  return result;
}

function formatCandidate(
  cand: Candidate,
  content: string,
  doc: Document,
  autoImport: boolean
): CompletionItem {
  const { entry, bucket, importDecl } = cand;

  let label = entry.name;
  let insertText = entry.name;
  let detail = entry.qname;
  let additionalTextEdits: TextEdit[] | undefined;

  switch (bucket) {
    case 'same-package':
      detail = `${entry.qname} (same package)`;
      break;
    case 'named-import':
      detail = `${entry.qname} (import: ${importDecl?.target})`;
      insertText = entry.name;
      break;
    case 'wildcard-import':
      detail = `${entry.qname} (import: ${importDecl?.target}.*)`;
      insertText = entry.name;
      break;
    case 'unimported':
      detail = `${entry.qname} (unimported)`;
      insertText = entry.qname;
      if (autoImport) {
        const targetPkg = entry.packageName;
        if (targetPkg) {
          const editResult = buildImportTextEdit(content, doc, targetPkg);
          if (editResult) {
            additionalTextEdits = [editResult.edit];
          }
        }
      }
      break;
  }

  const docLines: string[] = [];
  docLines.push(`**${entry.qname}**`);
  docLines.push(`*${entry.kind}*`);
  const defFile = entry.documentUri.split('/').pop() ?? entry.documentUri;
  docLines.push(`Defined in ${defFile}`);

  return {
    label,
    kind: CompletionItemKind.Reference,
    sortText: `${BUCKET_RANK[bucket]}_${entry.name}`,
    detail,
    documentation: { kind: 'markdown', value: docLines.join('\n\n') },
    insertText,
    additionalTextEdits,
  };
}

export function extractQueryPrefix(
  content: string,
  position: { line: number; character: number }
): string {
  const lines = content.split('\n');
  const line = lines[position.line];
  if (!line) return '';
  const before = line.slice(0, position.character);
  const match = before.match(/[a-zA-Z_][a-zA-Z0-9_.]*$/);
  return match ? match[0] : '';
}