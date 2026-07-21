// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 W3.S2 (task 3S2.1) — the TextDrawer edit round-trip. A clean save closes the editor
// (the host refreshes the canvas); a rejected save (parse/validate error) keeps the editor OPEN
// and renders the LSP diagnostic VERBATIM, never a paraphrase; cancel emits nothing; Escape
// cancels the edit.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { TextDrawer, type DrawerNode } from '../TextDrawer.js';

afterEach(() => cleanup());

const node: DrawerNode = {
  qname: 'sales.er.entity.Customer', kind: 'entity', label: 'Customer',
  sourceText: 'def entity Customer { attributes: [ ] }',
};

function openEditor(onSaveEdit: (n: DrawerNode, t: string) => void | Promise<{ ok: boolean; error?: string }>) {
  render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} editEnabled onSaveEdit={onSaveEdit} />);
  fireEvent.click(screen.getByTestId('peek-escalate'));
  fireEvent.click(screen.getByTestId('drawer-edit'));
  return screen.getByTestId('drawer-editor') as HTMLTextAreaElement;
}

describe('TextDrawer edit round-trip (W3.S2)', () => {
  it('a rejected save keeps the editor open and shows the diagnostic VERBATIM', async () => {
    const verbatim = "TTRP-PARSE-014: expected '}' at 3:12";
    const onSaveEdit = vi.fn().mockResolvedValue({ ok: false, error: verbatim });
    const editor = openEditor(onSaveEdit);
    fireEvent.change(editor, { target: { value: 'def entity Customer { attributes: [ ' } }); // broken
    fireEvent.click(screen.getByTestId('drawer-save'));
    await waitFor(() => expect(screen.getByTestId('drawer-save-error')).toBeInTheDocument());
    // verbatim — the exact diagnostic text, not a paraphrase
    expect(screen.getByTestId('drawer-save-error')).toHaveTextContent(verbatim);
    // editor still open (drawer did not close)
    expect(screen.getByTestId('drawer-editor')).toBeInTheDocument();
  });

  it('a clean save closes the editor (canvas refresh is host-side)', async () => {
    const onSaveEdit = vi.fn().mockResolvedValue({ ok: true });
    const editor = openEditor(onSaveEdit);
    fireEvent.change(editor, { target: { value: 'def entity Customer { attributes: [ def attribute id { type: int } ] }' } });
    fireEvent.click(screen.getByTestId('drawer-save'));
    await waitFor(() => expect(screen.queryByTestId('drawer-editor')).toBeNull());
    expect(screen.queryByTestId('drawer-save-error')).toBeNull();
    expect(onSaveEdit).toHaveBeenCalledTimes(1);
  });

  it('Cancel emits nothing and leaves the peek', () => {
    const onSaveEdit = vi.fn();
    const editor = openEditor(onSaveEdit);
    fireEvent.change(editor, { target: { value: 'whatever' } });
    fireEvent.click(screen.getByTestId('drawer-cancel'));
    expect(onSaveEdit).not.toHaveBeenCalled();
    expect(screen.queryByTestId('drawer-editor')).toBeNull();
  });

  it('Escape while editing cancels the edit (no emission)', () => {
    const onSaveEdit = vi.fn();
    const editor = openEditor(onSaveEdit);
    fireEvent.change(editor, { target: { value: 'whatever' } });
    fireEvent.keyDown(screen.getByTestId('text-drawer'), { key: 'Escape' });
    expect(onSaveEdit).not.toHaveBeenCalled();
    expect(screen.queryByTestId('drawer-editor')).toBeNull();
  });
});

describe('TextDrawer — a11y (W3.S2)', () => {
  it('the close control has an accessible name; Escape closes the drawer when not editing', () => {
    const onClose = vi.fn();
    render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={onClose} />);
    expect(screen.getByRole('button', { name: /close details/i })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByTestId('text-drawer'), { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
