import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { emptyShell, openSubject, focusTab, promoteTab, closeTab } from '../shell-state.js';
import { TabBar } from '../TabBar.js';
import type { Subject } from '../types.js';

const erSales: Subject = { ref: 'file:///g/er_sales.ttrg', kind: 'schema', schemaCode: 'er', label: 'er · sales' };
const cubeSales: Subject = { ref: 'md.Sales', kind: 'cube', schemaCode: 'md', label: 'cube Sales' };

describe('subject tabs (A-2 β / S-7)', () => {
  it('opening two subjects yields two tabs, each with its kind-prefixed label', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = openSubject(s, cubeSales, { preview: false });
    expect(s.tabs.map((t) => t.subject.label)).toEqual(['er · sales', 'cube Sales']);
    expect(s.activeTabId).toBe(cubeSales.ref);
  });

  it('re-opening an open subject focuses it, never duplicates', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = openSubject(s, cubeSales, { preview: false });
    s = openSubject(s, erSales, { preview: false }); // re-open
    expect(s.tabs).toHaveLength(2);
    expect(s.activeTabId).toBe(erSales.ref);
  });

  it('each tab restores its own skin on activation (per-canvas skin, E-4)', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = { ...s, tabs: s.tabs.map((t) => ({ ...t, skin: 'er.crow' })) };
    s = openSubject(s, cubeSales, { preview: false });
    s = focusTab(s, erSales.ref);
    expect(s.tabs.find((t) => t.id === erSales.ref)?.skin).toBe('er.crow');
  });

  it('a preview tab renders italic and is promoted on pin AND on double-click (S-7)', () => {
    const s = openSubject(emptyShell, erSales, { preview: true });
    const onPromote = vi.fn();
    const { rerender } = render(<TabBar state={s} onFocus={vi.fn()} onPromote={onPromote} onClose={vi.fn()} />);
    const tab = screen.getByTestId('subject-tab');
    expect(tab).toHaveAttribute('data-preview');
    expect(tab).toHaveStyle({ fontStyle: 'italic' });

    // promotion trigger 1: the pin affordance
    fireEvent.click(within(tab).getByTestId('pin-tab'));
    expect(onPromote).toHaveBeenCalledWith(erSales.ref);

    // promotion trigger 2: double-click the tab
    fireEvent.doubleClick(tab);
    expect(onPromote).toHaveBeenCalledTimes(2);

    // promoted tab is no longer italic
    const promoted = promoteTab(s, erSales.ref);
    rerender(<TabBar state={promoted} onFocus={vi.fn()} onPromote={onPromote} onClose={vi.fn()} />);
    expect(screen.getByTestId('subject-tab')).not.toHaveAttribute('data-preview');
  });

  it('closing the active tab activates a neighbor', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = openSubject(s, cubeSales, { preview: false });
    s = closeTab(s, cubeSales.ref);
    expect(s.tabs.map((t) => t.id)).toEqual([erSales.ref]);
    expect(s.activeTabId).toBe(erSales.ref);
  });

  it('clicking a tab focuses it', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = openSubject(s, cubeSales, { preview: false });
    const onFocus = vi.fn();
    render(<TabBar state={s} onFocus={onFocus} onPromote={vi.fn()} onClose={vi.fn()} />);
    fireEvent.click(screen.getAllByTestId('subject-tab')[0]);
    expect(onFocus).toHaveBeenCalledWith(erSales.ref);
  });
});
