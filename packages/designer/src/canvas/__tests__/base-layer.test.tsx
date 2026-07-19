import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NodeBaseChrome } from '../base/BaseLayer.js';
import { BindingHintChip } from '../CanvasNode.js';
import type { NodeRenderProps } from '@tatrman/canvas-core';
import { anchors, fullState } from './test-utils.js';

describe('base layer (contracts §2 / D-1) — drawn at skin-declared anchors', () => {
  it('shows selection ring, 🔒, orphan mark, ✕ badge, and ⚠/✕ counts for a fully-loaded state', () => {
    render(<NodeBaseChrome state={fullState} anchors={anchors} />);
    expect(screen.getByTestId('selection-ring')).toBeInTheDocument();
    expect(screen.getByTestId('readonly-badge')).toBeInTheDocument(); // 🔒
    expect(screen.getByTestId('orphan-badge')).toBeInTheDocument(); // orphan mark
    expect(screen.getByTestId('status-badge')).toHaveAttribute('data-badge', 'error'); // failed ⇒ ✕
    expect(screen.getByTestId('diag-warn')).toHaveTextContent('1');
    expect(screen.getByTestId('diag-error')).toHaveTextContent('2');
  });

  it('places status/diagnostics badges at the skin-declared anchor points', () => {
    render(<NodeBaseChrome state={fullState} anchors={anchors} />);
    const status = screen.getByTestId('status-badge');
    expect(status).toHaveStyle({ left: `${anchors.status!.x}px`, top: `${anchors.status!.y}px` });
    const warn = screen.getByTestId('diag-warn');
    expect(warn).toHaveStyle({ left: `${anchors.diagnostics!.x}px` });
  });

  it('when a skin CLAIMS status, the base StatusBadge is absent (the skin renders it)', () => {
    render(<NodeBaseChrome state={fullState} anchors={anchors} claims={{ status: true }} />);
    expect(screen.queryByTestId('status-badge')).toBeNull(); // base did not draw it
    // never-claimable chrome is still app-drawn regardless of claims
    expect(screen.getByTestId('selection-ring')).toBeInTheDocument();
    expect(screen.getByTestId('readonly-badge')).toBeInTheDocument();
  });

  it('a claiming skin receives the SAME NodeBaseState and can render it', () => {
    // a skin body that claims status renders it from state; assert the render prop got the state
    const spy = vi.fn();
    function ClaimingBody(props: NodeRenderProps) {
      spy(props.state);
      return <div data-testid="claimed-status">{props.state.runStatus}</div>;
    }
    const node = { id: 'n', qname: 'n', kind: 'op', label: 'n', ports: [], slotData: {} };
    render(<ClaimingBody node={node} state={fullState} anchors={anchors} theme="ice" />);
    expect(spy).toHaveBeenCalledWith(fullState);
    expect(screen.getByTestId('claimed-status')).toHaveTextContent('failed');
  });
});

describe('BindingHintChip (S-5 show-bindings ghost decoration)', () => {
  it('a table bind renders the table target with the table glyph', () => {
    render(<BindingHintChip hint={{ target: 'dbo.Customer', kind: 'table' }} />);
    const chip = screen.getByTestId('binding-hint-chip');
    expect(chip).toHaveAttribute('data-binding-kind', 'table');
    expect(chip.textContent).toContain('dbo.Customer');
  });

  it('a query bind renders the gear glyph', () => {
    render(<BindingHintChip hint={{ target: 'active_customers', kind: 'query' }} />);
    expect(screen.getByTestId('binding-hint-chip').textContent).toContain('⚙');
  });

  it('an unresolved bind renders the warning treatment', () => {
    render(<BindingHintChip hint={{ target: 'Missing', kind: 'unresolved' }} />);
    const chip = screen.getByTestId('binding-hint-chip');
    expect(chip).toHaveAttribute('data-binding-kind', 'unresolved');
    expect(chip.textContent).toContain('⚠');
  });
});
