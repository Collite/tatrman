import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import {
  getModelCodeCompletions,
  getSchemaHandleCompletions,
  detectCompletionContext,
} from '../completion-property.js';

// Pure-function coverage for the v4.0 directive completions (qname-redesign):
// `model <code>` offers model codes (no retired `query`), `schema <handle>` offers
// the manifest's named schema handles.

const doc = parseString('model db schema dbo\n', 'file:///t.ttrm').ast!;

describe('detectCompletionContext (v4.0 directives)', () => {
  const at = (line: string, character: number) =>
    detectCompletionContext({ position: { line: 0, character }, content: line, doc });

  it('classifies `model ` as modelCode', () => {
    expect(at('model ', 6)).toBe('modelCode');
  });
  it('classifies `schema ` as schemaHandle', () => {
    expect(at('schema ', 7)).toBe('schemaHandle');
  });
  it('classifies `def ` as defKind (def project never trips modelCode)', () => {
    expect(at('def ', 4)).toBe('defKind');
  });
});

describe('getModelCodeCompletions', () => {
  it('offers the five v4.0 model codes and never the retired query (D14)', () => {
    const res = getModelCodeCompletions({ position: { line: 0, character: 6 }, content: 'model ', doc });
    const labels = (res?.items ?? []).map((i) => i.label).sort();
    expect(labels).toEqual(['binding', 'cnc', 'db', 'er', 'md']);
  });
  it('does not fire when the line is not a model directive', () => {
    expect(getModelCodeCompletions({ position: { line: 0, character: 4 }, content: 'def ', doc })).toBeNull();
  });
});

describe('getSchemaHandleCompletions', () => {
  it('offers the manifest schema handles after `schema `', () => {
    const res = getSchemaHandleCompletions({
      position: { line: 0, character: 7 },
      content: 'schema ',
      doc,
      schemaHandles: ['dbo', 'sales', 'core'],
    });
    const labels = (res?.items ?? []).map((i) => i.label).sort();
    expect(labels).toEqual(['core', 'dbo', 'sales']);
    expect((res?.items ?? [])[0].detail).toBe('schema handle');
  });
  it('returns an empty list when the manifest declares no handles', () => {
    const res = getSchemaHandleCompletions({
      position: { line: 0, character: 7 },
      content: 'schema ',
      doc,
      schemaHandles: [],
    });
    expect(res?.items).toEqual([]);
  });
});
