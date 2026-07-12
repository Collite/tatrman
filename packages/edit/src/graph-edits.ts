// SPDX-License-Identifier: Apache-2.0
import type { WorkspaceEdit, TextDocumentEdit, Position, CreateFile } from 'vscode-languageserver-types';

export interface AddObjectParams {
  uri: string;
  qname: string;
  autoImport: boolean;
}

export interface RemoveObjectParams {
  uri: string;
  qname: string;
  pruneUnusedImport: boolean;
}

export interface CreateGraphParams {
  uri: string;
  name: string;
  schema: 'db' | 'er' | 'binding' | 'query' | 'cnc';
  packages: string[];
  objects: string[];
  description?: string;
  tags?: string[];
}

function offsetToPosition(content: string, offset: number): Position {
  let line = 0;
  let character = 0;
  for (let i = 0; i < offset && i < content.length; i++) {
    if (content[i] === '\n') {
      line++;
      character = 0;
    } else {
      character++;
    }
  }
  return { line, character };
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
    else if (content[i] === ']') {
      depth--;
      if (depth === 0) {
        closeOffset = i;
        break;
      }
    }
  }

  if (closeOffset === -1) return null;
  return { openOffset: bracketIdx, closeOffset };
}

function hasImportForPackage(content: string, packageName: string): boolean {
  const pattern = new RegExp(`import\\s+${packageName}\\b`);
  return pattern.test(content);
}

function findLastImportLineOffset(content: string): number {
  const lastImportIdx = content.lastIndexOf('import ');
  if (lastImportIdx === -1) return 0;
  const lineEnd = content.indexOf('\n', lastImportIdx);
  return lineEnd === -1 ? content.length : lineEnd + 1;
}

function findImportRange(content: string, packageName: string): { start: number; end: number } | null {
  const pattern = new RegExp(`import\\s+${packageName}\\b[^\\n]*\\n?`);
  const match = content.match(pattern);
  if (!match) return null;
  const idx = content.search(pattern);
  return { start: idx, end: idx + match[0].length };
}

function buildTextEdit(uri: string, start: Position, end: Position, newText: string, version: number | null): TextDocumentEdit {
  return {
    textDocument: { uri, version },
    edits: [{ range: { start, end }, newText }],
  };
}

export function buildAddObjectEdit(
  graphContent: string,
  graphUri: string,
  qname: string,
  packageToImport: string | null,
): WorkspaceEdit {
  const brackets = findObjectsBrackets(graphContent);
  if (!brackets) return { documentChanges: [] };

  const { openOffset, closeOffset } = brackets;
  const inner = graphContent.slice(openOffset + 1, closeOffset);
  const trimmed = inner.trim();

  let newContent: string;
  if (trimmed === '') {
    newContent = qname;
  } else {
    const hasTrailingComma = inner.trimEnd().endsWith(',');
    newContent = hasTrailingComma ? `${qname}` : `, ${qname}`;
  }

  const objectEdit = buildTextEdit(
    graphUri,
    offsetToPosition(graphContent, closeOffset),
    offsetToPosition(graphContent, closeOffset),
    newContent,
    null,
  );

  const edits: TextDocumentEdit[] = [objectEdit];

  if (packageToImport !== null && !hasImportForPackage(graphContent, packageToImport)) {
    const insertOffset = findLastImportLineOffset(graphContent);
    if (insertOffset >= 0) {
      const importEdit = buildTextEdit(
        graphUri,
        offsetToPosition(graphContent, insertOffset),
        offsetToPosition(graphContent, insertOffset),
        `import ${packageToImport}\n`,
        null,
      );
      edits.unshift(importEdit);
    } else {
      const importEdit = buildTextEdit(
        graphUri,
        offsetToPosition(graphContent, 0),
        offsetToPosition(graphContent, 0),
        `import ${packageToImport}\n\n`,
        null,
      );
      edits.unshift(importEdit);
    }
  }

  return { documentChanges: edits };
}

function buildRemoveObjectText(inner: string, qname: string): string | null {
  const trimmed = inner.trim();
  if (trimmed === '') return null;

  const qnameIndex = trimmed.indexOf(qname);
  if (qnameIndex === -1) return null;

  const beforeIdx = (() => {
    for (let i = qnameIndex - 1; i >= 0; i--) {
      if (trimmed[i] !== ' ') return i + 1;
    }
    return 0;
  })();
  const afterIdx = (() => {
    const end = qnameIndex + qname.length;
    for (let i = end; i < trimmed.length; i++) {
      if (trimmed[i] !== ' ') return i;
    }
    return trimmed.length;
  })();

  const validBefore = beforeIdx === 0 || trimmed[beforeIdx - 1] === ',';
  const validAfter = afterIdx === trimmed.length || trimmed[afterIdx] === ',';
  if (!validBefore || !validAfter) return null;

  const before = beforeIdx > 0 ? trimmed.slice(0, beforeIdx - 1).replace(/,?\s*$/, '').trimEnd() : '';
  const after = trimmed.slice(afterIdx).replace(/^,?\s*/, '').trimStart();

  if (after.startsWith(qname)) return null;

  if (before && after) return `${before}, ${after}`;
  if (before) return before;
  if (after) return after;
  return '';
}

