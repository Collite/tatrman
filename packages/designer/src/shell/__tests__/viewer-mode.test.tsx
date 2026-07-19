// SPDX-License-Identifier: Apache-2.0
// DM-P2.S5 — the FO-21 viewer guard, re-homed onto the RF shell (the old cy-era viewer-mode.spec was
// retired in S4). With NO `editContext` injected (the OPEN Studio Viewer), the shell renders the DS
// canvas but ZERO edit affordances: no toolbar edit actions, no node context menu on right-click, no
// add/remove. Complements `check-viewer-bundle.mjs` (which proves the strings are absent from the
// built bundle) by proving the runtime behavior is read-only.

import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { GetGraphResponse } from '@tatrman/lsp';
import { fakeDataSource } from './fake-data-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'er_sales', qname: 'er_sales', kind: 'schema', schemaCode: 'er', label: 'er · sales' }] },
];

const erGraph: GetGraphResponse = {
  schema: 'er', edges: [], layout: { nodes: {}, edges: {} }, missingObjects: ['er.ghost'], imports: [],
  nodes: [{ qname: 'er.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] }],
};

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('FO-21 viewer guard — the OPEN shell is edit-absent (no editContext)', () => {
  it('opens the canvas but renders no edit affordances', async () => {
    render(<ShellFrame dataSource={fakeDataSource({ getGraph: erGraph })} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />);
    fireEvent.click(await screen.findByText('er · sales'));
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());

    // read-only: the stale COUNT shows (truth surface) but nothing to click through to an edit.
    expect(screen.getByTestId('stale-count')).toHaveTextContent('1 stale');
    // no edit toolbar action, no context menu wiring.
    expect(screen.queryByText('+ Add object')).not.toBeInTheDocument();
    fireEvent.contextMenu(screen.getByTestId('skinned-canvas'));
    expect(screen.queryByTestId('node-context-menu')).not.toBeInTheDocument();
  });

  it('a program tab shows the DM-P4 processing placeholder, not a live processing canvas', async () => {
    const CAT: CatalogGroup[] = [{ kind: 'program', label: 'Programs', items: [{ ref: 'monthly_sales', qname: 'monthly_sales', kind: 'program', label: 'program monthly_sales' }] }];
    render(<ShellFrame dataSource={fakeDataSource()} workspace="ws" catalog={CAT} files={[]} displayMode="just-names" />);
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await waitFor(() => expect(screen.getByTestId('shell-processing-pending')).toBeInTheDocument());
    expect(screen.queryByTestId('processing-canvas')).not.toBeInTheDocument();
  });
});
