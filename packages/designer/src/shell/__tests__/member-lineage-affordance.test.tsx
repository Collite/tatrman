// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 W2 (task 2.3, contracts §5) — the detail-panel member-lineage entry point.
// A member symbol (lineageRoot present) lights the Lineage affordance and routes through
// the SAME openLineage/onRootAt path as the chip re-root, passing the LineageRootRef
// (kind:'member'). On a backend that can't resolve members (WS/Veles) the affordance
// degrades VISIBLY (disabled + A1-CAP-001), never absent.

import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { TextDrawer, type DrawerNode } from '../TextDrawer.js';
import type { LineageRootRef } from '@tatrman/lsp';

afterEach(() => cleanup());

const lineageRoot: LineageRootRef = { kind: 'member', qname: 'er.entity.customer.region', label: 'region' };
const memberNode: DrawerNode = {
  qname: 'er.entity.customer.region', kind: 'member', label: 'region', rootKind: 'attribute', lineageRoot,
};

function open(props: Partial<Parameters<typeof TextDrawer>[0]> = {}, node: DrawerNode = memberNode) {
  return render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} {...props} />);
}

describe('member-lineage detail-panel affordance (W2)', () => {
  it('a member (lineageRoot present) shows the Lineage affordance, enabled', () => {
    open({ onOpenLineage: vi.fn() });
    const btn = screen.getByTestId('open-lineage');
    expect(btn).toBeInTheDocument();
    expect(btn).not.toBeDisabled();
    expect(screen.queryByTestId('a1-cap-001')).toBeNull();
  });

  it('clicking routes through onOpenLineage with the member root kind + the LineageRootRef (kind:"member")', () => {
    const onOpenLineage = vi.fn();
    open({ onOpenLineage });
    fireEvent.click(screen.getByTestId('open-lineage'));
    expect(onOpenLineage).toHaveBeenCalledTimes(1);
    // qname, ObjectKind (the member's semantic kind — converges with the chip path), label, rootRef
    expect(onOpenLineage).toHaveBeenCalledWith('er.entity.customer.region', 'attribute', 'region', lineageRoot);
  });

  it('backend without member support → affordance VISIBLE but DISABLED + A1-CAP-001 (never absent)', () => {
    // a rootable-kind node reaches the panel, but the backend can't resolve members
    const degradedNode: DrawerNode = { qname: 'er.entity.customer.region', kind: 'attribute', label: 'region' };
    open({ onOpenLineage: vi.fn(), memberLineageCapable: false }, degradedNode);
    const btn = screen.getByTestId('open-lineage');
    expect(btn).toBeInTheDocument();      // NOT absent
    expect(btn).toBeDisabled();            // visibly degraded
    expect(screen.getByTestId('a1-cap-001')).toHaveTextContent('A1-CAP-001');
  });

  it('a non-rootable top-level node (no lineageRoot) shows no affordance', () => {
    const opNode: DrawerNode = { qname: 'p.crunch', kind: 'op', label: 'crunch' };
    open({ onOpenLineage: vi.fn() }, opNode);
    expect(screen.queryByTestId('open-lineage')).toBeNull();
  });
});
