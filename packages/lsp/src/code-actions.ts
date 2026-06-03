import { CodeAction, CodeActionKind } from 'vscode-languageserver';
import type { Diagnostic, TextEdit, WorkspaceEdit } from 'vscode-languageserver';
import type { Document, Definition } from '@modeler/parser';
import { buildImportTextEdit } from './import-edits.js';
import { formatDocument, type FormatConfig } from './formatter/format.js';

function textDocEdit(uri: string, edits: TextEdit[]): WorkspaceEdit {
  return { documentChanges: [{ textDocument: { uri, version: null }, edits }] };
}

/** ttr/unimported-reference → "Add import for <pkg>". */
export function quickFixUnimportedReference(
  uri: string, content: string, doc: Document, targetPkg: string, diagnostic: Diagnostic,
): CodeAction | null {
  const result = buildImportTextEdit(content, doc, targetPkg);
  if (!result) return null;
  return {
    title: `Add import for \`${targetPkg}\``,
    kind: CodeActionKind.QuickFix,
    diagnostics: [diagnostic],
    isPreferred: true,
    edit: textDocEdit(uri, [result.edit]),
  };
}

/** ttr/unused-import → "Remove unused import" (deletes the whole line). */
export function quickFixUnusedImport(uri: string, diagnostic: Diagnostic): CodeAction {
  const line = diagnostic.range.start.line;
  const edit: TextEdit = {
    range: { start: { line, character: 0 }, end: { line: line + 1, character: 0 } },
    newText: '',
  };
  return {
    title: 'Remove unused import',
    kind: CodeActionKind.QuickFix,
    diagnostics: [diagnostic],
    edit: textDocEdit(uri, [edit]),
  };
}

/** ttr/missing-package-declaration → "Add `package <inferred>`". */
export function quickFixMissingPackageDeclaration(
  uri: string, inferred: string, diagnostic: Diagnostic,
): CodeAction {
  const edit: TextEdit = {
    range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    newText: `package ${inferred}\n\n`,
  };
  return {
    title: `Add \`package ${inferred}\``,
    kind: CodeActionKind.QuickFix,
    diagnostics: [diagnostic],
    isPreferred: true,
    edit: textDocEdit(uri, [edit]),
  };
}

/** ttr/package-declaration-mismatch → "Update declaration to match directory". */
export function quickFixPackageDeclarationMismatch(
  uri: string, content: string, doc: Document, inferred: string, diagnostic: Diagnostic,
): CodeAction | null {
  if (!doc.packageDecl) return null;
  const lineIdx = doc.packageDecl.source.line - 1;
  const lineText = content.split('\n')[lineIdx] ?? '';
  const nameCol = lineText.indexOf(doc.packageDecl.name);
  if (nameCol < 0) return null;
  const edit: TextEdit = {
    range: { start: { line: lineIdx, character: nameCol }, end: { line: lineIdx, character: nameCol + doc.packageDecl.name.length } },
    newText: inferred,
  };
  return {
    title: 'Update declaration to match directory',
    kind: CodeActionKind.QuickFix,
    diagnostics: [diagnostic],
    isPreferred: true,
    edit: textDocEdit(uri, [edit]),
  };
}

/**
 * refactor.extract → move a top-level def into its own file in the SAME package
 * (a sibling file in the current directory). Because the package is unchanged,
 * same-package references keep resolving, so no `import` is added — the
 * cross-package extract variant (which would need an import) is out of scope.
 */
export function refactorExtractDefToNewFile(
  uri: string, content: string, doc: Definition, ast: Document, formatConfig: FormatConfig,
): CodeAction | null {
  const pkg = ast.packageDecl?.name;
  const sd = ast.schemaDirective;
  if (!sd) return null;

  // New file lives beside the current file (same package directory).
  const dir = uri.replace(/\/[^/]+$/, '');
  const newUri = `${dir}/${doc.name}.ttr`;

  // Format the extracted def by feeding a synthetic single-def document through
  // the formatter; the def's source offsets still point into `content`.
  const synthetic: Document = {
    packageDecl: ast.packageDecl,
    imports: [],
    schemaDirective: sd,
    definitions: [doc],
    source: ast.source,
  };
  const newContent = formatDocument(synthetic, content, formatConfig);

  // Remove the def (and a trailing blank line, if any) from the current file.
  const startLine = doc.source.line - 1;
  const endLine = doc.source.endLine - 1;
  const removeEnd = { line: endLine + 1, character: 0 };

  const editWs: WorkspaceEdit = {
    documentChanges: [
      { kind: 'create', uri: newUri },
      { textDocument: { uri: newUri, version: null }, edits: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, newText: newContent }] },
      { textDocument: { uri, version: null }, edits: [{ range: { start: { line: startLine, character: 0 }, end: removeEnd }, newText: '' }] },
    ],
  };

  return {
    title: `Extract \`${doc.name}\` to new file in package ${pkg ?? '(root)'}`,
    kind: CodeActionKind.RefactorExtract,
    edit: editWs,
  };
}
