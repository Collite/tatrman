import type { WorkspaceEdit, TextEdit, Range } from 'vscode-languageserver-types';
import type { Document } from '@tatrman/parser';
import { buildImportTextEdit } from './import-edits.js';

/** Wrap TextEdits for one document into a versionless WorkspaceEdit. */
export function textDocEdit(uri: string, edits: TextEdit[]): WorkspaceEdit {
  return { documentChanges: [{ textDocument: { uri, version: null }, edits }] };
}

/** Delete the whole line `line0` (0-indexed) — used for import removals. */
export function removeLineEdit(uri: string, line0: number): WorkspaceEdit {
  return textDocEdit(uri, [
    { range: { start: { line: line0, character: 0 }, end: { line: line0 + 1, character: 0 } }, newText: '' },
  ]);
}

/** Insert text at the very top of the document (line 0). */
export function insertAtTopEdit(uri: string, newText: string): WorkspaceEdit {
  return textDocEdit(uri, [
    { range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, newText },
  ]);
}

/** Replace an exact range with `newText`. */
export function replaceRangeEdit(uri: string, range: Range, newText: string): WorkspaceEdit {
  return textDocEdit(uri, [{ range, newText }]);
}

/** Insert `newText` at a position. */
export function insertEdit(uri: string, line0: number, char: number, newText: string): WorkspaceEdit {
  return textDocEdit(uri, [
    { range: { start: { line: line0, character: char }, end: { line: line0, character: char } }, newText },
  ]);
}

/**
 * `ttr/unimported-reference` → add the computed `import`. Returns null if the
 * import already exists or no insertion point can be found.
 */
export function buildAddImportEdit(
  uri: string,
  content: string,
  doc: Document,
  targetPkg: string,
  wildcard = false
): WorkspaceEdit | null {
  const result = buildImportTextEdit(content, doc, targetPkg, wildcard);
  if (!result) return null;
  return textDocEdit(uri, [result.edit]);
}

/** Apply a WorkspaceEdit's TextEdits for `uri` to `text` (offset-based, in order). */
export function applyWorkspaceEditToText(text: string, edit: WorkspaceEdit, uri: string): string {
  const edits: TextEdit[] = [];
  for (const change of edit.documentChanges ?? []) {
    if ('textDocument' in change && change.textDocument.uri === uri) edits.push(...change.edits);
  }
  if (edits.length === 0) return text;
  // Convert to absolute offsets, sort descending, splice so earlier edits don't shift later offsets.
  const lineStarts = computeLineStarts(text);
  const withOffsets = edits.map((e) => ({
    start: offsetOf(lineStarts, e.range.start.line, e.range.start.character, text.length),
    end: offsetOf(lineStarts, e.range.end.line, e.range.end.character, text.length),
    newText: e.newText,
  }));
  withOffsets.sort((a, b) => b.start - a.start);
  let out = text;
  for (const e of withOffsets) out = out.slice(0, e.start) + e.newText + out.slice(e.end);
  return out;
}

function computeLineStarts(text: string): number[] {
  const starts = [0];
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '\n') starts.push(i + 1);
  }
  return starts;
}

function offsetOf(lineStarts: number[], line: number, character: number, max: number): number {
  if (line >= lineStarts.length) return max;
  return Math.min(lineStarts[line] + character, max);
}
