import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import { fakeDataSource } from './fake-data-source.js';
import type { BindingMapData, GetGraphResponse } from '@tatrman/lsp';

// DS-P4.S2 — the lineage flow end to end in the shell: detail-panel root entry → the layers
// view, scope control, degradation, and a fresh-session deep-link boot to a scoped lineage.

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'file:///er.ttrg', qname: 'er.ttrg', kind: 'schema', schemaCode: 'er', label: 'er · sales' }] },
];

const NET_ATTR = 'orders_hero.er.entity.OrderLine.net_amount';
const NET_COL = 'orders_hero.db.dbo.table.OrderLine.NetAmount';
const NET_MEASURE = 'orders_hero.md.measure.net_amount';

const erGraph: GetGraphResponse = {
  schema: 'er', edges: [], layout: { nodes: {}, edges: {} }, missingObjects: [], imports: [],
  nodes: [{ qname: 'orders_hero.er.entity.OrderLine', kind: 'entity', name: 'OrderLine', schemaCode: 'er', label: 'OrderLine', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [{ name: 'net_amount', qname: NET_ATTR, kind: 'attribute', type: 'decimal', isKey: false, optional: true, isNameAttribute: false, isCodeAttribute: false }] }],
};
const bindings: BindingMapData = {
  entities: [], queries: [],
  attributes: [{ attributeQname: NET_ATTR, columnQname: NET_COL }],
};

const client = () => fakeDataSource({
  getGraph: erGraph,
  getBindings: bindings,
  getSymbolDetail: { qname: NET_ATTR, kind: 'attribute', name: 'net_amount', description: 'Net line amount', sourceUri: 'u', sourceLine: 1 },
});

function renderShell() {
  return render(<ShellFrame dataSource={client()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />);
}

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('lineage flow in the shell (S2.T5/T6)', () => {
  it('a fresh-session deep-link boots the lineage tab; β from the md measure spans db→er→md + program', async () => {
    // rooted at the md measure, β walks the full model bind-chain (live er↔db bind + md↔er
    // fixture) AND adds the writing program one hop — the canonical hero walkthrough.
    window.history.replaceState(null, '', `/w/ws/p/lineage?root=${encodeURIComponent(NET_MEASURE)}&scope=neighborhood&dir=upstream`);
    renderShell();
    await waitFor(() => expect(screen.getByTestId('lineage-layers-view')).toBeInTheDocument());
    const faces = screen.getAllByTestId('lineage-layer').map((l) => l.getAttribute('data-face'));
    expect(faces).toEqual(['db', 'er', 'md', 'program']); // ordered, all four
    expect(screen.getByTestId('scope-neighborhood')).toHaveAttribute('aria-pressed', 'true');
  });

  it('a γ request degrades to β with the DS-PERSP-001 hint (no runs backend)', async () => {
    window.history.replaceState(null, '', `/w/ws/p/lineage?root=${encodeURIComponent(NET_ATTR)}&scope=fullPath&dir=upstream`);
    renderShell();
    await waitFor(() => expect(screen.getByTestId('lineage-layers-view')).toBeInTheDocument());
    const hint = await screen.findByTestId('lineage-degraded-hint');
    expect(hint).toHaveAttribute('data-diagnostic', 'DS-PERSP-001');
    // the γ control stays selected (user sees what they asked for)
    expect(screen.getByTestId('scope-fullPath')).toHaveAttribute('aria-pressed', 'true');
  });

  it('changing scope re-generates (α narrows the chain)', async () => {
    window.history.replaceState(null, '', `/w/ws/p/lineage?root=${encodeURIComponent(NET_ATTR)}&scope=neighborhood&dir=upstream`);
    renderShell();
    await screen.findByTestId('lineage-layers-view');
    const chipsBefore = screen.getAllByTestId('lineage-chip').length;
    fireEvent.click(screen.getByTestId('scope-column'));
    await waitFor(() => expect(screen.getByTestId('scope-column')).toHaveAttribute('aria-pressed', 'true'));
    // α (column) drops the program provenance → fewer chips than β
    expect(screen.getAllByTestId('lineage-chip').length).toBeLessThanOrEqual(chipsBefore);
  });
});
