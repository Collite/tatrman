import { describe, it, expect } from 'vitest';
import type { WorkspaceEdit, TextDocumentEdit } from 'vscode-languageserver-types';
import { buildRenamePackageEdit, type RenamePackageEditParams } from '../rename-package.js';

function extractEdits(result: WorkspaceEdit): TextDocumentEdit[] {
  return (result.documentChanges ?? []) as TextDocumentEdit[];
}

function applyEdits(content: string, edits: TextDocumentEdit[]): string {
  let result = content;
  const sorted = [...edits].sort((a, b) => {
    if (a.textDocument.uri !== b.textDocument.uri) return a.textDocument.uri.localeCompare(b.textDocument.uri);
    const aStart = a.edits[0].range.start;
    const bStart = b.edits[0].range.start;
    if (aStart.line !== bStart.line) return bStart.line - aStart.line;
    return bStart.character - bStart.character;
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

describe('buildRenamePackageEdit', () => {
  it('updates package declaration in every file in the package', () => {
    const uri1 = 'file:///proj/billing/invoicing/a.ttrm';
    const uri2 = 'file:///proj/billing/invoicing/b.ttrm';
    const docs = new Map([
      [uri1, `package billing.invoicing\n\nmodel er schema entity\n\ndef entity foo {}`],
      [uri2, `package billing.invoicing\n\nmodel er schema entity\n\ndef entity bar {}`],
    ]);

    const params: RenamePackageEditParams = {
      oldPackageName: 'billing.invoicing',
      newPackageName: 'billing.invoicing_v2',
      allDocuments: docs,
    };

    const result = buildRenamePackageEdit(params);
    const edits = extractEdits(result);

    const uri1Edits = edits.filter(e => e.textDocument.uri === uri1);
    expect(uri1Edits.length).toBeGreaterThan(0);
    const uri2Edits = edits.filter(e => e.textDocument.uri === uri2);
    expect(uri2Edits.length).toBeGreaterThan(0);

    const newA = applyEdits(docs.get(uri1)!, uri1Edits);
    expect(newA).toContain('billing.invoicing_v2');
    const newB = applyEdits(docs.get(uri2)!, uri2Edits);
    expect(newB).toContain('billing.invoicing_v2');
  });

  it('updates every import referencing the package', () => {
    const importer = 'file:///proj/billing/client.ttrm';
    const importerContent = `package billing.client

import billing.invoicing.*

model er schema entity

def entity usage {
  ref: er.entity.foo
}`;
    const docs = new Map([[importer, importerContent]]);

    const params: RenamePackageEditParams = {
      oldPackageName: 'billing.invoicing',
      newPackageName: 'billing.invoicing_v2',
      allDocuments: docs,
    };

    const result = buildRenamePackageEdit(params);
    const edits = extractEdits(result);

    const importEdits = edits.filter(e => e.textDocument.uri === importer && e.edits[0].newText.includes('invoicing_v2'));
    expect(importEdits.length).toBeGreaterThan(0);
    const newContent = applyEdits(importerContent, importEdits);
    expect(newContent).toContain('billing.invoicing_v2.*');
    expect(newContent).not.toContain('billing.invoicing.*');
  });

  it('updates named imports and wildcard imports', () => {
    const uri = 'file:///proj/billing/client.ttrm';
    const content = `package billing.client

import billing.invoicing.er.entity.foo
import billing.invoicing.er.entity.bar
import billing.invoicing.*

model er schema entity`;
    const docs = new Map([[uri, content]]);

    const params: RenamePackageEditParams = {
      oldPackageName: 'billing.invoicing',
      newPackageName: 'billing.invoicing_v2',
      allDocuments: docs,
    };

    const result = buildRenamePackageEdit(params);
    const edits = extractEdits(result);

    const uriEdits = edits.filter(e => e.textDocument.uri === uri);
    expect(uriEdits.length).toBeGreaterThanOrEqual(3);
    const newContent = applyEdits(content, uriEdits);
    expect(newContent).toContain('billing.invoicing_v2.er.entity.foo');
    expect(newContent).toContain('billing.invoicing_v2.er.entity.bar');
    expect(newContent).toContain('billing.invoicing_v2.*');
  });

  it('updates .ttrg objects entries with old package prefix', () => {
    const uri = 'file:///proj/billing/overview.ttrg';
    const content = `package billing

import billing.invoicing.*

graph overview {
    model: er,
    objects: [
        billing.invoicing.er.entity.artikl,
        billing.invoicing.er.entity.faktura
    ]
}`;
    const docs = new Map([[uri, content]]);

    const params: RenamePackageEditParams = {
      oldPackageName: 'billing.invoicing',
      newPackageName: 'billing.invoicing_v2',
      allDocuments: docs,
    };

    const result = buildRenamePackageEdit(params);
    const edits = extractEdits(result);

    const ttrgEdits = edits.filter(e => e.textDocument.uri === uri);
    expect(ttrgEdits.length).toBeGreaterThan(0);
    const newContent = applyEdits(content, ttrgEdits);
    expect(newContent).toContain('billing.invoicing_v2.er.entity.artikl');
    expect(newContent).toContain('billing.invoicing_v2.er.entity.faktura');
    expect(newContent).not.toContain('billing.invoicing.er.entity.artikl');
  });

  it('bare-name references in same package do not need rewriting', () => {
    const uri = 'file:///proj/billing/invoicing/use.ttrm';
    const content = `package billing.invoicing

model er schema entity

def entity usage {
  ref: er.entity.foo
}`;
    const docs = new Map([[uri, content]]);

    const params: RenamePackageEditParams = {
      oldPackageName: 'billing.invoicing',
      newPackageName: 'billing.invoicing_v2',
      allDocuments: docs,
    };

    const result = buildRenamePackageEdit(params);
    const edits = extractEdits(result);

    const samePackageEdits = edits.filter(e =>
      e.textDocument.uri === uri &&
      e.edits[0].newText.includes('er.entity.foo')
    );
    expect(samePackageEdits.length, 'bare-name refs in same package should not be rewritten').toBe(0);
  });
});