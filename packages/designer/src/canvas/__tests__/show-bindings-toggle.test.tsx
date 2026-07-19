import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import type { ModelGraph } from '@tatrman/lsp';
import type { BindingHint } from '@tatrman/canvas-core';
import type { KernelProps } from '../Kernel.js';
import { SkinnedCanvas } from '../SkinnedCanvas.js';

// A kernel mock that surfaces the graph's bindingHints so we can assert the decoration reaches
// the kernel only when the toggle is on.
vi.mock('../Kernel.js', () => ({
  CanvasKernel: (props: KernelProps) => (
    <div data-testid="mock-kernel" data-binding-hints={JSON.stringify(props.graph.bindingHints ?? null)} />
  ),
}));

const erGraph: ModelGraph = {
  schemaCode: 'er',
  nodes: [
    { qname: 'orders_hero.er.entity.Customer', kind: 'entity', name: 'Customer', schemaCode: 'er', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] },
  ],
  edges: [],
};
const dbGraph: ModelGraph = { schemaCode: 'db', nodes: [{ qname: 'orders_hero.db.dbo.table.Customer', kind: 'table', name: 'Customer', schemaCode: 'db', label: 'Customer', sourceUri: 'u', sourceLocation: { line: 1, column: 0 }, rows: [] }], edges: [] };
const positions = { 'orders_hero.er.entity.Customer': { x: 0, y: 0 } };
const hints: Record<string, BindingHint> = { 'orders_hero.er.entity.Customer': { target: 'dbo.Customer', kind: 'table' } };

const common = { displayMode: 'with-types' as const, nodePositions: positions, canvasKey: 'g', onNodeSelect: vi.fn() };

beforeEach(() => cleanup());

describe('show-bindings toggle (S-5) — er-canvas binding decoration', () => {
  it('renders the toggle on the er canvas, off by default (no hints reach the kernel)', async () => {
    render(<SkinnedCanvas {...common} graph={erGraph} bindingHints={hints} showBindings={false} onToggleShowBindings={vi.fn()} />);
    await screen.findByTestId('mock-kernel');
    expect(screen.getByTestId('show-bindings-checkbox')).not.toBeChecked();
    expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-binding-hints', 'null');
  });

  it('clicking the toggle fires onToggleShowBindings', async () => {
    const onToggle = vi.fn();
    render(<SkinnedCanvas {...common} graph={erGraph} bindingHints={hints} showBindings={false} onToggleShowBindings={onToggle} />);
    await screen.findByTestId('mock-kernel');
    fireEvent.click(screen.getByTestId('show-bindings-checkbox'));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('when on, the ghost hints reach the kernel', async () => {
    render(<SkinnedCanvas {...common} graph={erGraph} bindingHints={hints} showBindings onToggleShowBindings={vi.fn()} />);
    await screen.findByTestId('mock-kernel');
    const drawn = JSON.parse(screen.getByTestId('mock-kernel').getAttribute('data-binding-hints')!);
    expect(drawn).toEqual(hints);
  });

  it('the toggle does NOT appear on a non-er (db) canvas — binding is er↔db only (C-2)', async () => {
    render(<SkinnedCanvas {...common} graph={dbGraph} bindingHints={hints} showBindings onToggleShowBindings={vi.fn()} />);
    await screen.findByTestId('mock-kernel');
    expect(screen.queryByTestId('show-bindings-toggle')).not.toBeInTheDocument();
    // even with showBindings=true, a db canvas never carries the decoration
    expect(screen.getByTestId('mock-kernel')).toHaveAttribute('data-binding-hints', 'null');
  });
});
