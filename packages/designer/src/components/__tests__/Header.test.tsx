import { afterEach, describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Header } from '../Header';

afterEach(cleanup);

describe('Header', () => {
  it('shows "TTR Modeler Designer" when no graph is open', () => {
    render(
      <Header
        graphName={null}
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri={null}
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    expect(screen.getByText('TTR Modeler Designer')).toBeInTheDocument();
  });

  it('shows graph name when a graph is open', () => {
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    expect(screen.getByText('artikl_overview')).toBeInTheDocument();
  });

  it('shows stale badge when missingObjectsCount > 0', () => {
    const onMissingObjectsBadgeClick = vi.fn();
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={3}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={onMissingObjectsBadgeClick}
      />
    );
    expect(screen.getByText('3 stale')).toBeInTheDocument();
    fireEvent.click(screen.getByText('3 stale'));
    expect(onMissingObjectsBadgeClick).toHaveBeenCalledOnce();
  });

  it('does not show stale badge when missingObjectsCount is 0', () => {
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    expect(screen.queryByText('stale')).not.toBeInTheDocument();
  });

  it('display-mode buttons call onDisplayModeChange with correct mode', () => {
    const onDisplayModeChange = vi.fn();
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={onDisplayModeChange}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'with types' }));
    expect(onDisplayModeChange).toHaveBeenCalledWith('with-types');
    fireEvent.click(screen.getByRole('button', { name: 'with constraints' }));
    expect(onDisplayModeChange).toHaveBeenCalledWith('with-constraints');
  });

  it('NL button calls onToggleNlPane', () => {
    const onToggleNlPane = vi.fn();
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={onToggleNlPane}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /nl/i }));
    expect(onToggleNlPane).toHaveBeenCalledOnce();
  });

  it('back button calls onBack when a graph is open', () => {
    const onBack = vi.fn();
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={onBack}
        onOpenFile={vi.fn()}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: '←' }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it('open file button calls onOpenFile', () => {
    const onOpenFile = vi.fn();
    render(
      <Header
        graphName="artikl_overview"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///x"
        transportKind={null}
        onFileLoad={vi.fn()}
        onDisplayModeChange={vi.fn()}
        onToggleNlPane={vi.fn()}
        onDirPick={vi.fn()}
        onBack={vi.fn()}
        onOpenFile={onOpenFile}
        onAddObject={vi.fn()}
        onMissingObjectsBadgeClick={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Open .ttrg…' }));
    expect(onOpenFile).toHaveBeenCalledOnce();
  });
});