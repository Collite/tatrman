import type { WorkspaceEdit, TextDocumentEdit } from 'vscode-languageserver-types';

function positionToOffset(content: string, line: number, character: number): number {
  let currentLine = 0;
  let currentChar = 0;
  for (let i = 0; i < content.length; i++) {
    if (currentLine === line && currentChar === character) return i;
    if (content[i] === '\n') {
      currentLine++;
      currentChar = 0;
    } else {
      currentChar++;
    }
  }
  return content.length;
}

function applyTextEdits(content: string, edits: Array<{ range: { start: { line: number; character: number }; end: { line: number; character: number } }; newText: string }>): string {
  const sorted = [...edits].sort((a, b) => {
    const aStart = positionToOffset(content, a.range.start.line, a.range.start.character);
    const bStart = positionToOffset(content, b.range.start.line, b.range.start.character);
    return bStart - aStart;
  });

  let result = content;
  for (const edit of sorted) {
    const start = positionToOffset(result, edit.range.start.line, edit.range.start.character);
    const end = positionToOffset(result, edit.range.end.line, edit.range.end.character);
    result = result.slice(0, start) + edit.newText + result.slice(end);
  }
  return result;
}

/**
 * Applies a WorkspaceEdit's text edits to the in-memory worker documents.
 *
 * `getText` reads the current text of a document; `openDoc` writes the patched
 * text back. The patched text MUST be written through `openDoc` (not directly
 * via the LSP client) so the caller's document cache stays in sync — otherwise
 * a subsequent edit, whose ranges the server computes against the now-updated
 * document, would be applied to stale text and corrupt the file.
 */
export async function applyWorkspaceEdit(
  edit: WorkspaceEdit,
  getText: (uri: string) => string | undefined,
  openDoc: (uri: string, content: string) => Promise<void>,
): Promise<string[]> {
  if (!edit.documentChanges?.length) return [];

  const uriToEdits = new Map<string, TextDocumentEdit[]>();
  for (const change of edit.documentChanges) {
    if ('textDocument' in change) {
      const td = change as TextDocumentEdit;
      uriToEdits.set(td.textDocument.uri, [...(uriToEdits.get(td.textDocument.uri) ?? []), td]);
    }
  }

  const affectedUris: string[] = [];
  for (const [uri, textEdits] of uriToEdits) {
    const currentText = getText(uri) ?? '';
    const patched = applyTextEdits(currentText, textEdits.flatMap(e => e.edits));
    await openDoc(uri, patched);
    affectedUris.push(uri);
  }

  return affectedUris;
}