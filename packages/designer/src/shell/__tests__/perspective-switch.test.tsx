import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import { fakeDataSource } from './fake-data-source.js';
import type { BindingMapData, GetGraphResponse } from '@tatrman/lsp';

// DS-P4.S1.T6 — the er·sales subject tab switches canvas ↔ binding IN PLACE (A-2 β). The
// switch must preserve the tab and its breadcrumb; the underlying canvas state (held by
// subject.ref) is untouched, so returning restores the canvas.

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'file:///er.ttrg', qname: 'er.ttrg', kind: 'schema', schemaCode: 'er', label: 'er · sales' }] },
];

const erGraph: GetGraphResponse = {
  schema: 'er',
  nodes: [{ qname: 'orders_hero.er.entity.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] }],
  edges: [],
  layout: { nodes: {}, edges: {} },
  missingObjects: [],
  imports: [],
};

const bindings: BindingMapData = {
  entities: [
    { entityQname: 'orders_hero.er.entity.Customer', target: { kind: 'table', tableQname: 'orders_hero.db.dbo.table.Customer' } },
    { entityQname: 'orders_hero.er.entity.ActiveCustomer', target: { kind: 'query', queryQname: 'orders_hero.db.query.query.active_customers' } },
  ],
  attributes: [{ attributeQname: 'orders_hero.er.entity.Customer.id', columnQname: 'orders_hero.db.dbo.table.Customer.CustomerKey' }],
  queries: [{ qname: 'orders_hero.db.query.query.active_customers', predicate: 'active this year', provenance: ['orders_hero.db.dbo.table.Customer'] }],
};

const client = () => fakeDataSource({ getGraph: erGraph, getBindings: bindings });

function renderShell() {
  return render(
    <ShellFrame dataSource={client()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />,
  );
}

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('in-tab perspective switch (A-2 β / S1.T6)', () => {
  it('an er tab offers the Canvas|Binding switch; Binding renders the derived ribbon in place', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('er · sales'));
    // canvas first
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.getByTestId('perspective-switch')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('perspective-binding'));
    // the SAME tab now shows the derived binding ribbon (derived banner + ribbon view)
    await waitFor(() => expect(screen.getByTestId('binding-ribbon-view')).toBeInTheDocument());
    expect(screen.getByTestId('derived-banner')).toBeInTheDocument();
    expect(screen.queryByTestId('skinned-canvas')).not.toBeInTheDocument();
    // the query-bound entity is a first-class card
    expect(screen.getByTestId('binding-query-card')).toBeInTheDocument();
    // still exactly one tab (in-place switch, not a new tab)
    expect(screen.getAllByTestId('subject-tab')).toHaveLength(1);
  });

  it('switching back to Canvas restores the canvas (same tab, canvas state intact)', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('er · sales'));
    await screen.findByTestId('skinned-canvas');
    fireEvent.click(screen.getByTestId('perspective-binding'));
    await screen.findByTestId('binding-ribbon-view');
    fireEvent.click(screen.getByTestId('perspective-canvas'));
    await waitFor(() => expect(screen.getByTestId('skinned-canvas')).toBeInTheDocument());
    expect(screen.queryByTestId('binding-ribbon-view')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('subject-tab')).toHaveLength(1);
  });

  it('the er canvas offers the show-bindings toggle (S-5), off by default', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('er · sales'));
    await screen.findByTestId('skinned-canvas');
    expect(screen.getByTestId('show-bindings-checkbox')).not.toBeChecked();
  });

  it('both toolbar actions register in ⌘K (E-4 parity: perspective switch + show-bindings)', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('er · sales'));
    await screen.findByTestId('skinned-canvas');
    fireEvent.keyDown(window, { key: 'k', metaKey: true }); // open ⌘K
    const items = await screen.findAllByTestId('cmdk-item');
    const titles = items.map((i) => i.textContent);
    expect(titles.some((t) => t?.includes('binding perspective'))).toBe(true);
    expect(titles.some((t) => t?.includes('show bindings'))).toBe(true);
  });
});
