// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import type { WorkspaceEdit, TextDocumentEdit } from 'vscode-languageserver-types';
import { buildAddObjectEdit, buildRemoveObjectEdit, buildCreateGraphContent, buildSetLayoutEdit } from '../graph-edits.js';

function applyTextEdit(content: string, edit: TextDocumentEdit): string {
  const lines = content.split('\n');
  const startLine = edit.edits[0].range.start.line;
  const startChar = edit.edits[0].range.start.character;
  const endLine = edit.edits[0].range.end.line;
  const endChar = edit.edits[0].range.end.character;

  if (startLine === endLine) {
    const line = lines[startLine];
    return lines.slice(0, startLine).join('\n') +
      '\n' + line.slice(0, startChar) + edit.edits[0].newText + line.slice(endChar) +
      '\n' + lines.slice(endLine + 1).join('\n');
  }

  const before = lines.slice(0, startLine).join('\n');
  const middle = edit.edits[0].newText;
  const after = lines.slice(endLine + 1).join('\n');
  return before + '\n' + middle + '\n' + after;
}

function isBalancedBraces(text: string): boolean {
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (escape) { escape = false; continue; }
    if (c === '\\' && inString) { escape = true; continue; }
    if (c === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (c === '{') depth++;
    else if (c === '}') { depth--; if (depth < 0) return false; }
  }
  return depth === 0;
}

function isValidGraphBlock(text: string): boolean {
  return isBalancedBraces(text) && text.includes('graph ') && text.includes('model: ') && text.includes('objects: [');
}

function getEdit(result: WorkspaceEdit): TextDocumentEdit {
  return result.documentChanges![0] as TextDocumentEdit;
}

