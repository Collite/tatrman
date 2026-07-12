// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, vi } from 'vitest';
import { applyWorkspaceEdit } from '../apply-workspace-edit';

/** A getText/openDoc pair backed by a single store — models the App's doc cache. */
function makeStore(initial: Record<string, string> = {}) {
  const store = new Map<string, string>(Object.entries(initial));
  const getText = (uri: string) => store.get(uri);
  const openDoc = vi.fn(async (uri: string, content: string) => { store.set(uri, content); });
  return { store, getText, openDoc };
}

/** Builds an edit that inserts `text` at a single (line, character) point. */
function insertAt(uri: string, line: number, character: number, text: string) {
  return {
    documentChanges: [
      {
        textDocument: { uri, version: null },
        edits: [{ range: { start: { line, character }, end: { line, character } }, newText: text }],
      },
    ],
  };
}

describe('applyWorkspaceEdit', () => {
  it('applies a single TextEdit to a document', async () => {
    const { store, getText, openDoc } = makeStore({ 'file:///x.ttrg': 'hello world' });
    const edit = {
      documentChanges: [
        {
          textDocument: { uri: 'file:///x.ttrg', version: null },
          edits: [{ range: { start: { line: 0, character: 6 }, end: { line: 0, character: 11 } }, newText: 'plate' }],
        },
      ],
    };
    await applyWorkspaceEdit(edit, getText, openDoc);
    expect(openDoc).toHaveBeenCalledWith('file:///x.ttrg', 'hello plate');
    expect(store.get('file:///x.ttrg')).toBe('hello plate');
  });

  it('applies two TextEdits in reverse order so offsets stay valid', async () => {
    const content = 'package foo\n\nimport bar\n\ngraph g {\n    objects []\n}';
    const { getText, openDoc } = makeStore({ 'file:///x.ttrg': content });
    const edit = {
      documentChanges: [
        {
          textDocument: { uri: 'file:///x.ttrg', version: null },
          edits: [
            { range: { start: { line: 3, character: 0 }, end: { line: 3, character: 0 } }, newText: 'EARLY\n' },
            { range: { start: { line: 1, character: 0 }, end: { line: 1, character: 0 } }, newText: 'import billing.invoicing\n' },
          ],
        },
      ],
    };
    await applyWorkspaceEdit(edit, getText, openDoc);
    expect(openDoc).toHaveBeenCalledOnce();
    const patched = openDoc.mock.calls[0]![1];
    // Both insertions land correctly: the later edit didn't shift the earlier one.
    expect(patched).toContain('import billing.invoicing');
    expect(patched).toContain('EARLY');
    expect(patched.indexOf('import billing.invoicing')).toBeLessThan(patched.indexOf('EARLY'));
  });

  it('returns the list of affected uris', async () => {
    const { getText, openDoc } = makeStore({ 'file:///a.ttrg': 'hello', 'file:///b.ttrg': 'hello' });
    const edit = {
      documentChanges: [
        {
          textDocument: { uri: 'file:///a.ttrg', version: null },
          edits: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 5 } }, newText: 'hi' }],
        },
        {
          textDocument: { uri: 'file:///b.ttrg', version: null },
          edits: [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 5 } }, newText: 'yo' }],
        },
      ],
    };
    const result = await applyWorkspaceEdit(edit, getText, openDoc);
    expect(result).toContain('file:///a.ttrg');
    expect(result).toContain('file:///b.ttrg');
  });

  it('returns empty array when there are no documentChanges', async () => {
    const { openDoc } = makeStore();
    const result = await applyWorkspaceEdit({ documentChanges: [] }, () => '', openDoc);
    expect(result).toEqual([]);
    expect(openDoc).not.toHaveBeenCalled();
  });

  // Regression guard for the review-044 "stale cache" bug: the patched text must
  // be written back through `openDoc` so a *second* edit — whose ranges are
  // computed against the already-updated document — composes instead of
  // corrupting. If the helper ever bypasses `openDoc`, the store stays stale and
  // the second insertion lands at the wrong place / clobbers the first.
  it('writes through openDoc so consecutive edits compose on the updated text', async () => {
    const uri = 'file:///x.ttrg';
    const v0 = 'graph g {\n  objects {\n  }\n}';
    const { store, getText, openDoc } = makeStore({ [uri]: v0 });

    // Op 1: insert "A" on its own line just before the objects closing brace.
    // In v0 that brace is on line 2, character 2.
    await applyWorkspaceEdit(insertAt(uri, 2, 2, 'A\n  '), getText, openDoc);
    const v1 = store.get(uri)!;
    expect(v1).toContain('A');
    expect(openDoc).toHaveBeenCalledTimes(1);

    // Op 2: insert "B" before the closing brace, whose position has now MOVED
    // because "A" was inserted. We compute the range against the updated text
    // (as the server would), so this only stays correct if op 1 was written back.
    const closeLine = v1.split('\n').findIndex((l) => l.trim() === '}' && l.startsWith('  }'));
    await applyWorkspaceEdit(insertAt(uri, closeLine, 2, 'B\n  '), getText, openDoc);
    const v2 = store.get(uri)!;

    expect(v2).toContain('A');
    expect(v2).toContain('B');
    // Well-formed: still exactly two closing braces, objects block intact.
    expect((v2.match(/}/g) ?? []).length).toBe(2);
  });
});
