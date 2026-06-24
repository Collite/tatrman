import { describe, it, expect } from 'vitest';
import type { WorkspaceEdit, TextDocumentEdit } from 'vscode-languageserver-types';
import { buildRenameSymbolEdit, type RenameSymbolEditParams } from '../rename-symbol.js';

function extractEdits(result: WorkspaceEdit): TextDocumentEdit[] {
  return (result.documentChanges ?? []) as TextDocumentEdit[];
}

function applyEdits(content: string, edits: TextDocumentEdit[]): string {
  let result = content;
  const sorted = [...edits].sort((a, b) => {
    const aUri = a.textDocument.uri;
    const bUri = b.textDocument.uri;
    if (aUri !== bUri) return aUri.localeCompare(bUri);
    const aStart = a.edits[0].range.start;
    const bStart = b.edits[0].range.start;
    if (aStart.line !== bStart.line) return bStart.line - aStart.line;
    return bStart.character - aStart.character;
  });
  for (const edit of sorted) {
    result = applyEdit(result, edit);
  }
  return result;
}

function applyEdit(content: string, edit: TextDocumentEdit): string {
  const { range, newText } = edit.edits[0];
  const lines = content.split('\n');
  const { start, end } = range;
  if (start.line === end.line) {
    const line = lines[start.line];
    return [
      ...lines.slice(0, start.line),
      line.slice(0, start.character) + newText + line.slice(end.character),
      ...lines.slice(end.line + 1),
    ].join('\n');
  }
  const firstLine = lines[start.line];
  const lastLine = lines[end.line];
  const newFirst = firstLine.slice(0, start.character) + newText + lastLine.slice(end.character);
  return [
    ...lines.slice(0, start.line),
    newFirst,
    ...lines.slice(end.line + 1),
  ].join('\n');
}

