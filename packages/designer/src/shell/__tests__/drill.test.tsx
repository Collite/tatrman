import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { emptyShell, openSubject, drillIn, drillOut, drillTo, activeTab } from '../shell-state.js';
import { Breadcrumb } from '../Breadcrumb.js';
import type { Subject } from '../types.js';

// P-2: the SAME drill gesture family works on both faces — parametrize.
const SUBJECTS: Array<[string, Subject]> = [
  ['modeling (er)', { ref: 'g/er', kind: 'schema', schemaCode: 'er', label: 'er · sales' }],
  ['processing (program)', { ref: 'monthly_sales', kind: 'program', label: 'program monthly_sales' }],
];

describe.each(SUBJECTS)('in-tab drill — %s', (_name, subject) => {
  it('drillIn pushes a breadcrumb segment; drillOut pops one', () => {
    let s = openSubject(emptyShell, subject, { preview: false });
    const id = s.activeTabId!;
    s = drillIn(s, id, { id: 'crunch', label: 'crunch' });
    expect(activeTab(s)!.drillPath.map((d) => d.label)).toEqual(['crunch']);
    s = drillIn(s, id, { id: 'join', label: 'join' });
    expect(activeTab(s)!.drillPath).toHaveLength(2);
    s = drillOut(s, id);
    expect(activeTab(s)!.drillPath.map((d) => d.label)).toEqual(['crunch']);
  });

  it('drillTo(index) pops to that level; drillTo(-1) returns to the subject root', () => {
    let s = openSubject(emptyShell, subject, { preview: false });
    const id = s.activeTabId!;
    s = drillIn(s, id, { id: 'a', label: 'a' });
    s = drillIn(s, id, { id: 'b', label: 'b' });
    s = drillIn(s, id, { id: 'c', label: 'c' });
    s = drillTo(s, id, 0); // back to 'a'
    expect(activeTab(s)!.drillPath.map((d) => d.label)).toEqual(['a']);
    s = drillTo(s, id, -1); // subject root
    expect(activeTab(s)!.drillPath).toEqual([]);
  });
});

describe('breadcrumb component', () => {
  const subject: Subject = { ref: 'monthly_sales', kind: 'program', label: 'program monthly_sales' };

  it('renders nothing at the subject root (no drill)', () => {
    const s = openSubject(emptyShell, subject, { preview: false });
    const { container } = render(<Breadcrumb tab={activeTab(s)!} onDrillTo={vi.fn()} />);
    expect(container.querySelector('[data-testid="breadcrumb"]')).toBeNull();
  });

  it('renders root + segments; clicking a crumb pops to that level', () => {
    let s = openSubject(emptyShell, subject, { preview: false });
    const id = s.activeTabId!;
    s = drillIn(s, id, { id: 'crunch', label: 'crunch' });
    s = drillIn(s, id, { id: 'join', label: 'join' });
    const onDrillTo = vi.fn();
    render(<Breadcrumb tab={activeTab(s)!} onDrillTo={onDrillTo} />);
    expect(screen.getByTestId('crumb-root')).toHaveTextContent('program monthly_sales');
    const crumbs = screen.getAllByTestId('crumb');
    expect(crumbs.map((c) => c.textContent)).toEqual(['crunch', 'join']);
    fireEvent.click(crumbs[0]); // click 'crunch' (index 0)
    expect(onDrillTo).toHaveBeenCalledWith(0);
    fireEvent.click(screen.getByTestId('crumb-root'));
    expect(onDrillTo).toHaveBeenCalledWith(-1);
  });
});
