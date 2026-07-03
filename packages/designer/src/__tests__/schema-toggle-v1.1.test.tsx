import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Header } from '../components/Header';

describe('E4.9 — Schema-toggle UI removed (v1.1)', () => {
  it('Header does not render schema-toggle pills (no er/db/map buttons)', () => {
    render(
      <Header
        graphName="billing_er"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///proj"
        transportKind="browser"
        onFileLoad={() => {}}
        onDisplayModeChange={() => {}}
        onToggleNlPane={() => {}}
        onDirPick={() => {}}
        onBack={() => {}}
        onOpenFile={() => {}}
        onAddObject={() => {}}
        onMissingObjectsBadgeClick={() => {}}
        onDownloadLayout={undefined}
      />
    );

    expect(screen.queryByRole('button', { name: /^er$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^db$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^map$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^cnc$/i })).not.toBeInTheDocument();
  });

  it('Header still renders display-mode toggle buttons (just-names, with-types, with-constraints)', () => {
    render(
      <Header
        graphName="billing_er"
        missingObjectsCount={0}
        displayMode="just-names"
        projectUri="file:///proj"
        transportKind="browser"
        onFileLoad={() => {}}
        onDisplayModeChange={() => {}}
        onToggleNlPane={() => {}}
        onDirPick={() => {}}
        onOpenFile={() => {}}
        onBack={() => {}}
        onAddObject={() => {}}
        onMissingObjectsBadgeClick={() => {}}
        onDownloadLayout={undefined}
      />
    );

    expect(screen.getByRole('button', { name: 'just names' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'with types' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'with constraints' })).toBeInTheDocument();
  });
});