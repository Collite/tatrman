import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import type { BindingRibbon } from '@tatrman/perspectives';
import { BindingRibbonView } from '../BindingRibbonView.js';

const ribbon: BindingRibbon = {
  rows: [
    { kind: 'table', entity: { qname: 'er.entity.Customer', label: 'Customer' }, table: { qname: 'db.dbo.Customer', label: 'Customer master' } },
    { kind: 'query', entity: { qname: 'er.entity.ActiveCustomer', label: 'Active customer' }, query: { qname: 'query.query.active_customers', predicate: 'Customers active this year', provenance: [{ qname: 'db.dbo.Customer', label: 'Customer master' }, { qname: 'db.dbo.OrderHeader', label: 'Order header' }] } },
    { kind: 'unresolved', entity: { qname: 'er.entity.Orphan', label: 'Orphan' }, diagnostic: 'DS-PERSP-002', detail: 'db.dbo.Missing' },
  ],
};

describe('BindingRibbonView (contracts §4.1, C-2)', () => {
  it('renders one row per binding, entities ← ribbons → targets', () => {
    render(<BindingRibbonView ribbon={ribbon} />);
    expect(screen.getAllByTestId('binding-row')).toHaveLength(3);
    expect(screen.getAllByTestId('binding-ribbon-path').length).toBeGreaterThanOrEqual(3);
  });

  it('a query-bound entity is a first-class query card (gear, predicate, base-table provenance)', () => {
    render(<BindingRibbonView ribbon={ribbon} />);
    const card = screen.getByTestId('binding-query-card');
    expect(card.textContent).toContain('⚙');
    expect(card.textContent).toContain('Customers active this year');
    expect(within(card).getByTestId('binding-query-provenance').textContent).toContain('Customer master');
    expect(within(card).getByTestId('binding-query-provenance').textContent).toContain('Order header');
  });

  it('a dangling bind renders with the DS-PERSP-002 warning treatment, never hidden', () => {
    render(<BindingRibbonView ribbon={ribbon} />);
    const u = screen.getByTestId('binding-unresolved');
    expect(u.getAttribute('data-diagnostic')).toBe('DS-PERSP-002');
    expect(u.textContent).toContain('db.dbo.Missing');
  });

  it('clicking an entity fires onSelectEntity; a second click clears it (toggle)', () => {
    const onSelect = vi.fn();
    const { rerender } = render(<BindingRibbonView ribbon={ribbon} onSelectEntity={onSelect} />);
    fireEvent.click(screen.getAllByTestId('binding-entity')[0]);
    expect(onSelect).toHaveBeenCalledWith('er.entity.Customer');
    // parent supplies the expansion → toggling off calls back with null
    const expanded: BindingRibbon = { ...ribbon, expanded: { entity: 'er.entity.Customer', pairs: [{ attribute: 'id', column: 'CustomerKey' }] } };
    rerender(<BindingRibbonView ribbon={expanded} onSelectEntity={onSelect} />);
    fireEvent.click(screen.getAllByTestId('binding-entity')[0]);
    expect(onSelect).toHaveBeenLastCalledWith(null);
  });

  it('when expanded, the selected entity shows attribute→column pairs', () => {
    const expanded: BindingRibbon = { ...ribbon, expanded: { entity: 'er.entity.Customer', pairs: [{ attribute: 'id', column: 'CustomerKey' }, { attribute: 'name', column: 'CustomerName' }] } };
    render(<BindingRibbonView ribbon={expanded} />);
    const pairs = screen.getAllByTestId('binding-pair');
    expect(pairs).toHaveLength(2);
    expect(pairs[0].textContent).toContain('id');
    expect(pairs[0].textContent).toContain('CustomerKey');
  });
});
