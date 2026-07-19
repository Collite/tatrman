import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { LineageGraph } from '@tatrman/perspectives';
import { LineageLayersView } from '../LineageLayersView.js';

const graph: LineageGraph = {
  layers: [
    { face: 'db', nodes: [{ kind: 'column', ref: { qname: 'db.OrderLine.NetAmount', kind: 'column' }, label: 'NetAmount', face: 'db' }] },
    { face: 'er', nodes: [{ kind: 'attribute', ref: { qname: 'er.OrderLine.net_amount', kind: 'attribute' }, label: 'net_amount', face: 'er' }] },
    { face: 'md', nodes: [{ kind: 'measure', ref: { qname: 'md.Sales.net_amount', kind: 'measure' }, label: 'net_amount', face: 'md' }] },
    { face: 'program', nodes: [{ kind: 'program', ref: { qname: 'monthly_sales', kind: 'program' }, label: 'monthly_sales', face: 'program' }] },
  ],
  edges: [],
};

const degraded: LineageGraph = { ...graph, degraded: { requested: 'fullPath', served: 'neighborhood', reason: 'runs-need-platform-backend' } };

describe('LineageLayersView (contracts §4.2, C-3/C-4)', () => {
  it('renders the db→er→md→program layer chain in order', () => {
    render(<LineageLayersView graph={graph} />);
    const layers = screen.getAllByTestId('lineage-layer');
    expect(layers.map((l) => l.getAttribute('data-face'))).toEqual(['db', 'er', 'md', 'program']);
  });

  it('P-2: a chip is the same object everywhere — clicking fires onOpenObject with the qname', () => {
    const onOpen = vi.fn();
    render(<LineageLayersView graph={graph} handlers={{ onOpenObject: onOpen }} />);
    fireEvent.click(screen.getByText('NetAmount')); // inside the chip's open button
    expect(onOpen).toHaveBeenCalledWith('db.OrderLine.NetAmount');
  });

  it('a rootable chip offers a re-root affordance that works without getSymbolDetail (live-path fix)', () => {
    const onRoot = vi.fn();
    render(<LineageLayersView graph={graph} handlers={{ onRootAt: onRoot }} />);
    // db column, er attribute, md measure are rootable; the program is not
    const reroots = screen.getAllByTestId('lineage-chip-reroot');
    expect(reroots.length).toBe(3);
    fireEvent.click(reroots[0]);
    expect(onRoot).toHaveBeenCalledWith('db.OrderLine.NetAmount', 'column', 'NetAmount');
  });

  it('the scope control shows α/β/γ with β preselected by default', () => {
    render(<LineageLayersView graph={graph} />);
    expect(screen.getByTestId('scope-column')).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByTestId('scope-neighborhood')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('scope-fullPath')).toHaveAttribute('aria-pressed', 'false');
  });

  it('scope buttons fire onScopeChange', () => {
    const onScope = vi.fn();
    render(<LineageLayersView graph={graph} handlers={{ onScopeChange: onScope }} />);
    fireEvent.click(screen.getByTestId('scope-fullPath'));
    expect(onScope).toHaveBeenCalledWith('fullPath');
  });

  it('impact is a direction toggle on the SAME view (not a separate entry)', () => {
    const onDir = vi.fn();
    render(<LineageLayersView graph={graph} direction="upstream" handlers={{ onDirectionChange: onDir }} />);
    expect(screen.getByTestId('impact-toggle')).not.toBeChecked();
    fireEvent.click(screen.getByTestId('impact-toggle'));
    expect(onDir).toHaveBeenCalledWith('downstream');
  });

  it('renders the REAL derivation links from graph.edges (not a decorative per-column arrow)', () => {
    const withEdges: LineageGraph = {
      ...graph,
      edges: [
        { from: 'db.OrderLine.NetAmount', to: 'er.OrderLine.net_amount', relation: 'binds' },
        { from: 'er.OrderLine.net_amount', to: 'md.Sales.net_amount', relation: 'derives' },
        // a link to an off-canvas object → falls back to the qname leaf, never a fabricated face arrow
        { from: 'monthly_sales', to: 'db.OrderLine.NetAmount', relation: 'reads' },
      ],
    };
    render(<LineageLayersView graph={withEdges} />);
    const edges = screen.getAllByTestId('lineage-edge');
    expect(edges).toHaveLength(3);
    // each rendered edge carries the true endpoints + relation (direction preserved)
    expect(edges.map((e) => [e.getAttribute('data-from'), e.getAttribute('data-relation'), e.getAttribute('data-to')])).toEqual([
      ['db.OrderLine.NetAmount', 'binds', 'er.OrderLine.net_amount'],
      ['er.OrderLine.net_amount', 'derives', 'md.Sales.net_amount'],
      ['monthly_sales', 'reads', 'db.OrderLine.NetAmount'],
    ]);
    // endpoints resolve to their chip labels (leaf fallback proves nothing is fabricated)
    expect(edges[0].textContent).toContain('NetAmount');
    expect(edges[0].textContent).toContain('net_amount');
  });

  it('with no edges the links section is absent (no blind arrows implying connections)', () => {
    render(<LineageLayersView graph={graph} />); // graph.edges === []
    expect(screen.queryByTestId('lineage-links')).toBeNull();
    expect(screen.queryByTestId('lineage-edge')).toBeNull();
  });

  it('a degradation renders a visible labeled bar (DS-PERSP-001) AND the γ control stays selectable', () => {
    const onScope = vi.fn();
    render(<LineageLayersView graph={degraded} scope="fullPath" handlers={{ onScopeChange: onScope }} />);
    const hint = screen.getByTestId('lineage-degraded-hint');
    expect(hint).toHaveAttribute('data-diagnostic', 'DS-PERSP-001');
    expect(hint.textContent).toContain('neighborhood');
    // the user still sees they asked for γ (fullPath stays pressed) and can re-select it
    expect(screen.getByTestId('scope-fullPath')).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByTestId('scope-fullPath'));
    expect(onScope).toHaveBeenCalledWith('fullPath');
  });
});
