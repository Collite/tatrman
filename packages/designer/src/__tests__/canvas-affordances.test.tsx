import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { AddObjectPicker } from '../AddObjectPicker';
import { MissingObjectsDrawer } from '../MissingObjectsDrawer';
import type { LspClient } from '../lsp-client';

vi.mock('cytoscape', () => ({
  default: vi.fn(() => ({ on: vi.fn(), destroy: vi.fn() })),
}));

const mockSymbols = [
  { qname: 'billing.invoicing.er.entity.Invoice', kind: 'entity', name: 'Invoice', packageName: 'billing.invoicing' },
  { qname: 'billing.invoicing.er.entity.LineItem', kind: 'entity', name: 'LineItem', packageName: 'billing.invoicing' },
  { qname: 'billing.products.er.entity.Product', kind: 'entity', name: 'Product', packageName: 'billing.products' },
  { qname: 'accounting.ledger.db.table.Account', kind: 'table', name: 'Account', packageName: 'accounting.ledger' },
];

const makeClient = (): LspClient =>
  ({
    listSymbols: vi.fn().mockResolvedValue(mockSymbols),
    addObjectToGraph: vi.fn().mockResolvedValue({ documentChanges: [] }),
  }) as unknown as LspClient;

describe('<AddObjectPicker />', () => {
  beforeEach(() => {
    cleanup();
  });

  it('renders search input and object list', async () => {
    const client = makeClient();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={[]}
        onSelect={vi.fn()}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    expect(screen.getByPlaceholderText(/search objects/i)).toBeInTheDocument();
    expect(screen.getByText('Invoice')).toBeInTheDocument();
    expect(screen.getByText('LineItem')).toBeInTheDocument();
    expect(screen.getByText('Product')).toBeInTheDocument();
  });

  it('filters objects by search text', async () => {
    const client = makeClient();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={[]}
        onSelect={vi.fn()}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    const searchInput = screen.getByPlaceholderText(/search objects/i);
    fireEvent.change(searchInput, { target: { value: 'Invoice' } });
    await waitFor(() => expect(screen.getByText('Invoice')).toBeInTheDocument());
    expect(screen.queryByText('LineItem')).not.toBeInTheDocument();
    expect(screen.queryByText('Product')).not.toBeInTheDocument();
  });

  it('shows out-of-scope indicator for objects whose package is not imported', async () => {
    const client = makeClient();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={['billing.invoicing']}
        onSelect={vi.fn()}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    const invoice = screen.getByText('Invoice').closest('button')!;
    const product = screen.getByText('Product').closest('button')!;
    expect(invoice).toHaveAttribute('data-out-of-scope', 'false');
    expect(product).toHaveAttribute('data-out-of-scope', 'true');
  });

  it('auto-import toggle is on by default and can be toggled off', async () => {
    const client = makeClient();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={[]}
        onSelect={vi.fn()}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    const toggle = screen.getByRole('checkbox', { name: /auto-import/i });
    expect(toggle).toBeChecked();
    fireEvent.click(toggle);
    expect(toggle).not.toBeChecked();
  });

  it('clicking an in-scope object calls onSelect with autoImport=true', async () => {
    const client = makeClient();
    const onSelect = vi.fn();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={['billing.invoicing']}
        onSelect={onSelect}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    fireEvent.click(screen.getByText('Invoice').closest('button')!);
    await waitFor(() =>
      expect(onSelect).toHaveBeenCalledWith('billing.invoicing.er.entity.Invoice', true)
    );
  });

  it('clicking an out-of-scope object with auto-import off calls onSelect with autoImport=false', async () => {
    const client = makeClient();
    const onSelect = vi.fn();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={['billing.invoicing']}
        onSelect={onSelect}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    const toggle = screen.getByRole('checkbox', { name: /auto-import/i });
    fireEvent.click(toggle);
    expect(toggle).not.toBeChecked();
    fireEvent.click(screen.getByText('Product').closest('button')!);
    await waitFor(() =>
      expect(onSelect).toHaveBeenCalledWith('billing.products.er.entity.Product', false)
    );
  });

  it('close button calls onClose', async () => {
    const client = makeClient();
    const onClose = vi.fn();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={[]}
        onSelect={vi.fn()}
        onClose={onClose}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(onClose).toHaveBeenCalled();
  });
});

describe('F3.2 — out-of-scope object, auto-import default on → autoImport: true', () => {
  it('clicking Product (out-of-scope) without toggling auto-import calls onSelect with autoImport=true', async () => {
    const client = makeClient();
    const onSelect = vi.fn();
    render(
      <AddObjectPicker
        lspClient={client}
        currentImports={['billing.invoicing']}
        onSelect={onSelect}
        onClose={vi.fn()}
      />
    );
    await waitFor(() => expect(client.listSymbols).toHaveBeenCalled());
    fireEvent.click(screen.getByText('Product').closest('button')!);
    await waitFor(() =>
      expect(onSelect).toHaveBeenCalledWith('billing.products.er.entity.Product', true)
    );
  });
});

describe('MissingObjectsDrawer', () => {
  beforeEach(() => { cleanup(); });

  it('renders all missing object qnames with a Remove button each', () => {
    const onRemove = vi.fn();
    const onClose = vi.fn();
    render(
      <MissingObjectsDrawer
        missingObjects={['a.b.er.entity.Gone', 'a.b.er.entity.AlsoGone']}
        onRemove={onRemove}
        onClose={onClose}
      />
    );
    expect(screen.getByText('a.b.er.entity.Gone')).toBeInTheDocument();
    expect(screen.getByText('a.b.er.entity.AlsoGone')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /remove/i }).length).toBe(2);
  });

  it('clicking Remove calls onRemove with the right qname', () => {
    const onRemove = vi.fn();
    render(
      <MissingObjectsDrawer
        missingObjects={['a.b.er.entity.Gone']}
        onRemove={onRemove}
        onClose={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /remove/i }));
    expect(onRemove).toHaveBeenCalledWith('a.b.er.entity.Gone');
  });
});

// The Canvas context-menu → onRemoveNode path and the App-level add/remove
// round-trip are covered in affordances-integration.test.tsx, which exercises
// the real components instead of a hand-copied menu.