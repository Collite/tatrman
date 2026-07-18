import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Header } from '../Header';
import type { DisplayMode } from '@tatrman/lsp';

afterEach(cleanup);

// FO-21 (FO-P0.S2.T4): the Studio Viewer header. The "+ Add object" button and
// the click-through on the stale badge (which opened the remove drawer) moved to
// the authoring extension; the badge stays as a read-only indicator.
function renderHeader(overrides: Partial<React.ComponentProps<typeof Header>> = {}) {
  const props: React.ComponentProps<typeof Header> = {
    graphName: 'artikl_overview',
    missingObjectsCount: 0,
    displayMode: 'just-names' as DisplayMode,
    projectUri: 'file:///x',
    transportKind: null,
    onFileLoad: vi.fn(),
    onDisplayModeChange: vi.fn(),
    onToggleNlPane: vi.fn(),
    onDirPick: vi.fn(),
    onBack: vi.fn(),
    onOpenFile: vi.fn(),
    ...overrides,
  };
  return render(<Header {...props} />);
}

describe('Header', () => {
  it('shows "TTR Modeler Designer" when no graph is open', () => {
    renderHeader({ graphName: null, projectUri: null });
    expect(screen.getByText('TTR Modeler Designer')).toBeInTheDocument();
  });

  it('shows graph name when a graph is open', () => {
    renderHeader({ graphName: 'artikl_overview' });
    expect(screen.getByText('artikl_overview')).toBeInTheDocument();
  });

  it('shows stale badge (read-only indicator) when missingObjectsCount > 0', () => {
    renderHeader({ missingObjectsCount: 3 });
    const badge = screen.getByText('3 stale');
    expect(badge).toBeInTheDocument();
    // Viewer build: the badge is informational, not a button into an edit drawer.
    expect(badge.tagName).toBe('SPAN');
  });

  it('does not show stale badge when missingObjectsCount is 0', () => {
    renderHeader({ missingObjectsCount: 0 });
    expect(screen.queryByText('stale')).not.toBeInTheDocument();
  });

  it('exposes no "+ Add object" edit affordance', () => {
    renderHeader({ missingObjectsCount: 0 });
    expect(screen.queryByText('+ Add object')).not.toBeInTheDocument();
  });

  it('display-mode buttons call onDisplayModeChange with correct mode', () => {
    const onDisplayModeChange = vi.fn();
    renderHeader({ onDisplayModeChange });
    fireEvent.click(screen.getByRole('button', { name: 'with types' }));
    expect(onDisplayModeChange).toHaveBeenCalledWith('with-types');
    fireEvent.click(screen.getByRole('button', { name: 'with constraints' }));
    expect(onDisplayModeChange).toHaveBeenCalledWith('with-constraints');
  });

  it('NL button calls onToggleNlPane', () => {
    const onToggleNlPane = vi.fn();
    renderHeader({ onToggleNlPane });
    fireEvent.click(screen.getByRole('button', { name: /nl/i }));
    expect(onToggleNlPane).toHaveBeenCalledOnce();
  });

  it('back button calls onBack when a graph is open', () => {
    const onBack = vi.fn();
    renderHeader({ onBack });
    fireEvent.click(screen.getByRole('button', { name: '←' }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it('open file button calls onOpenFile', () => {
    const onOpenFile = vi.fn();
    renderHeader({ onOpenFile });
    fireEvent.click(screen.getByRole('button', { name: 'Open .ttrg…' }));
    expect(onOpenFile).toHaveBeenCalledOnce();
  });
});
