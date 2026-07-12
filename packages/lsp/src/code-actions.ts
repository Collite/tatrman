// SPDX-License-Identifier: Apache-2.0
import { CodeAction, CodeActionKind } from 'vscode-languageserver';
import type { WorkspaceEdit } from 'vscode-languageserver';
import type { Document, Definition } from '@tatrman/parser';
import { formatDocument, type FormatConfig } from '@tatrman/format';

/**
 * refactor.extract → move a top-level def into its own file in the SAME package
 * (a sibling file in the current directory). Because the package is unchanged,
 * same-package references keep resolving, so no `import` is added — the
 * cross-package extract variant (which would need an import) is out of scope.
 *
 * The autofix quick-fixes that used to live here are now `Rule.fix` builders in
 * `@tatrman/lint` (over `@tatrman/edit`); `onCodeAction` wires them generically.
 */
export function refactorExtractDefToNewFile(
  uri: string, content: string, doc: Definition, ast: Document, formatConfig: FormatConfig,
): CodeAction | null {
  const pkg = ast.packageDecl?.name;
  const sd = ast.modelDirective;
  if (!sd) return null;

  // New file lives beside the current file (same package directory).
  const dir = uri.replace(/\/[^/]+$/, '');
  const newUri = `${dir}/${doc.name}.ttrm`;

  // Format the extracted def by feeding a synthetic single-def document through
  // the formatter; the def's source offsets still point into `content`.
  const synthetic: Document = {
    packageDecl: ast.packageDecl,
    imports: [],
    modelDirective: sd,
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
