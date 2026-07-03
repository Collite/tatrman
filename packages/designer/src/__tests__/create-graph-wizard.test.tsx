import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { CreateGraphWizard } from '../CreateGraphWizard';
import type { LspClient } from '../lsp-client';

// The Step-2 dependency mini-graph uses cytoscape; mock it (no real canvas in jsdom).
vi.mock('cytoscape', () => ({
  default: vi.fn(() => ({ on: vi.fn(), destroy: vi.fn() })),
}));

const mockPkgGraph = {
  packages: [
    { name: 'billing.invoicing', documentUris: ['file:///p/billing/invoicing/ttrg'] },
    { name: 'billing.products', documentUris: ['file:///p/billing/products/ttrg'] },
    { name: 'accounting.ledger', documentUris: ['file:///p/accounting/ledger/ttrg'] },
  ],
  dependencies: [
    { from: 'billing.invoicing', to: 'billing.products', citedBy: ['billing.invoicing'] },
    { from: 'accounting.ledger', to: 'billing.products', citedBy: ['accounting.ledger'] },
  ],
  cycles: [],
};

const makeClient = (): LspClient =>
  ({
    getPackageGraph: vi.fn().mockResolvedValue(mockPkgGraph),
    createGraph: vi.fn().mockResolvedValue({
      documentChanges: [
        { kind: 'create', uri: 'file:///project/graphs/testgraph.ttrg' },
        { textDocument: { uri: 'file:///project/graphs/testgraph.ttrg', version: null }, edits: [{ newText: 'graph TestGraph {\n    schema: er\n    objects: []\n}\n' }] },
      ],
    }),
    openDocument: vi.fn().mockResolvedValue(undefined),
    listSymbols: vi.fn().mockResolvedValue([
      { qname: 'billing.invoicing.er.entity.Invoice', kind: 'entity', name: 'Invoice' },
      { qname: 'billing.invoicing.er.entity.LineItem', kind: 'entity', name: 'LineItem' },
    ]),
  }) as unknown as LspClient;

async function goToStep3(_client: LspClient) {
  await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
  fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
  fireEvent.click(screen.getByRole('button', { name: /next/i }));
  await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
  fireEvent.click(screen.getByRole('button', { name: /continue/i }));
  await waitFor(() => expect(screen.getByText('Invoice')).toBeInTheDocument());
}

