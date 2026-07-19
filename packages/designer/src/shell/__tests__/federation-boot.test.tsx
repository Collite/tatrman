// SPDX-License-Identifier: Apache-2.0
//
// FO-P1.S5.T3 — federation inbound + the copyable share URL, on the Viewer surface.
// Mirrors url-boot.test.tsx, but for the §3 federation grammar (`/s/viewer?object=…`)
// that arrives from ANOTHER app (launcher, Iris, a pasted share link).

import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { GetGraphResponse } from '@tatrman/lsp';
import { fakeDataSource } from './fake-data-source.js';

const CATALOG: CatalogGroup[] = [
  {
    kind: 'schema',
    label: 'Schemas',
    items: [{ ref: 'er_sales', qname: 'er_sales', kind: 'schema', schemaCode: 'er', label: 'er · sales' }],
  },
];

const erGraph: GetGraphResponse = {
  schema: 'er',
  nodes: [{ qname: 'er.entity.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] }],
  edges: [],
  layout: { nodes: {}, edges: {} },
  missingObjects: [],
  imports: [],
};

const source = () => fakeDataSource({ getGraph: erGraph });

function renderShell(catalog = CATALOG) {
  return render(
    <ShellFrame dataSource={source()} workspace="ws" catalog={catalog} files={[]} displayMode="just-names" />,
  );
}

describe('federation deep-link boot + share URL (FO contracts §3 / S5.T3)', () => {
  afterEach(() => {
    cleanup();
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('an inbound /s/viewer?object=… link opens the linked subject on boot', async () => {
    window.history.replaceState(null, '', '/s/viewer?object=er_sales');
    renderShell();
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.getAllByTestId('subject-tab')).toHaveLength(1);
    expect(screen.queryByTestId('ds-shell-001-hint')).not.toBeInTheDocument();
  });

  it('an inbound federation link to an unknown object surfaces the DS-SHELL-001 hint', async () => {
    window.history.replaceState(null, '', '/s/viewer?object=does.not.exist');
    renderShell();
    await waitFor(() => expect(screen.getByTestId('ds-shell-001-hint')).toBeInTheDocument());
    expect(screen.getByTestId('ds-shell-001-hint')).toHaveTextContent('does.not.exist');
  });

  it('renders a copyable federation URL (§3 projection) for the active view', async () => {
    window.history.replaceState(null, '', '/s/viewer?object=er_sales');
    renderShell();
    const btn = await screen.findByTestId('copy-federation-link');
    // the shared URL is the §3 federation projection, not the internal §6 address bar
    expect(btn.getAttribute('data-federation-url')).toMatch(/\/s\/viewer\?object=er_sales$/);
  });

  it('copies the federation URL to the clipboard on click', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    window.history.replaceState(null, '', '/s/viewer?object=er_sales');
    renderShell();
    const btn = await screen.findByTestId('copy-federation-link');
    fireEvent.click(btn);
    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1));
    expect(writeText.mock.calls[0][0]).toMatch(/\/s\/viewer\?object=er_sales$/);
  });

  it('hides the Ask-about-this affordance when no irisBaseUrl is configured (open Viewer)', async () => {
    window.history.replaceState(null, '', '/s/viewer?object=er_sales');
    renderShell();
    await screen.findByTestId('copy-federation-link');
    expect(screen.queryByTestId('ask-about-this')).not.toBeInTheDocument();
  });

  it('shows a §3 ask link into Iris when irisBaseUrl is configured', async () => {
    window.history.replaceState(null, '', '/s/viewer?object=er_sales');
    render(
      <ShellFrame
        dataSource={source()}
        workspace="ws"
        catalog={CATALOG}
        files={[]}
        displayMode="just-names"
        irisBaseUrl="https://iris.example.com"
      />,
    );
    const ask = await screen.findByTestId('ask-about-this');
    expect(ask.getAttribute('href')).toMatch(/^https:\/\/iris\.example\.com\/ask\?context=/);
  });
});