export function buildRemoveObjectEdit(
  graphContent: string,
  graphUri: string,
  qname: string,
  pruneUnusedImport: boolean,
): WorkspaceEdit {
  const brackets = findObjectsBrackets(graphContent);
  if (!brackets) return { documentChanges: [] };

  const { openOffset, closeOffset } = brackets;
  const inner = graphContent.slice(openOffset + 1, closeOffset);

  const newInner = buildRemoveObjectText(inner, qname);
  if (newInner === null) return { documentChanges: [] };

  const objectEdit = buildTextEdit(
    graphUri,
    offsetToPosition(graphContent, openOffset + 1),
    offsetToPosition(graphContent, closeOffset),
    newInner,
    null,
  );

  const edits: TextDocumentEdit[] = [objectEdit];

  if (pruneUnusedImport) {
    const dotIdx = qname.indexOf('.');
    const packageName = dotIdx === -1 ? null : qname.slice(0, dotIdx);
    if (packageName) {
      const otherInner = buildRemoveObjectText(inner, qname);
      const stillHasPackage = otherInner !== null && otherInner.includes(packageName + '.');
      if (!stillHasPackage) {
        const importRange = findImportRange(graphContent, packageName);
        if (importRange) {
          const importEdit = buildTextEdit(
            graphUri,
            offsetToPosition(graphContent, importRange.start),
            offsetToPosition(graphContent, importRange.end),
            '',
            null,
          );
          edits.push(importEdit);
        }
      }
    }
  }

  return { documentChanges: edits };
}

export function buildCreateGraphContent(params: CreateGraphParams): string {
  const { name, schema, packages, objects, description, tags } = params;

  const lines: string[] = [];

  if (packages.length > 0) {
    for (const pkg of packages) lines.push(`import ${pkg}`);
    lines.push('');
  }

  const graphProps: string[] = [`model: ${schema}`]; // v4.0 — graph model property
  if (description) graphProps.push(`description: "${description}"`);
  if (tags && tags.length > 0) graphProps.push(`tags: [${tags.map(t => `"${t}"`).join(', ')}]`);

  const objectsStr = objects.length > 0 ? `objects: [${objects.join(', ')}]` : 'objects: []';

  lines.push(`graph ${name} {`);
  for (const prop of graphProps) lines.push(`    ${prop}`);
  lines.push(`    ${objectsStr}`);
  lines.push('}');

  return lines.join('\n');
}

export function buildCreateGraphEdit(params: CreateGraphParams): WorkspaceEdit {
  const content = buildCreateGraphContent(params);
  const createFile: CreateFile = { kind: 'create', uri: params.uri };
  const textDocEdit: TextDocumentEdit = {
    textDocument: { uri: params.uri, version: null },
    edits: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } }, newText: content }],
  };

  return {
    documentChanges: [createFile, textDocEdit],
  };
}

export interface SetLayoutParams {
  layout: {
    viewport?: { zoom: number; panX: number; panY: number; displayMode: string };
    nodes: Record<string, { x: number; y: number }>;
    edges: Record<string, { bendPoints?: [number, number][] }>;
  };
}

export function serializeLayoutBlock(layout: SetLayoutParams['layout']): string {
  const parts: string[] = ['layout: {'];

  if (layout.viewport) {
    parts.push(`    viewport: { zoom: ${layout.viewport.zoom}, panX: ${layout.viewport.panX}, panY: ${layout.viewport.panY}, displayMode: "${layout.viewport.displayMode}" }`);
  }

  const nodeEntries = Object.entries(layout.nodes ?? {});
  if (nodeEntries.length > 0) {
    const nodeLines = nodeEntries.map(([k, v]) => `        ${k}: { x: ${v.x}, y: ${v.y} }`);
    parts.push('    nodes: {');
    parts.push(...nodeLines);
    parts.push('    }');
  } else {
    parts.push('    nodes: {}');
  }

  parts.push('}');
  return parts.join('\n');
}

function findExistingLayoutBlock(content: string): { start: number; end: number } | null {
  const layoutIdx = content.indexOf('layout:');
  if (layoutIdx === -1) return null;

  const braceIdx = content.indexOf('{', layoutIdx);
  if (braceIdx === -1) return null;

  let depth = 0;
  let end = -1;
  for (let i = braceIdx; i < content.length; i++) {
    if (content[i] === '{') depth++;
    else if (content[i] === '}') {
      depth--;
      if (depth === 0) { end = i; break; }
    }
  }
  if (end === -1) return null;
  return { start: layoutIdx, end: end + 1 };
}

function findGraphClosingBrace(content: string): number {
  const graphMatch = /graph\s+\w+\s*\{/.exec(content);
  if (!graphMatch) return -1;
  const start = graphMatch.index + graphMatch[0].length - 1;
  let depth = 0;
  for (let i = start; i < content.length; i++) {
    if (content[i] === '{') depth++;
    else if (content[i] === '}') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

export function buildSetLayoutEdit(
  graphContent: string,
  graphUri: string,
  layout: SetLayoutParams['layout'],
): WorkspaceEdit {
  const existingLayout = findExistingLayoutBlock(graphContent);
  const newBlock = serializeLayoutBlock(layout);

  if (!existingLayout) {
    const closingBrace = findGraphClosingBrace(graphContent);
    if (closingBrace === -1) return { documentChanges: [] };
    return {
      documentChanges: [{
        textDocument: { uri: graphUri, version: null },
        edits: [{
          range: { start: offsetToPosition(graphContent, closingBrace), end: offsetToPosition(graphContent, closingBrace + 1) },
          newText: `,\n    ${newBlock}\n  }`,
        }],
      }],
    };
  }

  const existingRange = {
    start: offsetToPosition(graphContent, existingLayout.start),
    end: offsetToPosition(graphContent, existingLayout.end),
  };

  return {
    documentChanges: [{
      textDocument: { uri: graphUri, version: null },
      edits: [{ range: existingRange, newText: newBlock }],
    }],
  };
}