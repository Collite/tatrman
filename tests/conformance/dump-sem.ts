/**
 * TS side of the *semantics* conformance dump (contracts.md §5). For each
 * fixture it loads the stock vocab, builds the project symbol table, resolves
 * every reference and runs the portable validator subset, then emits a
 * normalised `{ diagnostics, resolved }` object that must be byte-identical to
 * the Kotlin `SemanticsConformanceDump` output.
 *
 * Normalisation (both runtimes):
 *  - `symbols`: sorted full qnames of every definition in the scenario's own
 *    documents (stock vocab excluded). This is what compares the schema-code
 *    prefix directly, so kind-derived defaults (pkg-schema-defaults) are checked
 *    even when a fixture has no resolvable references.
 *  - `resolved`: sorted `"<refPath> => <resolvedQname>"` strings (one per
 *    reference that resolves) — no source positions.
 *  - `diagnostics`: sorted diagnostic-code strings (code only; severity is
 *    consumer policy and positions are impl-specific).
 */
import type { Document } from '@modeler/parser';
import {
  ProjectSymbolTable,
  Resolver,
  Validator,
  collectAllReferences,
  enclosingQnameOf,
  resolveManifest,
} from '@modeler/semantics';

export interface SemDump {
  resolved: string[];
  diagnostics: string[];
  symbols: string[];
}

/** One parsed document in a (possibly multi-file) scenario. */
export interface SemDocInput {
  ast: Document;
  uri: string;
}

/** Single-document dump — the common case. A 1-element delegation to {@link dumpSemDocs}. */
export function dumpSem(ast: Document | undefined, uri: string, stock: Map<string, Document>): SemDump {
  if (!ast) return dumpSemDocs([], stock);
  return dumpSemDocs([{ ast, uri }], stock);
}

/**
 * Multi-document dump: builds ONE project symbol table from the stock vocab plus
 * every document in the scenario, then resolves references and runs the portable
 * validator subset across all of them. This is what exercises cross-file
 * resolution (same-package siblings, named/wildcard imports) — see the
 * `fixtures/<scenario>/` directories. For a single document it is byte-identical
 * to the previous single-doc dump.
 */
export function dumpSemDocs(docs: SemDocInput[], stock: Map<string, Document>): SemDump {
  const symbols = new ProjectSymbolTable();
  for (const [name, doc] of stock) {
    symbols.upsertDocument(`stock://${name}.ttr`, doc, 'cnc', 'role', '');
  }

  // Upsert every document FIRST so cross-document lookups (getByPackage,
  // named/wildcard imports, getBySuffix) see the whole project.
  const metas = docs.map(({ ast, uri }) => {
    // '' (no directive) ⇒ the semantics layer derives the schema per definition
    // from its kind. Must match the Kotlin dump (`?: ""`) for byte-identical output.
    const schemaCode = ast.schemaDirective?.schemaCode ?? '';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';
    symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);
    return { ast, uri, schemaCode, namespace, packageName };
  });

  const resolver = new Resolver(symbols);
  const validator = new Validator(symbols, resolver, resolveManifest(undefined, ''));

  const resolved: string[] = [];
  const diagnostics: string[] = [];
  for (const m of metas) {
    for (const { ref, ownerDef } of collectAllReferences(m.ast)) {
      const enclosingQname = enclosingQnameOf(ownerDef, m.schemaCode, m.namespace, m.packageName);
      const res = resolver.resolveReference(
        { path: ref.path, parts: ref.parts },
        { schemaCode: m.schemaCode, namespace: m.namespace, enclosingQname, imports: m.ast.imports, packageName: m.packageName }
      );
      if (res.resolved) resolved.push(`${ref.path} => ${res.symbol.qname}`);
    }
    diagnostics.push(
      ...validator.validateDocument(m.uri, m.ast).map((d) => d.code),
      ...validator.validateReferences(m.uri, m.ast).map((d) => d.code),
      ...validator.validateImports(m.uri, m.ast).map((d) => d.code)
    );
  }
  // validateProject() is project-global — run once across all documents.
  diagnostics.push(...validator.validateProject().map((d) => d.code));

  // Full qnames of the scenario's own definitions (stock vocab excluded).
  const symbolQnames = symbols
    .all()
    .filter((e) => !e.documentUri.startsWith('stock://'))
    .map((e) => e.qname);

  resolved.sort();
  diagnostics.sort();
  symbolQnames.sort();
  return { resolved, diagnostics, symbols: symbolQnames };
}

/** Keys ordered `diagnostics`, `resolved`, `symbols`; matches the Kotlin render. */
export function renderSem(d: SemDump): string {
  return JSON.stringify({ diagnostics: d.diagnostics, resolved: d.resolved, symbols: d.symbols }, null, 4);
}