describe('graph-edits', () => {
  describe('buildAddObjectEdit', () => {
    it('adds object to empty list', () => {
      const content = 'graph test { model: er, objects: [] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'er.entity.foo', null);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      expect(edit.edits[0].newText).toBe('er.entity.foo');
    });

    it('adds object to single-element list', () => {
      const content = 'graph test { model: er, objects: [er.entity.a] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'er.entity.b', null);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      expect(edit.edits[0].newText).toBe(', er.entity.b');
    });

    it('adds object to multi-element list', () => {
      const content = 'graph test { model: er, objects: [er.entity.a, er.entity.b] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'er.entity.c', null);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      expect(edit.edits[0].newText).toBe(', er.entity.c');
    });

    it('adds object with trailing comma', () => {
      const content = 'graph test { model: er, objects: [er.entity.a,] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'er.entity.b', null);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      expect(edit.edits[0].newText).toBe('er.entity.b');
    });

    it('adds import when packageToImport is set and package not imported', () => {
      const content = 'graph test { model: er, objects: [] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'billing.entity.foo', 'billing');
      expect(result.documentChanges).toHaveLength(2);
      const changes = result.documentChanges as TextDocumentEdit[];
      expect(changes[0].edits[0].newText).toBe('import billing\n');
      expect(changes[1].edits[0].newText).toBe('billing.entity.foo');
    });

    it('does not duplicate import when packageToImport is set and package already imported', () => {
      const content = 'import billing\n\ngraph test { model: er, objects: [] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'billing.entity.foo', 'billing');
      expect(result.documentChanges).toHaveLength(1);
    });

    it('adds no import when packageToImport is null', () => {
      const content = 'graph test { model: er, objects: [] }';
      const result = buildAddObjectEdit(content, 'file:///test.ttrg', 'er.entity.foo', null);
      expect(result.documentChanges).toHaveLength(1);
    });
  });

  describe('buildRemoveObjectEdit', () => {
    it('removes sole element', () => {
      const content = 'graph test { model: er, objects: [er.entity.a] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.a', false);
      expect(result.documentChanges).toHaveLength(1);
      const edit = (result.documentChanges as TextDocumentEdit[])[0];
      expect(edit.edits[0].newText).toBe('');
    });

    it('removes first element', () => {
      const content = 'graph test { model: er, objects: [er.entity.a, er.entity.b] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.a', false);
      expect(result.documentChanges).toHaveLength(1);
      const edit = (result.documentChanges as TextDocumentEdit[])[0];
      expect(edit.edits[0].newText).toBe('er.entity.b');
    });

    it('removes middle element', () => {
      const content = 'graph test { model: er, objects: [er.entity.a, er.entity.b, er.entity.c] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.b', false);
      expect(result.documentChanges).toHaveLength(1);
      const edit = (result.documentChanges as TextDocumentEdit[])[0];
      expect(edit.edits[0].newText).toBe('er.entity.a, er.entity.c');
    });

    it('removes last element', () => {
      const content = 'graph test { model: er, objects: [er.entity.a, er.entity.b] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.b', false);
      expect(result.documentChanges).toHaveLength(1);
      const edit = (result.documentChanges as TextDocumentEdit[])[0];
      expect(edit.edits[0].newText).toBe('er.entity.a');
    });

    it('returns empty when qname not found', () => {
      const content = 'graph test { model: er, objects: [er.entity.a] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.nonexistent', false);
      expect(result.documentChanges).toHaveLength(0);
    });

    it('does not match near-prefix sibling - token boundary required', () => {
      const content = 'graph test { model: er, objects: [er.entity.a, er.entity.ab] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'er.entity.a', false);
      expect(result.documentChanges).toHaveLength(0);
    });

    it('removes import when pruneUnusedImport is true and no other object uses it', () => {
      const content = 'import billing\n\ngraph test { model: er, objects: [billing.entity.a] }';
      const result = buildRemoveObjectEdit(content, 'file:///test.ttrg', 'billing.entity.a', true);
      expect(result.documentChanges).toHaveLength(2);
    });
  });

  describe('buildCreateGraphContent', () => {
    it('creates canonical graph body', () => {
      const params = {
        uri: 'file:///test.ttrg',
        name: 'test_graph',
        schema: 'er' as const,
        packages: [],
        objects: [],
      };
      const content = buildCreateGraphContent(params);
      expect(content).toBe(`graph test_graph {
    model: er
    objects: []
}`);
    });

    it('includes description and tags', () => {
      const params = {
        uri: 'file:///test.ttrg',
        name: 'test_graph',
        schema: 'er' as const,
        packages: [],
        objects: [],
        description: 'A test graph',
        tags: ['foo', 'bar'],
      };
      const content = buildCreateGraphContent(params);
      expect(content).toContain('description: "A test graph"');
      expect(content).toContain('tags: ["foo", "bar"]');
    });

    it('includes imports', () => {
      const params = {
        uri: 'file:///test.ttrg',
        name: 'test_graph',
        schema: 'er' as const,
        packages: ['billing', 'db'],
        objects: [],
      };
      const content = buildCreateGraphContent(params);
      expect(content).toContain('import billing');
      expect(content).toContain('import db');
    });

    it('includes objects', () => {
      const params = {
        uri: 'file:///test.ttrg',
        name: 'test_graph',
        schema: 'er' as const,
        packages: [],
        objects: ['er.entity.a', 'er.entity.b'],
      };
      const content = buildCreateGraphContent(params);
      expect(content).toContain('objects: [er.entity.a, er.entity.b]');
    });
  });

  describe('buildSetLayoutEdit', () => {
    it('inserts layout into graph with no existing layout block', () => {
      const content = 'graph test { model: er, objects: [er.entity.a] }';
      const layout = {
        nodes: { 'er.entity.a': { x: 100, y: 200 } },
        edges: {},
      };
      const result = buildSetLayoutEdit(content, 'file:///test.ttrg', layout);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      const newText = applyTextEdit(content, edit);

      expect(isValidGraphBlock(newText), `Generated invalid TTR: ${newText}`).toBe(true);
      expect(newText).toContain('er.entity.a:');
      expect(newText).toContain('x: 100');
      expect(newText).toContain('y: 200');
    });

    it('replaces existing layout block without doubling layout keyword', () => {
      const content = 'graph test { model: er, objects: [], layout: { nodes: { er.entity.a: { x: 50, y: 60 } } } }';
      const layout = {
        nodes: { 'er.entity.a': { x: 99, y: 88 } },
        edges: {},
      };
      const result = buildSetLayoutEdit(content, 'file:///test.ttrg', layout);
      expect(result.documentChanges).toHaveLength(1);
      const edit = getEdit(result);
      const newText = applyTextEdit(content, edit);

      expect(isValidGraphBlock(newText), `Generated invalid TTR: ${newText}`).toBe(true);
      expect(newText).toContain('er.entity.a:');
      expect(newText).not.toContain('layout: { layout:');
    });

    it('uses unquoted dotted ids for node keys', () => {
      const content = 'graph test { model: er, objects: [] }';
      const layout = {
        nodes: { 'er.entity.foo': { x: 10, y: 20 } },
        edges: {},
      };
      const result = buildSetLayoutEdit(content, 'file:///test.ttrg', layout);
      const edit = getEdit(result);
      expect(edit.edits[0].newText).toContain('er.entity.foo:');
      expect(edit.edits[0].newText).not.toContain('"er.entity.foo"');
    });
  });
});