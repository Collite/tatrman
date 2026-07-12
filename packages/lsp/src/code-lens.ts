// SPDX-License-Identifier: Apache-2.0
import type { CodeLens, Range } from 'vscode-languageserver';
import type { Document, Definition } from '@tatrman/parser';
import type { ReferenceIndex, ProjectSymbolTable } from '@tatrman/semantics';

export interface CodeLensContext {
  ast: Document;
  refIndex: ReferenceIndex;
  projectSymbols: ProjectSymbolTable;
}

function lineRange(lineIdx: number): Range {
  return { start: { line: lineIdx, character: 0 }, end: { line: lineIdx, character: 0 } };
}

// Package-qualified qname of a top-level def, matching the symbol-table keys.
function topLevelQname(def: Definition, ast: Document): string {
  const pkg = ast.packageDecl?.name ?? '';
  const schemaCode = ast.modelDirective?.modelCode ?? '';
  const namespace = ast.modelDirective?.schema ?? '';
  return [pkg, schemaCode, namespace, def.name].filter((s) => s !== '').join('.');
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

export function getCodeLenses(ctx: CodeLensContext): CodeLens[] {
  const { ast, refIndex, projectSymbols } = ctx;
  const lenses: CodeLens[] = [];

  // "N files in package" on the package declaration.
  if (ast.packageDecl) {
    const pkg = ast.packageDecl.name;
    const files = new Set(projectSymbols.getByPackage(pkg).map((s) => s.documentUri)).size;
    lenses.push({
      range: lineRange(ast.packageDecl.source.line - 1),
      command: { title: plural(files, 'file') + ' in package', command: 'modeler.listPackageFiles', arguments: [pkg] },
    });
  }

  // "N references" on every top-level def header.
  for (const def of ast.definitions) {
    const qname = topLevelQname(def, ast);
    const n = refIndex.findByQname(qname).length;
    lenses.push({
      range: lineRange(def.source.line - 1),
      command: { title: plural(n, 'reference'), command: 'modeler.showReferences', arguments: [qname] },
    });
  }

  return lenses;
}
