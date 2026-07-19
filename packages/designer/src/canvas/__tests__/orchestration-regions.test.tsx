// SPDX-License-Identifier: Apache-2.0
// DM-P4.S2: ported processing render/run suite (canvas-core namespace rewritten to @tatrman).
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import type { CanvasGraph } from '@tatrman/canvas-core';
import type { KernelProps } from '../Kernel.js';
import { ProcessingCanvas } from '../ProcessingCanvas.js';
import { ProcessingNodeBody } from '../../skins/processing-nodes.js';
import { fixtureProcessingSource } from '../../model/processing-source.js';
import { processingGraphToCanvas } from '@tatrman/canvas-core';

// DS-P5.S1.T2 — orchestration: collapsed containers render as REGIONS (D-3 β); transfers are
// visible edges; store/display are program-level leaves; drill-in navigates (P-2, DS-P2 seam).
// Kernel is mocked (RF is unreliable in jsdom) so we assert the CanvasGraph the canvas produced
// and the drill wiring; the region *chrome* is asserted by rendering the skin body directly.

let lastGraph: CanvasGraph | null = null;
vi.mock('../Kernel.js', () => ({
  CanvasKernel: (props: KernelProps) => {
    lastGraph = props.graph;
    return (
      <div data-testid="mock-kernel" data-skin={props.skinId} data-derived={String(!!props.derived)} data-theme={props.theme}>
        {props.graph.containers.map((c) => (
          <button key={c.id} data-testid={`drill-${c.id}`} onClick={() => props.onDrillIn?.(c.id, c.label)}>drill {c.id}</button>
        ))}
        <button data-testid="drill-leaf" onClick={() => props.onDrillIn?.('store', 'store monthly_sales')}>drill leaf</button>
      </div>
    );
  },
}));

const source = fixtureProcessingSource();
beforeEach(() => { cleanup(); lastGraph = null; });

describe('orchestration graph → regions + leaves (mapped)', () => {
  it('collapses both containers to regions and keeps store/display as leaves', async () => {
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={[]} />);
    await screen.findByTestId('mock-kernel');
    await waitFor(() => expect(lastGraph).not.toBeNull());
    const g = lastGraph!;
    expect(g.containers.map((c) => c.id).sort()).toEqual(['crunch', 'extract']);
    expect(g.containers.every((c) => c.collapsed)).toBe(true);
    expect(g.nodes.find((n) => n.id === 'extract')!.kind).toBe('container-ref');
    expect(g.nodes.find((n) => n.id === 'store')!.kind).toBe('store');
    expect(g.nodes.find((n) => n.id === 'display')!.kind).toBe('display');
  });

  it('renders the synthesized transfer edge and the store→display control edge', async () => {
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={[]} />);
    await waitFor(() => expect(lastGraph).not.toBeNull());
    const roles = lastGraph!.edges.map((e) => e.role);
    expect(roles).toContain('transfer');
    expect(roles).toContain('control');
  });

  it('drilling a region emits onDrillIn (breadcrumb push); a leaf does NOT drill', async () => {
    const onDrillIn = vi.fn();
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={[]} onDrillIn={onDrillIn} />);
    await screen.findByTestId('drill-crunch');
    fireEvent.click(screen.getByTestId('drill-crunch'));
    expect(onDrillIn).toHaveBeenCalledWith('crunch', 'crunch');
    onDrillIn.mockClear();
    fireEvent.click(screen.getByTestId('drill-leaf'));
    expect(onDrillIn).not.toHaveBeenCalled(); // store is a leaf, not a region
  });

  it('drilling into the sql fragment arrives derived (read-only + banner) at the kernel', async () => {
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={['extract']} />);
    await waitFor(() => expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-derived', 'true'));
    expect(screen.getByTestId('truth-chip')).toHaveTextContent('derived (read-only)');
  });

  it('drilling into crunch shows the op-graph (join/filter/aggregate), not derived', async () => {
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={['crunch']} />);
    await waitFor(() => expect(lastGraph).not.toBeNull());
    expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-derived', 'false');
    expect(lastGraph!.nodes.map((n) => n.id).sort()).toEqual(['aggregate', 'filter', 'join']);
  });
});

describe('region chrome (skin body, contracts §1)', () => {
  const orchestration = processingGraphToCanvas({
    id: 'monthly_sales', face: 'processing',
    nodes: [{
      id: 'extract', qname: 'monthly_sales.extract', kind: 'container', label: 'extract',
      engine: 'sql @ mssql', fragmentDerived: true, collapsed: true,
      ports: [{ id: 'extract.orders', direction: 'out', role: 'data', connected: true, label: 'orders' }],
    }],
    edges: [],
  });
  const extract = orchestration.nodes[0];

  it('a collapsed region shows header + engine badge + ghost hint + ⌕ drill + fragment marking', () => {
    render(<ProcessingNodeBody node={extract} variant="stage" />);
    expect(screen.getByTestId('processing-node')).toHaveAttribute('data-region', 'true');
    expect(screen.getByTestId('node-label')).toHaveTextContent('extract');
    expect(screen.getByTestId('engine-badge')).toHaveTextContent('sql @ mssql');
    expect(screen.getByTestId('ghost-content')).toBeInTheDocument();
    expect(screen.getByTestId('region-drill')).toBeInTheDocument();
    expect(screen.getByTestId('derived-marking')).toHaveTextContent('sql · derived view');
  });
});
