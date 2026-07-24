// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 W3.S2 (task 3S2.3) — ⌘K authoring verbs. When an authoring context contributes
// `commands`, the palette lists them and dispatch reaches the surface (bound to the focused
// subject). WITHOUT a context (open build) the verbs are ABSENT — never named, never disabled —
// so the open bundle stays grep-clean (FO-21).

import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, within } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { ShellEditContext, AuthoringCommand } from '../edit-context.js';
import { fakeDataSource } from './fake-data-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'er.sales', qname: 'er.sales', kind: 'schema', label: 'schema sales', schemaCode: 'er' }] },
];

function ctxWithCommands(run: AuthoringCommand['run']): ShellEditContext {
  return {
    editable: true,
    removeNode: vi.fn().mockResolvedValue(true),
    saveNode: vi.fn().mockResolvedValue({ ok: true }),
    renderToolbarActions: () => null,
    renderNodeMenu: () => null,
    renderMissingObjects: () => null,
    commands: [
      { id: 'add-object', title: 'Add object…', group: 'Authoring', run },
      { id: 'remove-object', title: 'Remove object…', group: 'Authoring', run },
      { id: 'rename-object', title: 'Rename object…', group: 'Authoring', run },
      { id: 'edit-as-text', title: 'Edit as text', group: 'Authoring', run },
    ],
  };
}

function renderShell(editContext?: ShellEditContext) {
  return render(
    <ShellFrame dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" editContext={editContext} />,
  );
}

const openPalette = () => fireEvent.keyDown(window, { key: 'k', metaKey: true });

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('⌘K authoring verbs (W3.S2)', () => {
  it('lists the contributed authoring verbs when an authoring context is present', async () => {
    renderShell(ctxWithCommands(vi.fn()));
    fireEvent.click(await screen.findByText('schema sales'));
    openPalette();
    // the palette re-renders as the tab opens + commands register
    expect(await screen.findByText('Add object…')).toBeInTheDocument();
    const list = screen.getByRole('listbox');
    expect(within(list).getByText('Remove object…')).toBeInTheDocument();
    expect(within(list).getByText('Rename object…')).toBeInTheDocument();
    expect(within(list).getByText('Edit as text')).toBeInTheDocument();
  });

  it('names NO authoring verb in the open build (no editContext) — absent, not disabled', async () => {
    renderShell(undefined);
    fireEvent.click(await screen.findByText('schema sales'));
    openPalette();
    // wait for the palette to stabilise on a subject command (catalog "Open …"), then assert absence
    await screen.findByText(/Open schema sales/i);
    const list = screen.getByRole('listbox');
    expect(within(list).queryByText('Add object…')).toBeNull();
    expect(within(list).queryByText('Remove object…')).toBeNull();
    expect(within(list).queryByText('Edit as text')).toBeNull();
  });

  it('dispatch reaches the authoring surface (run invoked on click)', async () => {
    const run = vi.fn();
    renderShell(ctxWithCommands(run));
    fireEvent.click(await screen.findByText('schema sales'));
    openPalette();
    fireEvent.click(await screen.findByText('Rename object…'));
    await waitFor(() => expect(run).toHaveBeenCalledTimes(1));
  });
});
