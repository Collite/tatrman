// SPDX-License-Identifier: Apache-2.0
import type { WorkspaceEdit, TextDocumentEdit, Position } from 'vscode-languageserver-types';

function offsetToPosition(content: string, offset: number): Position {
  let line = 0;
  let character = 0;
  for (let i = 0; i < offset && i < content.length; i++) {
    if (content[i] === '\n') { line++; character = 0; }
    else { character++; }
  }
  return { line, character };
}

function buildTextEdit(uri: string, start: Position, end: Position, newText: string): TextDocumentEdit {
  return {
    textDocument: { uri, version: null },
    edits: [{ range: { start, end }, newText }],
  };
}

function findObjectsBrackets(content: string): { openOffset: number; closeOffset: number } | null {
  const objectsKeywordIdx = content.indexOf('objects');
  if (objectsKeywordIdx === -1) return null;
  const bracketIdx = content.indexOf('[', objectsKeywordIdx);
  if (bracketIdx === -1) return null;
  let depth = 0;
  let closeOffset = -1;
  for (let i = bracketIdx; i < content.length; i++) {
    if (content[i] === '[') depth++;
    else if (content[i] === ']') { depth--; if (depth === 0) { closeOffset = i; break; } }
  }
  if (closeOffset === -1) return null;
  return { openOffset: bracketIdx, closeOffset };
}

function replaceQnameInObjectsList(
  content: string,
  uri: string,
  oldPrefix: string,
  newPrefix: string,
): TextDocumentEdit[] {
  const brackets = findObjectsBrackets(content);
  if (!brackets) return [];
  const { openOffset, closeOffset } = brackets;
  const inner = content.slice(openOffset + 1, closeOffset);
  const trimmed = inner.trim();
  if (!trimmed) return [];

  const parts = trimmed.split(',').map(s => s.trim()).filter(Boolean);
  let changed = false;
  const newParts = parts.map(part => {
    if (part.startsWith(oldPrefix)) {
      changed = true;
      return newPrefix + part.slice(oldPrefix.length);
    }
    return part;
  });
  if (!changed) return [];

  return [buildTextEdit(
    uri,
    offsetToPosition(content, openOffset + 1),
    offsetToPosition(content, closeOffset),
    newParts.join(', '),
  )];
}

export interface RenamePackageEditParams {
  oldPackageName: string;
  newPackageName: string;
  allDocuments: Map<string, string>;
}

export function buildRenamePackageEdit(params: RenamePackageEditParams): WorkspaceEdit {
  const { oldPackageName, newPackageName, allDocuments } = params;
  const edits: TextDocumentEdit[] = [];

  for (const [uri, content] of allDocuments) {
    if (uri.endsWith('.ttrg')) {
      edits.push(...replaceQnameInObjectsList(content, uri, oldPackageName + '.', newPackageName + '.'));
      continue;
    }

    const packageDeclMatch = content.match(new RegExp(`(^|\\n)package\\s+${escapeRegExp(oldPackageName)}($|\\n)`));
    if (packageDeclMatch) {
      const idx = content.search(new RegExp(`(^|\\n)package\\s+${escapeRegExp(oldPackageName)}($|\\n)`));
      const before = idx === 0 ? '' : content.slice(0, idx).match(/\n?$/)![0];
      // Edit only the name span (after the `package ` keyword), so the keyword
      // is preserved — replacing the whole `package <name>` span dropped it.
      const nameStartOffset = idx + before.length + 'package '.length;
      const startPos = offsetToPosition(content, nameStartOffset);
      const endPos = offsetToPosition(content, nameStartOffset + oldPackageName.length);
      edits.push(buildTextEdit(uri, startPos, endPos, newPackageName));
    }

    const importPrefix = oldPackageName + '.';
    const importExact = oldPackageName;
    for (const match of content.matchAll(new RegExp(`import\\s+(${escapeRegExp(importExact)}(\\.\*)?|${escapeRegExp(importPrefix)}[\\w.]+)`, 'g'))) {
      const fullMatch = match[0];
      const imported = match[1];
      const newImported = imported.replace(oldPackageName, newPackageName);
      if (newImported === imported) continue;
      const startOffset = match.index! + fullMatch.indexOf(imported);
      edits.push(buildTextEdit(
        uri,
        offsetToPosition(content, startOffset),
        offsetToPosition(content, startOffset + imported.length),
        newImported,
      ));
    }
  }

  return { documentChanges: edits };
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}