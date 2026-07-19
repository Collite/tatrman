import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { PerspectiveResult, BindingRibbon } from '@tatrman/perspectives';
import type { ViewStateStore } from '@tatrman/canvas-core';
import { DerivedCanvas } from '../DerivedCanvas.js';

const ribbon: BindingRibbon = {
  rows: [
    { kind: 'table', entity: { qname: 'er.entity.Customer', label: 'Customer' }, table: { qname: 'db.dbo.Customer', label: 'Customer master' } },
    { kind: 'query', entity: { qname: 'er.entity.ActiveCustomer', label: 'Active customer' }, query: { qname: 'query.query.active_customers', predicate: 'active this year', provenance: [{ qname: 'db.dbo.Customer', label: 'Customer master' }] } },
    { kind: 'unresolved', entity: { qname: 'er.entity.Orphan', label: 'Orphan' }, diagnostic: 'DS-PERSP-002', detail: 'db.dbo.Missing' },
  ],
};
const bindingResult: PerspectiveResult = { kind: 'custom', view: 'binding-ribbon', data: ribbon };
const lineageResult: PerspectiveResult = { kind: 'custom', view: 'lineage-layers', data: { layers: [{ face: 'db', nodes: [] }], edges: [] } };

describe('DerivedCanvas host (contracts §4, C-1)', () => {
  it('always shows the derived banner (DS-CANV-002)', () => {
    render(<DerivedCanvas result={bindingResult} />);
    expect(screen.getByTestId('derived-banner')).toBeInTheDocument();
  });

  it('routes kind:custom to the purpose-built view registry — binding-ribbon', () => {
    render(<DerivedCanvas result={bindingResult} />);
    expect(screen.getByTestId('binding-ribbon-view')).toBeInTheDocument();
    expect(screen.queryByTestId('lineage-layers-view')).not.toBeInTheDocument();
  });

  it('routes kind:custom to the purpose-built view registry — lineage-layers', () => {
    render(<DerivedCanvas result={lineageResult} />);
    expect(screen.getByTestId('lineage-layers-view')).toBeInTheDocument();
    expect(screen.queryByTestId('binding-ribbon-view')).not.toBeInTheDocument();
  });

  it('NEVER writes view-state — forward-guard for THIS component (spy store)', () => {
    // Honest scope: DerivedCanvas has no view-state write path by construction (it does not wire the
    // store to any persist). This spy guards against a REGRESSION that introduces one inside this
    // component — if a future edit called viewStateStore.write on interaction, this fails. It does
    // not (and cannot) prove the invariant for other code paths; that's the never-persisted rule at
    // the generator/host level.
    const store: ViewStateStore = {
      read: vi.fn().mockResolvedValue({ skin: 'er.crow', mode: 'auto', nodes: {}, collapsed: [] }),
      write: vi.fn().mockResolvedValue(undefined),
    };
    render(<DerivedCanvas result={bindingResult} viewStateStore={store} handlers={{ onSelectEntity: () => {} }} />);
    fireEvent.click(screen.getAllByTestId('binding-entity')[0]);
    expect(store.write).not.toHaveBeenCalled();
  });
});
