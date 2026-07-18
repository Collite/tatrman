import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { GraphPicker } from '../components/GraphPicker';
import type { GraphMetadata } from '@tatrman/lsp';

const mockGraph = (name: string, schema: 'er' | 'db' = 'er', overrides: Partial<GraphMetadata> = {}): GraphMetadata => ({
  name,
  uri: `file:///project/graphs/${name}.ttrg`,
  schema,
  description: undefined,
  tags: [],
  objectCount: 0,
  missingObjectCount: 0,
  ...overrides,
});

describe('<GraphPicker />', () => {
  const graphs: GraphMetadata[] = [
    mockGraph('artikl_overview', 'er', { description: 'Core article domain' }),
    mockGraph('invoicing_er', 'er'),
    mockGraph('users_db', 'db'),
  ];

  it('renders all graph names', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    expect(screen.getByText('artikl_overview')).toBeInTheDocument();
    expect(screen.getByText('invoicing_er')).toBeInTheDocument();
    expect(screen.getByText('users_db')).toBeInTheDocument();
  });

  it('calls onSelect with correct URI on click', () => {
    const onSelect = vi.fn();
    render(<GraphPicker graphs={graphs} onSelect={onSelect} />);
    fireEvent.click(screen.getByText('invoicing_er'));
    expect(onSelect).toHaveBeenCalledWith('file:///project/graphs/invoicing_er.ttrg');
  });

  it('filters by search text', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    const input = screen.getByPlaceholderText('Search graphs…') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'artikl' } });
    expect(screen.getByText('artikl_overview')).toBeInTheDocument();
    expect(screen.queryByText('invoicing_er')).not.toBeInTheDocument();
    expect(screen.queryByText('users_db')).not.toBeInTheDocument();
  });

  it('filters by schema badge click', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'db' }));
    expect(screen.queryByText('users_db')).toBeInTheDocument();
    expect(screen.queryByText('artikl_overview')).not.toBeInTheDocument();
    expect(screen.queryByText('invoicing_er')).not.toBeInTheDocument();
  });

  it('shows "No graphs match" when filter is too narrow', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    const input = screen.getByPlaceholderText('Search graphs…') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'nonexistent' } });
    expect(screen.getByText('No graphs match your search')).toBeInTheDocument();
  });

  it('shows schema filter buttons when multiple schemas are present', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    expect(screen.getByRole('button', { name: 'er' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'db' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All' })).toBeInTheDocument();
  });

  // FO-21 (FO-P0.S2.T4): "+ Create New Graph" moved to the authoring extension
  // (CreateGraphWizard now lives in tatrman-platform). The Viewer picker browses
  // and opens only; the create affordance re-enters via the extension in FO-P0.S4.
  it('exposes no "+ Create New Graph" edit affordance', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    expect(screen.queryByText('+ Create New Graph')).not.toBeInTheDocument();
  });

  it('shows graph count in subtitle', () => {
    render(<GraphPicker graphs={graphs} onSelect={vi.fn()} />);
    expect(screen.getByText('3 graphs found in this project')).toBeInTheDocument();
  });
});