describe('<CreateGraphWizard />', () => {
  beforeEach(() => {
    cleanup();
  });

  describe('Step 1 — pick packages', () => {
    it('fetches and displays packages on mount', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      expect(screen.getByText('billing.invoicing')).toBeInTheDocument();
      expect(screen.getByText('billing.products')).toBeInTheDocument();
      expect(screen.getByText('accounting.ledger')).toBeInTheDocument();
    });

    it('Next is disabled when no package selected', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).toBeDisabled());
    });

    it('Next enables when at least one package selected', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled());
    });

    it('clicking a package checkbox toggles selection', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      const checkbox = screen.getByRole('checkbox', { name: /billing\.invoicing/i });
      fireEvent.click(checkbox);
      await waitFor(() => expect(checkbox).toBeChecked());
      fireEvent.click(checkbox);
      await waitFor(() => expect(checkbox).not.toBeChecked());
    });

    it('advances to step 2 with Next', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      expect(screen.getByRole('button', { name: /add all transitive/i })).toBeInTheDocument();
    });
  });

  describe('Step 2 — review dependencies', () => {
    it('shows selected packages and Add all transitive button', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      expect(screen.getByRole('button', { name: /add all transitive/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /continue/i })).toBeInTheDocument();
    });

    it('Add all transitive adds missing packages to selection', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /add all transitive/i }));
      await waitFor(() => expect(screen.getByRole('button', { name: 'billing.products' })).toHaveAttribute('aria-pressed', 'true'));
    });

    it('renders the dependency mini-graph and toggles a package via its chip', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      // the embedded cytoscape panel is present
      await waitFor(() => expect(screen.getByTestId('package-dep-graph')).toBeInTheDocument());
      // billing.invoicing chip is selected (pressed); billing.products is not
      expect(screen.getByRole('button', { name: 'billing.invoicing' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('button', { name: 'billing.products' })).toHaveAttribute('aria-pressed', 'false');
      // clicking the products chip adds it
      fireEvent.click(screen.getByRole('button', { name: 'billing.products' }));
      await waitFor(() => expect(screen.getByRole('button', { name: 'billing.products' })).toHaveAttribute('aria-pressed', 'true'));
    });

    it('Continue advances to step 3', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /continue/i }));
      await waitFor(() => expect(screen.getByText('Invoice')).toBeInTheDocument());
    });
  });

  describe('Step 3 — pick objects', () => {
    it('shows objects from selected packages', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      expect(screen.getByText('Invoice')).toBeInTheDocument();
    });

    it('groups objects by package and bulk-selects a whole package', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      // package group header acts as a bulk-select control
      const selectAll = screen.getByRole('checkbox', { name: /select all in billing\.invoicing/i });
      fireEvent.click(selectAll);
      // both objects in the package are now selected → counter reads 2 of 2, Next enabled
      await waitFor(() => expect(screen.getByText(/2 of 2 selected/)).toBeInTheDocument());
      expect(screen.getByRole('checkbox', { name: /Invoice/i })).toBeChecked();
      expect(screen.getByRole('checkbox', { name: /LineItem/i })).toBeChecked();
      expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled();
    });

    it('Next is disabled when no object selected', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      expect(screen.getByRole('button', { name: /next/i })).toBeDisabled();
    });

    it('selecting an object enables Next', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      fireEvent.click(screen.getByRole('checkbox', { name: /Invoice/i }));
      await waitFor(() => expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled());
    });
  });

  describe('Step 4 — pick schema kind', () => {
    it('renders radio options for er and db', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      fireEvent.click(screen.getByRole('checkbox', { name: /Invoice/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByRole('radio', { name: /er/i })).toBeInTheDocument());
      expect(screen.getByRole('radio', { name: /db/i })).toBeInTheDocument();
    });

    it('er is selected by default', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      fireEvent.click(screen.getByRole('checkbox', { name: /Invoice/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByRole('radio', { name: /er/i })).toBeChecked());
    });

    it('switching schema updates selection', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep3(client);
      fireEvent.click(screen.getByRole('checkbox', { name: /Invoice/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByRole('radio', { name: /er/i })).toBeInTheDocument());
      fireEvent.click(screen.getByRole('radio', { name: /db/i }));
      await waitFor(() => expect(screen.getByRole('radio', { name: /db/i })).toBeChecked());
    });
  });

  describe('Step 5 — name + save', () => {
    async function goToStep5(client: LspClient) {
      await goToStep3(client);
      fireEvent.click(screen.getByRole('checkbox', { name: /Invoice/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByRole('radio', { name: /er/i })).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByRole('textbox', { name: /graph name/i })).toBeInTheDocument());
    }

    it('shows suggested filename based on graph name', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep5(client);
      fireEvent.change(screen.getByRole('textbox', { name: /graph name/i }), { target: { value: 'TestGraph' } });
      await waitFor(() => expect(screen.getByTestId('suggested-filename')).toHaveTextContent('testgraph.ttrg'));
    });

    it('invalid name shows error and Save is disabled', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep5(client);
      fireEvent.change(screen.getByRole('textbox', { name: /graph name/i }), { target: { value: 'My Graph' } });
      await waitFor(() => expect(screen.getByText('Name must be a valid identifier: letters, digits, underscore; no spaces')).toBeInTheDocument());
      expect(screen.getByRole('button', { name: /save/i })).toBeDisabled();
    });

    it('valid identifier name shows no error and Save is enabled', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep5(client);
      fireEvent.change(screen.getByRole('textbox', { name: /graph name/i }), { target: { value: 'ValidName' } });
      await waitFor(() => expect(screen.queryByText('Name must be a valid identifier')).not.toBeInTheDocument());
      await waitFor(() => expect(screen.getByRole('button', { name: /save/i })).not.toBeDisabled());
    });

    it('Save calls client.createGraph with assembled params', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await goToStep5(client);
      fireEvent.change(screen.getByRole('textbox', { name: /graph name/i }), { target: { value: 'TestGraph' } });
      fireEvent.click(screen.getByRole('button', { name: /save/i }));
      await waitFor(() => expect(client.createGraph).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'TestGraph', schema: 'er' })
      ));
    });

    it('Save calls onComplete with the new graph URI', async () => {
      const client = makeClient();
      const onComplete = vi.fn();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={onComplete} onCancel={vi.fn()} />);
      await goToStep5(client);
      fireEvent.change(screen.getByRole('textbox', { name: /graph name/i }), { target: { value: 'NewGraph' } });
      fireEvent.click(screen.getByRole('button', { name: /save/i }));
      await waitFor(() => expect(onComplete).toHaveBeenCalledWith('file:///project/graphs/newgraph.ttrg'));
    });
  });

  describe('Cancel', () => {
    it('calls onCancel when Cancel button clicked', async () => {
      const client = makeClient();
      const onCancel = vi.fn();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={onCancel} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('checkbox', { name: /billing\.invoicing/i }));
      fireEvent.click(screen.getByRole('button', { name: /next/i }));
      await waitFor(() => expect(screen.getByText('billing.invoicing')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
      expect(onCancel).toHaveBeenCalled();
    });
  });

  describe('Progress dots', () => {
    it('renders 5 dots', async () => {
      const client = makeClient();
      render(<CreateGraphWizard lspClient={client} projectRoot="file:///project" onComplete={vi.fn()} onCancel={vi.fn()} />);
      await waitFor(() => expect(client.getPackageGraph).toHaveBeenCalled());
      expect(screen.getAllByTestId(/^dot-/).length).toBe(5);
    });
  });
});