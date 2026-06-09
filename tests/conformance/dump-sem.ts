/**
 * TS side of the *semantics* conformance dump (contracts.md §5). For each
 * fixture it loads the stock vocab, builds the project symbol table, resolves
 * every reference and runs the portable validator subset, then emits a
 * normalised `{ diagnostics, resolved }` object that must be byte-identical to
 * the Kotlin `SemanticsConformanceDump` output.
 *
 * Normalisation (both runtimes):
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
}

export function dumpSem(ast: Document | undefined, uri: string, stock: Map<string, Document>): SemDump {
  const symbols = new ProjectSymbolTable();
  for (const [name, doc] of stock) {
    symbols.upsertDocument(`stock://${name}.ttr`, doc, 'cnc', 'role', '');
  }
  if (!ast) return { resolved: [], diagnostics: [] };

  const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
  const namespace = ast.schemaDirective?.namespace ?? '';
  const packageName = ast.packageDecl?.name ?? '';
  symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);

  const resolver = new Resolver(symbols);
  const validator = new Validator(symbols, resolver, resolveManifest(undefined, ''));

  const resolved: string[] = [];
  for (const { ref, ownerDef } of collectAllReferences(ast)) {
    const enclosingQname = enclosingQnameOf(ownerDef, schemaCode, namespace, packageName);
    const res = resolver.resolveReference(
      { path: ref.path, parts: ref.parts },
      { schemaCode, namespace, enclosingQname, imports: ast.imports, packageName }
    );
    if (res.resolved) resolved.push(`${ref.path} => ${res.symbol.qname}`);
  }
  resolved.sort();

  const diagnostics = [
    ...validator.validateDocument(uri, ast),
    ...validator.validateReferences(uri, ast),
    ...validator.validateProject(),
    ...validator.validateImports(uri, ast),
  ]
    .map((d) => d.code)
    .sort();

  return { resolved, diagnostics };
}

/** Keys inserted alphabetically (`diagnostics` before `resolved`); matches `JSON.stringify(_, null, 4)`. */
export function renderSem(d: SemDump): string {
  return JSON.stringify({ diagnostics: d.diagnostics, resolved: d.resolved }, null, 4);
}
