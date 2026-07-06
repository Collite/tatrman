import type { TextEdit } from 'vscode-languageserver-types';
import type { Document, ImportDecl } from '@tatrman/parser';

export interface ImportEditResult {
  edit: TextEdit;
  importDecl: ImportDecl;
  insertedLine: number;
}

function compareImportTargets(a: string, b: string): number {
  return a.localeCompare(b);
}

function findInsertionIndex(imports: ImportDecl[], targetPkg: string): number {
  let lo = 0;
  let hi = imports.length;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (compareImportTargets(imports[mid].target, targetPkg) < 0) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  return lo;
}

function buildImportText(importDecl: ImportDecl): string {
  return `import ${importDecl.target}${importDecl.wildcard ? '.*' : ''}\n`;
}

function isBlankLine(line: string): boolean {
  return line.trim() === '';
}

function nextNonBlankLine(lines: string[], startIdx: number): number {
  for (let i = startIdx; i < lines.length; i++) {
    if (!isBlankLine(lines[i])) return i;
  }
  return lines.length;
}

function findInsertionLineForFirstImport(
  lines: string[],
  doc: Document
): number {
  if (doc.packageDecl) {
    const pkgLineIdx = doc.packageDecl.source.line - 1;
    const afterPkgLineIdx = pkgLineIdx + 1;
    if (afterPkgLineIdx < lines.length && isBlankLine(lines[afterPkgLineIdx])) {
      return afterPkgLineIdx;
    }
    const nextNonBlank = nextNonBlankLine(lines, afterPkgLineIdx);
    if (nextNonBlank < lines.length) {
      return nextNonBlank;
    }
    return lines.length;
  }

  for (let i = 0; i < lines.length; i++) {
    if (!isBlankLine(lines[i])) return i;
  }
  return 0;
}

function wholeLineEdit(line: number, newText: string): TextEdit {
  return {
    range: {
      start: { line, character: 0 },
      end: { line, character: 0 },
    },
    newText,
  };
}

export function buildImportTextEdit(
  content: string,
  doc: Document,
  targetPkg: string,
  wildcard = false
): ImportEditResult | null {
  const imports = doc.imports ?? [];
  const existing = imports.find((i) => i.target === targetPkg);
  if (existing) return null;

  const lines = content.split('\n');
  const importDecl: ImportDecl = {
    kind: 'importDecl',
    target: targetPkg,
    targetParts: targetPkg.split('.'),
    wildcard,
    source: { file: doc.source.file, line: 0, column: 0, endLine: 0, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
  };

  if (imports.length === 0) {
    const insertLine = findInsertionLineForFirstImport(lines, doc);
    const edit = wholeLineEdit(insertLine, buildImportText(importDecl));
    return { edit, importDecl, insertedLine: insertLine };
  }

  const insertionIndex = findInsertionIndex(imports, targetPkg);
  let insertLine: number;

  if (insertionIndex < imports.length) {
    insertLine = imports[insertionIndex].source.line - 1;
  } else {
    insertLine = imports[imports.length - 1].source.endLine;
  }

  const edit = wholeLineEdit(insertLine, buildImportText(importDecl));
  return { edit, importDecl, insertedLine: insertLine };
}