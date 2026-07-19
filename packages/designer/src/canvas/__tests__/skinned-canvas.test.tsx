import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, within } from '@testing-library/react';
import type { ModelGraph } from '@tatrman/lsp';
import type { KernelProps } from '../Kernel.js';
import { SkinnedCanvas } from '../SkinnedCanvas.js';

// Mock the RF kernel so we can drive onNodeDrag deterministically (RF drag is unreliable in
// jsdom) and read back the skin/positions the SkinnedCanvas resolved.
vi.mock('../Kernel.js', () => ({
  CanvasKernel: (props: KernelProps) => (
    <div data-testid="mock-kernel" data-skin={props.skinId} data-nodes={Object.keys(props.positions).join(',')} data-posjson={JSON.stringify(props.positions)}>
      <button data-testid="fake-drag" onClick={() => props.onNodeDrag?.('er.Customer', { x: 42, y: 84 })}>drag</button>
    </div>
  ),
}));

const erGraph: ModelGraph = {
  schemaCode: 'er',
  nodes: [
    { qname: 'er.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] },
    { qname: 'er.Order', kind: 'entity', name: 'Order', schemaCode: 'er', label: 'Order', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] },
  ],
  edges: [],
};

const positions = { 'er.Customer': { x: 0, y: 0 }, 'er.Order': { x: 200, y: 0 } };

beforeEach(() => cleanup());

describe('SkinnedCanvas — picker + truth chip (E-4)', () => {
  it('defaults to er.crow for an er graph and shows the truth chip', async () => {
    render(<SkinnedCanvas graph={erGraph} displayMode="with-types" nodePositions={positions} canvasKey="g" onNodeSelect={vi.fn()} />);
    await screen.findByTestId('mock-kernel');
    expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-skin', 'er.crow');
    expect(screen.getByTestId('truth-chip')).toHaveTextContent('skin=er.crow');
    expect(screen.getByTestId('truth-chip')).toHaveTextContent('canvas=er');
  });

  it('the picker lists the er roster', async () => {
    render(<SkinnedCanvas graph={erGraph} displayMode="with-types" nodePositions={positions} canvasKey="g" onNodeSelect={vi.fn()} />);
    const picker = await screen.findByTestId('skin-picker');
    expect(picker).toHaveValue('er.crow');
    expect(within(picker).getByText("crow's-foot")).toBeInTheDocument();
  });

  it('DS-SKIN-002: an unknown view-state skin falls back to the default and the chip says so', async () => {
    render(<SkinnedCanvas graph={erGraph} displayMode="with-types" nodePositions={positions} canvasKey="g" initialSkin="er.bogus" onNodeSelect={vi.fn()} />);
    await screen.findByTestId('mock-kernel');
    expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-skin', 'er.crow'); // fell back
    expect(screen.getByTestId('skin-fallback')).toHaveTextContent('er.bogus');
  });
});

// DS-P3.S2.T6 — per-kind rosters on the right canvases only; positions survive skin switches.
const mdGraph: ModelGraph = {
  schemaCode: 'md',
  nodes: [
    { qname: 'orders_hero.md.cubelet.Sales', kind: 'cubelet', name: 'Sales', schemaCode: 'md', label: 'Sales', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [{ name: 'qty', qname: 'q', kind: 'measure', type: 'sum', isKey: false, optional: false, isNameAttribute: false, isCodeAttribute: false }] },
    { qname: 'orders_hero.md.dimension.Time', kind: 'dimension', name: 'Time', schemaCode: 'md', label: 'Time', sourceUri: 'u', sourceLocation: { line: 2, column: 0 }, rows: [] },
  ],
  edges: [],
};
const cncGraph: ModelGraph = {
  schemaCode: 'cnc',
  nodes: [
    { qname: 'orders_hero.cnc.entity.Customer', kind: 'entity', name: 'Customer', schemaCode: 'cnc', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] },
  ],
  edges: [],
};

describe('SkinnedCanvas — per-kind rosters (E-2 / T6)', () => {
  it('an md canvas defaults to star-glyph and offers ONLY the md roster (never er.crow)', async () => {
    render(<SkinnedCanvas graph={mdGraph} displayMode="with-types" nodePositions={{}} canvasKey="g" onNodeSelect={vi.fn()} />);
    const picker = await screen.findByTestId('skin-picker');
    expect(picker).toHaveValue('md.star-glyph');
    expect(within(picker).getByText('star-glyph')).toBeInTheDocument();
    expect(within(picker).getByText('ER-dialect')).toBeInTheDocument();
    expect(within(picker).queryByText("crow's-foot")).toBeNull(); // er skin never offered on md
  });

  it('a cnc canvas defaults to bubbles and offers ONLY the cnc roster', async () => {
    render(<SkinnedCanvas graph={cncGraph} displayMode="with-types" nodePositions={{}} canvasKey="g" onNodeSelect={vi.fn()} />);
    const picker = await screen.findByTestId('skin-picker');
    expect(picker).toHaveValue('cnc.bubbles');
    expect(within(picker).getByText('bubbles')).toBeInTheDocument();
    expect(within(picker).getByText('cards')).toBeInTheDocument();
    expect(within(picker).queryByText('table-classic')).toBeNull();
  });

  it('loaded positions survive an md skin switch (C1-b-iv seam with real skins)', async () => {
    const loaded = { 'orders_hero.md.cubelet.Sales': { x: 460, y: 240 }, 'orders_hero.md.dimension.Time': { x: 820, y: 240 } };
    render(<SkinnedCanvas graph={mdGraph} displayMode="with-types" nodePositions={loaded} canvasKey="g" onNodeSelect={vi.fn()} />);
    const kernel = await screen.findByTestId('mock-kernel');
    const before = kernel.getAttribute('data-posjson');
    expect(JSON.parse(before!)).toEqual(loaded);

    fireEvent.change(screen.getByTestId('skin-picker'), { target: { value: 'md.er-dialect' } });
    await waitFor(() => expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-skin', 'md.er-dialect'));
    // the loaded (manual) positions are unchanged by the notation switch
    expect(JSON.parse(screen.getByTestId('mock-kernel').getAttribute('data-posjson')!)).toEqual(loaded);
  });
});

describe('SkinnedCanvas — drag persistence', () => {
  it('a node drag emits onPersistView with the dragged position + manual mode (DM-P2.S2 seam)', async () => {
    const onPersistView = vi.fn();

    render(<SkinnedCanvas graph={erGraph} displayMode="with-types" nodePositions={positions} canvasKey="g" onNodeSelect={vi.fn()} onPersistView={onPersistView} />);
    fireEvent.click(await screen.findByTestId('fake-drag'));

    await waitFor(() => expect(onPersistView).toHaveBeenCalled());
    const change = onPersistView.mock.calls.at(-1)![0];
    expect(change.mode).toBe('manual');
    expect(change.positions['er.Customer']).toEqual({ x: 42, y: 84 }); // the dragged position
  });

  it('does not emit onPersistView when canvasKey is null (persistence off)', async () => {
    const onPersistView = vi.fn();
    render(<SkinnedCanvas graph={erGraph} displayMode="with-types" nodePositions={positions} canvasKey={null} onNodeSelect={vi.fn()} onPersistView={onPersistView} />);
    fireEvent.click(await screen.findByTestId('fake-drag'));
    // give the debounce a chance; it must never fire without a canvasKey.
    await new Promise((r) => setTimeout(r, 20));
    expect(onPersistView).not.toHaveBeenCalled();
  });
});