describe('buildRenameSymbolEdit', () => {
  it('produces edits for def site and all references', () => {
    const defUri = 'file:///proj/billing/invoicing/artikl.ttr';
    const refUri = 'file:///proj/billing/invoicing/faktura.ttr';
    const ttrgUri = 'file:///proj/billing/invoicing/overview.ttrg';

    const defContent = `package billing.invoicing

schema er namespace entity

def entity artikl {
  nameAttribute: er.entity.artikl
}`;
    const refContent = `package billing.invoicing

schema er namespace entity

def entity faktura {
  to: er.entity.artikl
}`;
    const ttrgContent = `package billing.invoicing

import billing.invoicing.*

graph overview {
    schema: er,
    objects: [
        billing.invoicing.er.entity.artikl
    ]
}`;

    const defSource = { file: 'file:///proj/billing/invoicing/artikl.ttr', line: 5, column: 13, endLine: 5, endColumn: 19, offsetStart: 0, offsetEnd: 0 };
    const refSource = { file: 'file:///proj/billing/invoicing/faktura.ttr', line: 6, column: 19, endLine: 6, endColumn: 32, offsetStart: 0, offsetEnd: 0 };
    const refs = [
      { documentUri: refUri, source: refSource, targetQname: 'billing.invoicing.er.entity.artikl' },
    ];
    const ttrgDocs = new Map([[ttrgUri, ttrgContent]]);
    const docs = new Map([[defUri, defContent], [refUri, refContent]]);
    const allDocs = new Map([...docs, ...ttrgDocs]);

    const params: RenameSymbolEditParams = {
      oldQname: 'billing.invoicing.er.entity.artikl',
      newBareName: 'artikl_v2',
      defEntry: {
        qname: 'billing.invoicing.er.entity.artikl',
        kind: 'entity',
        name: 'artikl',
        source: defSource,
        documentUri: defUri,
        packageName: 'billing.invoicing',
        schemaCode: 'er',
      },
      defDocumentContent: defContent,
      references: refs,
      ttrgDocuments: allDocs,
    };

    const result = buildRenameSymbolEdit(params);
    const edits = extractEdits(result);

    const defEdits = edits.filter(e => e.textDocument.uri === defUri);
    expect(defEdits.length, 'def site edit').toBe(1);
    const refEdits = edits.filter(e => e.textDocument.uri === refUri);
    expect(refEdits.length, 'ref site edit').toBe(1);
    const ttrgEdits = edits.filter(e => e.textDocument.uri === ttrgUri);
    expect(ttrgEdits.length, 'ttrg objects edit').toBe(1);

    const newDefContent = applyEdits(defContent, defEdits);
    expect(newDefContent).toContain('artikl_v2');

    const newRefContent = applyEdits(refContent, refEdits);
    expect(newRefContent).toContain('artikl_v2');
  });

  it('rename from reference site resolves to def and produces same result', () => {
    const defUri = 'file:///proj/billing/invoicing/artikl.ttr';
    const refUri = 'file:///proj/billing/invoicing/faktura.ttr';

    const defContent = `package billing.invoicing

schema er namespace entity

def entity artikl {
}`;
    const refContent = `package billing.invoicing

schema er namespace entity

def entity faktura {
  to: er.entity.artikl
}`;

    const refSource = { file: 'file:///proj/billing/invoicing/faktura.ttr', line: 6, column: 19, endLine: 6, endColumn: 32, offsetStart: 0, offsetEnd: 0 };
    const defSource = { file: 'file:///proj/billing/invoicing/artikl.ttr', line: 5, column: 13, endLine: 5, endColumn: 19, offsetStart: 0, offsetEnd: 0 };
    const docs = new Map([[defUri, defContent], [refUri, refContent]]);

    const params: RenameSymbolEditParams = {
      oldQname: 'billing.invoicing.er.entity.artikl',
      newBareName: 'artikl_v2',
      defEntry: {
        qname: 'billing.invoicing.er.entity.artikl',
        kind: 'entity',
        name: 'artikl',
        source: defSource,
        documentUri: defUri,
        packageName: 'billing.invoicing',
        schemaCode: 'er',
      },
      defDocumentContent: defContent,
      references: [
        { documentUri: refUri, source: refSource, targetQname: 'billing.invoicing.er.entity.artikl' },
      ],
      ttrgDocuments: docs,
    };

    const result = buildRenameSymbolEdit(params);
    const edits = extractEdits(result);

    expect(edits.some(e => e.textDocument.uri === defUri && e.edits[0].newText === 'artikl_v2')).toBe(true);
    expect(edits.some(e => e.textDocument.uri === refUri && e.edits[0].newText.includes('artikl_v2'))).toBe(true);
  });

  it('rename across schema kinds updates both files', () => {
    const entityUri = 'file:///proj/billing/invoicing/artikl.ttr';
    const mapUri = 'file:///proj/billing/invoicing/maps.ttr';

    const entityContent = `package billing.invoicing

schema er namespace entity

def entity artikl {
}`;
    const mapContent = `package billing.invoicing

schema binding namespace layer

def map mymap {
  uses: er.entity.artikl
}`;

    const entitySource = { file: 'file:///proj/billing/invoicing/artikl.ttr', line: 5, column: 13, endLine: 5, endColumn: 19, offsetStart: 0, offsetEnd: 0 };
    const mapSource = { file: 'file:///proj/billing/invoicing/maps.ttr', line: 6, column: 8, endLine: 6, endColumn: 20, offsetStart: 0, offsetEnd: 0 };
    const docs = new Map([[entityUri, entityContent], [mapUri, mapContent]]);

    const params: RenameSymbolEditParams = {
      oldQname: 'billing.invoicing.er.entity.artikl',
      newBareName: 'polozka',
      defEntry: {
        qname: 'billing.invoicing.er.entity.artikl',
        kind: 'entity',
        name: 'artikl',
        source: entitySource,
        documentUri: entityUri,
        packageName: 'billing.invoicing',
        schemaCode: 'er',
      },
      defDocumentContent: entityContent,
      references: [
        { documentUri: mapUri, source: mapSource, targetQname: 'billing.invoicing.er.entity.artikl' },
      ],
      ttrgDocuments: docs,
    };

    const result = buildRenameSymbolEdit(params);
    const edits = extractEdits(result);

    const mapEdits = edits.filter(e => e.textDocument.uri === mapUri);
    expect(mapEdits.length).toBeGreaterThan(0);
    const newMapContent = applyEdits(mapContent, mapEdits);
    expect(newMapContent).toContain('polozka');
  });

  // Skipped: idempotency check depends on file content at original source position
  // after rename is applied. When newName matches newQname (e.g. 'artikl_v2' ===
  // 'test.er.entity.artikl_v2'), the check passes and returns empty edit. But when
  // bare name doesn't match newQname, edit is produced even though it would produce
  // the same result. This is acceptable behavior - rename is functionally idempotent
  // even if second edit isn't empty.
  it('idempotent: applying rename twice produces empty second edit', () => {
    const defUri = 'file:///proj/artikl.ttr';
    const defContent = `package test

schema er namespace entity

def entity artikl {
}`;
    // SourceLocation is 1-indexed line, 0-indexed column. "def entity artikl {"
    // is line 5; "artikl" starts at column 11 and is 6 chars (end-exclusive 17).
    const defSource = { file: defUri, line: 5, column: 11, endLine: 5, endColumn: 17, offsetStart: 0, offsetEnd: 0 };

    const params: RenameSymbolEditParams = {
      oldQname: 'test.er.entity.artikl',
      newBareName: 'artikl_v2',
      defEntry: {
        qname: 'test.er.entity.artikl',
        kind: 'entity',
        name: 'artikl',
        source: defSource,
        documentUri: defUri,
        packageName: 'test',
        schemaCode: 'er',
      },
      defDocumentContent: defContent,
      references: [],
      ttrgDocuments: new Map([[defUri, defContent]]),
    };

    const first = buildRenameSymbolEdit(params);
    const firstEdits = extractEdits(first);
    expect(firstEdits.length).toBe(1);
    const afterFirst = applyEdits(defContent, firstEdits);
    expect(afterFirst).toContain('def entity artikl_v2');

    // Second rename mirrors the real LSP flow: after applying + re-parsing, the
    // symbol is re-indexed, so its def span now covers "artikl_v2" (9 chars).
    const second = buildRenameSymbolEdit({
      oldQname: 'test.er.entity.artikl_v2',
      newBareName: 'artikl_v2',
      defEntry: {
        qname: 'test.er.entity.artikl_v2',
        kind: 'entity',
        name: 'artikl_v2',
        source: { ...defSource, endColumn: 20 },
        documentUri: defUri,
        packageName: 'test',
        schemaCode: 'er',
      },
      defDocumentContent: afterFirst,
      references: [],
      ttrgDocuments: new Map([[defUri, afterFirst]]),
    });
    expect(second.documentChanges).toHaveLength(0);
  });
});