import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { TextDrawer, type DrawerNode } from '../TextDrawer.js';

// DS-P6.S1.T4 — the drawer's edit half (A-3 β). With edit ON, peek escalates to an embedded
// editor and a save routes through the ONE WorkspaceEdit path (onSaveEdit → applyGraphEdit-class
// seam, never a second write path). With edit OFF, the same Edit gesture is the DS-P2 peek +
// DS-EDIT-001 behavior, unchanged (regression).

const node: DrawerNode = {
  qname: 'monthly_sales.crunch', kind: 'op', label: 'crunch',
  sourceText: 'join orders+lines\nfilter cancelled', sourceUri: 'file:///p.ttrp', sourceLine: 3,
};

afterEach(() => cleanup());

function open(props: Partial<Parameters<typeof TextDrawer>[0]> = {}) {
  return render(
    <TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} {...props} />,
  );
}

describe('drawer edit half — edit ON', () => {
  it('Edit escalates the peek to an embedded editor; Save routes through the one WorkspaceEdit path', () => {
    const onSaveEdit = vi.fn();
    open({ editEnabled: true, onSaveEdit });
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    // the embedded editor appears, prefilled with the source (NOT a DS-EDIT-001 hint)
    const editor = screen.getByTestId('drawer-editor') as HTMLTextAreaElement;
    expect(editor).toBeInTheDocument();
    expect(editor.value).toBe(node.sourceText);
    expect(screen.queryByTestId('ds-edit-001')).toBeNull();

    fireEvent.change(editor, { target: { value: 'join orders+lines\nfilter cancelled\naggregate' } });
    fireEvent.click(screen.getByTestId('drawer-save'));
    expect(onSaveEdit).toHaveBeenCalledTimes(1);
    expect(onSaveEdit).toHaveBeenCalledWith(node, 'join orders+lines\nfilter cancelled\naggregate');
  });

  it('changing the drawer subject resets the editor buffer (no cross-node write contamination)', () => {
    const onSaveEdit = vi.fn();
    const { rerender } = render(<TextDrawer open node={node} onOpenInIde={vi.fn()} onClose={vi.fn()} editEnabled onSaveEdit={onSaveEdit} />);
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    fireEvent.change(screen.getByTestId('drawer-editor'), { target: { value: 'EDITED A' } });
    // select a different node while mid-edit
    const nodeB: DrawerNode = { qname: 'monthly_sales.store', kind: 'store', label: 'store', sourceText: 'store body' };
    rerender(<TextDrawer open node={nodeB} onOpenInIde={vi.fn()} onClose={vi.fn()} editEnabled onSaveEdit={onSaveEdit} />);
    // the editor is reset (back to peek) — A's buffer cannot be saved onto B
    expect(screen.queryByTestId('drawer-editor')).toBeNull();
    expect(onSaveEdit).not.toHaveBeenCalled();
  });

  it('Save is disabled when the buffer is unchanged (no no-op writes)', () => {
    const onSaveEdit = vi.fn();
    open({ editEnabled: true, onSaveEdit });
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    expect(screen.getByTestId('drawer-save')).toBeDisabled(); // unchanged
    fireEvent.change(screen.getByTestId('drawer-editor'), { target: { value: node.sourceText + ' x' } });
    expect(screen.getByTestId('drawer-save')).not.toBeDisabled(); // dirty
  });

  it('Cancel discards the editor without saving', () => {
    const onSaveEdit = vi.fn();
    open({ editEnabled: true, onSaveEdit });
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    fireEvent.click(screen.getByTestId('drawer-cancel'));
    expect(screen.queryByTestId('drawer-editor')).toBeNull();
    expect(onSaveEdit).not.toHaveBeenCalled();
  });
});

describe('drawer edit half — edit OFF (regression: DS-P2 peek unchanged)', () => {
  it('Edit shows the DS-EDIT-001 read-only hint, no editable surface', () => {
    open({ editEnabled: false });
    fireEvent.click(screen.getByTestId('peek-escalate'));
    fireEvent.click(screen.getByTestId('drawer-edit'));
    expect(screen.getByTestId('ds-edit-001')).toBeInTheDocument();
    expect(screen.queryByTestId('drawer-editor')).toBeNull();
    // the peek <pre> is still not editable
    expect(screen.getByTestId('peek-source').tagName).toBe('PRE');
  });
});
