import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { GetGraphResponse } from '@tatrman/lsp';
import { fakeDataSource } from './fake-data-source.js';

// Deep-link boot (contracts §6): a shared URL lands a fresh session on the same view.
// DM-P2.S3: boots through a SCHEMA subject on the read path (the processing face is DM-P4) — the
// tab + rendered canvas prove the subject opened; the unknown-ref path proves DS-SHELL-001.

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

describe('deep-link URL boot (contracts §6 / S2.T2)', () => {
  afterEach(() => {
    cleanup();
    window.history.replaceState(null, '', '/');
  });

  it('a /w/:ws/s/:ref link opens the linked subject durably on boot', async () => {
    window.history.replaceState(null, '', '/w/ws/s/er_sales');
    renderShell();
    // the schema subject opened — its canvas renders on the read path
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.getAllByTestId('subject-tab')).toHaveLength(1);
    // no DS-SHELL-001 hint for a known ref
    expect(screen.queryByTestId('ds-shell-001-hint')).not.toBeInTheDocument();
  });

  it('an unknown :ref surfaces the DS-SHELL-001 hint instead of crashing', async () => {
    window.history.replaceState(null, '', '/w/ws/s/does.not.exist');
    renderShell();
    await waitFor(() => expect(screen.getByTestId('ds-shell-001-hint')).toBeInTheDocument());
    expect(screen.getByTestId('ds-shell-001-hint')).toHaveTextContent('does.not.exist');
  });

  it('honors the deep link even when the catalog arrives a tick after mount (async)', async () => {
    // Regression: App sets projectUri (mounting the shell) BEFORE awaiting the catalog, so the
    // shell first renders with an empty catalog. The URL-sync effect must not clobber the deep
    // link in that window — boot parses the snapshot taken at first render.
    window.history.replaceState(null, '', '/w/ws/s/er_sales');
    const { rerender } = renderShell([]); // mount with no catalog yet
    rerender(
      <ShellFrame dataSource={source()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />,
    );
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.getAllByTestId('subject-tab')).toHaveLength(1);
  });

  it('no subject in the URL opens nothing (empty shell)', async () => {
    window.history.replaceState(null, '', '/w/ws');
    renderShell();
    await waitFor(() => expect(screen.getByTestId('shell-no-tab')).toBeInTheDocument());
    expect(screen.queryByTestId('ds-shell-001-hint')).not.toBeInTheDocument();
  });
});
