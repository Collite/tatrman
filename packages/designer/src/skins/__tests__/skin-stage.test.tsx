import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TOKENS, processingGraphToCanvas, type ProcessingGraph, type RenderContext } from '@tatrman/canvas-core';
import { stage } from '../stage.js';
import { createSkinRegistry } from '../index.js';
import { CanvasKernel } from '../../canvas/Kernel.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

const HERO: ProcessingGraph = {
  id: 'monthly_sales', face: 'processing',
  nodes: [
    { id: 'crunch', qname: 'monthly_sales.crunch', kind: 'container', label: 'crunch', engine: 'polars', collapsed: true, ports: [
      { id: 'crunch.in', direction: 'in', role: 'data', connected: true, label: 'in' },
      { id: 'crunch.out', direction: 'out', role: 'data', connected: true, label: 'out' },
      { id: 'crunch.rejects', direction: 'out', role: 'rejects', connected: false, label: 'rejects ∅' },
    ] },
    { id: 'store', qname: 'monthly_sales.store', kind: 'store', label: 'store', ports: [{ id: 'store.in', direction: 'in', role: 'data', connected: true }] },
    { id: 'display', qname: 'monthly_sales.display', kind: 'display', label: 'display', ports: [{ id: 'display.in', direction: 'in', role: 'data', connected: true }] },
  ],
  edges: [
    { id: 'e_store', from: 'crunch', to: 'store', role: 'data' },
    { id: 'e_after', from: 'store', to: 'display', role: 'control', label: 'after' },
  ],
};
const graph = processingGraphToCanvas(HERO);
const ctx = { skin: stage, theme: 'ice' } as unknown as RenderContext;
const state = { selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false } as const;

describe('stage skin — the default processing skin (E-3a)', () => {
  it('is the registered processing default (LR, ice dot-grid)', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('processing').map((s) => s.id)).toContain('stage');
    expect(reg.defaultSkin('processing')).toBe('stage');
    expect(stage.flow.orientation).toBe('LR');
    expect(stage.canvas.background).toBe(TOKENS.ice);
    expect(stage.canvas.grid).toBe('dots');
  });

  it('renders icon-card nodes (kind glyph + label + engine badge) for a region', () => {
    const Body = stage.renderNode;
    render(<Body node={graph.nodes.find((n) => n.id === 'crunch')!} state={state} anchors={anchors} theme="ice" />);
    expect(screen.getByTestId('kind-glyph')).toBeInTheDocument();
    expect(screen.getByTestId('node-label')).toHaveTextContent('crunch');
    expect(screen.getByTestId('engine-badge')).toHaveTextContent('polars');
  });

  it('data/transfer edges are solid navy; the control edge is dashed gray (D-4)', () => {
    const data = stage.edgeStyle({ id: 'e', from: { node: 'a', port: 'p' }, to: { node: 'b', port: 'q' }, role: 'data' }, ctx);
    expect(data.stroke).toBe(TOKENS.stageNavy);
    expect(data.dash).toBeUndefined();
    const control = stage.edgeStyle({ id: 'c', from: { node: 'a', port: 'p' }, to: { node: 'b', port: 'q' }, role: 'control' }, ctx);
    expect(control.dash).toBeTruthy();
    expect(control.stroke).toBe(TOKENS.gray.structure);
  });

  it('D-4 proof: control ports sit on the CROSS axis, data on the FLOW axis', () => {
    const dataOut = stage.portGeometry({ id: 'p', direction: 'out', role: 'data', connected: true }, graph.nodes[0]);
    expect(dataOut.placement).toBe('flow-out');
    const ctrlIn = stage.portGeometry({ id: 'c', direction: 'in', role: 'control', connected: true }, graph.nodes[0]);
    expect(ctrlIn.placement).toBe('cross-in');
    const ctrlOut = stage.portGeometry({ id: 'c', direction: 'out', role: 'control', connected: true }, graph.nodes[0]);
    expect(ctrlOut.placement).toBe('cross-out');
  });

  it('EVERY port renders incl. the unconnected rejects stub (D-2)', () => {
    // the labelled port strip shows the rejects stub
    const Body = stage.renderNode;
    render(<Body node={graph.nodes.find((n) => n.id === 'crunch')!} state={state} anchors={anchors} theme="ice" />);
    const reject = screen.getByTestId('port-strip').querySelector('[data-role="rejects"]');
    expect(reject).not.toBeNull();
    expect(reject).toHaveAttribute('data-connected', 'false');
    // and the kernel draws a Handle for every CanvasPort (including synthesized control handles)
    const reg = createSkinRegistry();
    const totalPorts = graph.nodes.reduce((n, node) => n + node.ports.length, 0);
    const { container } = render(
      <div style={{ width: 800, height: 600 }}>
        <CanvasKernel graph={graph} registry={reg} skinId="stage" positions={{ crunch: { x: 0, y: 0 }, store: { x: 300, y: 0 }, display: { x: 600, y: 0 } }} />
      </div>,
    );
    expect(container.querySelectorAll('.react-flow__handle').length).toBe(totalPorts);
  });
});
