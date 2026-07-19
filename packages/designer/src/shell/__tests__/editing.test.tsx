// SPDX-License-Identifier: Apache-2.0
// DM-P3.S5 — the edit-PRESENT complement to viewer-mode.test. With a full `ShellEditContext` injected
// (what the FO-P0.S4 loader supplies on an `authoring` grant, from @tatrman/designer-authoring), the
// shell MOUNTS the extension's edit slots: the subject-toolbar actions + the missing-objects
// affordance. Proves the OPEN shell consumes a supplied context end-to-end. (The node-menu + drawer
// save slots are unit-covered — @tatrman/designer-authoring slots.test + drawer-edit.test — and wired
// at ShellFrame.tsx: renderNodeMenu={…renderNodeMenu} / onSaveEdit={…saveNode}.)

import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { ShellEditContext } from '../edit-context.js';
import type { GetGraphResponse } from '@tatrman/lsp';
import { fakeDataSource } from './fake-data-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'er_sales', qname: 'er_sales', kind: 'schema', schemaCode: 'er', label: 'er · sales' }] },
];

const erGraph: GetGraphResponse = {
  schema: 'er', edges: [], layout: { nodes: {}, edges: {} }, missingObjects: ['er.ghost'], imports: [],
  nodes: [{ qname: 'er.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] }],
};

/** A stand-in for what @tatrman/designer-authoring's createAuthoringContext returns — the shell only
 *  sees generic verbs + rendered slots (marker-free), so a fake of the same shape proves the mount. */
function fakeEditContext(over: Partial<ShellEditContext> = {}): ShellEditContext {
  return {
    editable: true,
    removeNode: vi.fn().mockResolvedValue(true),
    saveNode: vi.fn().mockResolvedValue({ ok: true }),
    renderToolbarActions: () => <button data-testid="ext-add">add object</button>,
    renderNodeMenu: () => <button data-testid="ext-remove">remove</button>,
    renderMissingObjects: () => <button data-testid="ext-missing">resolve stale</button>,
    ...over,
  };
}

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('DM-P3 — the shell mounts a supplied ShellEditContext (edit present)', () => {
  it('the subject toolbar mounts the extension edit actions + the missing-objects slot', async () => {
    render(<ShellFrame dataSource={fakeDataSource({ getGraph: erGraph })} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" editContext={fakeEditContext()} />);
    fireEvent.click(await screen.findByText('er · sales'));
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());

    // the extension's contributed toolbar action + missing-objects affordance mount (they were ABSENT
    // in viewer-mode.test with no context).
    expect(screen.getByTestId('ext-add')).toBeInTheDocument();
    expect(screen.getByTestId('ext-missing')).toBeInTheDocument();
    // the read-only truth surface still shows alongside.
    expect(screen.getByTestId('stale-count')).toHaveTextContent('1 stale');
  });

  it('with editable:false the shell treats the context as read-only (no edit slots mount)', async () => {
    render(<ShellFrame dataSource={fakeDataSource({ getGraph: erGraph })} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" editContext={fakeEditContext({ editable: false })} />);
    fireEvent.click(await screen.findByText('er · sales'));
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.queryByTestId('ext-add')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ext-missing')).not.toBeInTheDocument();
    // the stale count (read-only) is still shown — edit is gated, truth is not.
    expect(screen.getByTestId('stale-count')).toBeInTheDocument();
  });
});
