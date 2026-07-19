import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { TextDrawer, type DrawerNode } from '../TextDrawer.js';

const node: DrawerNode = {
  qname: 'db.dbo.Customer', kind: 'table', label: 'Customer',
  description: 'Customers of the shop',
  sourceText: 'def table Customer {\n  columns: [ def column id { type: int } ]\n}',
  sourceUri: 'file:///proj/db.ttrm', sourceLine: 3,
};

const attrNode: DrawerNode = { qname: 'orders_hero.er.entity.OrderLine.net_amount', kind: 'attribute', label: 'net_amount', description: 'Net line amount' };

describe('text drawer (A-3 β) — read-only peek half', () => {
  it('shows the textual property panel for the selected node', () => {
    render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} />);
    expect(screen.getByTestId('drawer-label')).toHaveTextContent('Customer');
    expect(screen.getByTestId('property-panel')).toHaveTextContent('Customers of the shop');
  });

  it('offers "Trace lineage" for a rootable object (attribute/column/measure/calc) and fires it', () => {
    const onOpenLineage = vi.fn();
    render(<TextDrawer open node={attrNode} onOpenInIde={vi.fn()} onClose={vi.fn()} onOpenLineage={onOpenLineage} />);
    fireEvent.click(screen.getByTestId('open-lineage'));
    expect(onOpenLineage).toHaveBeenCalledWith('orders_hero.er.entity.OrderLine.net_amount', 'attribute', 'net_amount');
  });

  it('does NOT offer "Trace lineage" for a non-rootable object (a table)', () => {
    render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} onOpenLineage={vi.fn()} />);
    expect(screen.queryByTestId('open-lineage')).not.toBeInTheDocument();
  });

  it('escalates to a read-only peek showing the node source text', () => {
    render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} />);
    expect(screen.queryByTestId('peek-source')).toBeNull(); // not peeking yet
    fireEvent.click(screen.getByTestId('peek-escalate'));
    expect(screen.getByTestId('peek-source')).toHaveTextContent('def table Customer');
    expect(screen.getByTestId('peek-readonly-badge')).toBeInTheDocument(); // 🔒
  });

  it('an edit gesture routes to DS-EDIT-001 (peek + open-in-IDE handoff), never an editor', () => {
    const onOpenInIde = vi.fn();
    render(<TextDrawer open node={node} onOpenInIde={onOpenInIde} onClose={vi.fn()} />);
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    expect(screen.getByTestId('ds-edit-001')).toHaveTextContent(/read-only/i);
    fireEvent.click(screen.getByTestId('edit-open-in-ide'));
    expect(onOpenInIde).toHaveBeenCalledWith('file:///proj/db.ttrm', 3);
  });

  it('open-in-IDE in the peek header hands off to the host', () => {
    const onOpenInIde = vi.fn();
    render(<TextDrawer open node={node} onOpenInIde={onOpenInIde} onClose={vi.fn()} />);
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('open-in-ide'));
    expect(onOpenInIde).toHaveBeenCalledWith('file:///proj/db.ttrm', 3);
  });

  it('has NO editable surface in this phase (no textarea / contentEditable)', () => {
    const { container } = render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} />);
    fireEvent.click(screen.getByTestId('peek-escalate'));
    expect(container.querySelector('textarea')).toBeNull();
    expect(container.querySelector('[contenteditable="true"]')).toBeNull();
    expect(container.querySelector('input')).toBeNull();
  });
});
