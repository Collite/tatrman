import { describe, it, expect } from 'vitest';
import { emptyShell, openSubject, promoteTab, dropPreviewIfLeaving, activeTab } from '../shell-state.js';
import { popOut, tabUrl } from '../popout.js';
import type { Subject } from '../types.js';

const erSales: Subject = { ref: 'file:///g/er_sales.ttrg', kind: 'schema', schemaCode: 'er', label: 'er · sales' };
const lineageSubject: Subject = { ref: 'md.Sales.net_amount', kind: 'cube', schemaCode: 'md', label: 'lineage · net_amount' };

describe('pop-out (A-2 γ / S-7)', () => {
  it('pinning a view pops it out as a PREVIEW tab carrying its full state', () => {
    const s = popOut(emptyShell, { subject: lineageSubject, perspective: 'lineage' });
    const t = activeTab(s)!;
    expect(t.preview).toBe(true); // transient italic
    expect(t.perspective).toBe('lineage');
    expect(t.subject.ref).toBe('md.Sales.net_amount');
  });

  it('pinning promotes the pop-out (preview → durable)', () => {
    let s = popOut(emptyShell, { subject: lineageSubject, perspective: 'lineage' });
    s = promoteTab(s, s.activeTabId!);
    expect(activeTab(s)!.preview).toBe(false);
  });

  it('navigating away from an UNPINNED preview drops it (preview discipline)', () => {
    // open a durable subject, then pop out a preview, then navigate back to the durable one
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = popOut(s, { subject: lineageSubject, perspective: 'lineage' });
    expect(s.tabs).toHaveLength(2);
    s = dropPreviewIfLeaving(s, erSales.ref);
    expect(s.tabs.map((t) => t.id)).toEqual([erSales.ref]); // the unpinned preview was dropped
    expect(s.activeTabId).toBe(erSales.ref);
  });

  it('a PINNED preview survives navigate-away', () => {
    let s = openSubject(emptyShell, erSales, { preview: false });
    s = popOut(s, { subject: lineageSubject, perspective: 'lineage' });
    s = promoteTab(s, s.activeTabId!);
    s = dropPreviewIfLeaving(s, erSales.ref);
    expect(s.tabs).toHaveLength(2); // pinned pop-out kept
  });

  it("the pop-out tab's URL is the contracts §6 format of its state (shareable)", () => {
    const s = popOut(emptyShell, { subject: lineageSubject, perspective: 'lineage' });
    const url = tabUrl('demo', activeTab(s)!);
    expect(url).toContain('/w/demo/p/lineage');
    expect(url).toContain(encodeURIComponent('md.Sales.net_amount'));

    const s2 = openSubject(emptyShell, erSales, { preview: false });
    expect(tabUrl('demo', activeTab(s2)!)).toContain('/w/demo/s/');
  });
});
