// SPDX-License-Identifier: Apache-2.0
// FO-A1 W4 (P4.S2 / 4S2.5 + 4S2.6) — the Studio Designer authoring loop, end-to-end through the REAL
// ShellFrame render path. The commercial doors/AuthorPanel live in @tatrman/designer-authoring (a
// separate workspace, publish held), so this exercises the OPEN half the shell contributes: the
// marker-free `renderProcessingDoors` slot delivers a working loop substrate — author (onApplied
// refetch), validate (the `validate` capability → §2 diagnostics), preview (the `preview` capability
// → the ResultDrawer badged `preview`, never a save) — and a program deep-link re-opens the canvas.
// A stand-in doors slot drives the capabilities (proving the shell wires them); the real doors are
// tested in the platform package. Deterministic (fixture sources, no sleeps).
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { ShellEditContext, SlotValidateResult } from '../edit-context.js';
import { fakeDataSource } from './fake-data-source.js';
import { fixtureRunSource } from '../../model/run-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'program', label: 'Programs', items: [{ ref: 'monthly_sales', qname: 'monthly_sales', kind: 'program', label: 'program monthly_sales' }] },
];

// a loop-substrate doors slot: one button per capability the shell forwards. Stands in for the real
// (commercial) ProcessingDoors + AuthorPanel; proves the OPEN shell delivers the loop's moving parts.
function loopDoors(applied: () => void, onValidated: (r: SlotValidateResult) => void) {
  return function DoorsSlot(slot: Parameters<NonNullable<ShellEditContext['renderProcessingDoors']>>[0]) {
    return (
      <div data-testid="loop-doors" data-program={slot.programRef}>
        <button data-testid="loop-author" onClick={() => { applied(); slot.onApplied(); }}>author</button>
        <button data-testid="loop-validate" onClick={() => { void slot.validate?.().then(onValidated); }} disabled={!slot.validate}>validate</button>
        <button data-testid="loop-preview" onClick={() => slot.preview?.()} disabled={!slot.preview}>preview</button>
      </div>
    );
  };
}

function editContext(over: Partial<ShellEditContext> = {}): ShellEditContext {
  return {
    editable: true,
    removeNode: vi.fn().mockResolvedValue(true),
    saveNode: vi.fn().mockResolvedValue({ ok: true }),
    renderToolbarActions: () => null,
    renderNodeMenu: () => null,
    renderMissingObjects: () => null,
    ...over,
  };
}

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('FO-A1 authoring loop — 4S2.6 (author → validate → preview through the shell slot)', () => {
  it('walks the loop: the doors slot authors, validates (§2 diagnostics), and previews (badged, no save)', async () => {
    const applied = vi.fn();
    const validated = vi.fn();
    const result: SlotValidateResult = { supported: true, ok: true, diagnostics: [] };
    const validate = vi.fn(async (): Promise<SlotValidateResult> => result);
    render(
      <ShellFrame
        dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names"
        runSource={fixtureRunSource()}
        validateProgram={validate}
        editContext={editContext({ renderProcessingDoors: loopDoors(applied, validated) })}
      />,
    );
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');

    // the loop substrate mounted, scoped to the program.
    const doors = await screen.findByTestId('loop-doors');
    expect(doors).toHaveAttribute('data-program', 'monthly_sales');

    // author: the slot's onApplied fires (the shell refetches the graph).
    fireEvent.click(screen.getByTestId('loop-author'));
    expect(applied).toHaveBeenCalled();

    // validate: the shell forwarded the capability; it returns the §2 result.
    fireEvent.click(screen.getByTestId('loop-validate'));
    await waitFor(() => expect(validated).toHaveBeenCalledWith(result));

    // preview: runs the draft and the drawer is badged preview (a preview never persists).
    fireEvent.click(screen.getByTestId('loop-preview'));
    await waitFor(() => expect(screen.getByTestId('result-drawer')).toBeInTheDocument());
    expect(screen.getByTestId('result-preview-badge')).toBeInTheDocument();
    expect(screen.getByTestId('result-sink')).toHaveTextContent('top_customers');
  });

  it('A1-CAP-002: with no validate capability the slot reports it (Validate leg degraded)', async () => {
    render(
      <ShellFrame
        dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names"
        runSource={fixtureRunSource()}
        editContext={editContext({ renderProcessingDoors: loopDoors(vi.fn(), vi.fn()) })}
      />,
    );
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('loop-doors');
    expect(screen.getByTestId('loop-validate')).toBeDisabled(); // slot.validate undefined ⇒ A1-CAP-002
  });
});

describe('FO-A1 authoring loop — 4S2.5 (graduated program re-opens via deep link)', () => {
  it('a program deep link boots straight to the processing canvas (the reopen post-condition)', async () => {
    // post-graduate: the program is in the catalog; its deep link resolves to the running canvas.
    window.history.replaceState(null, '', '/w/ws/s/monthly_sales');
    render(
      <ShellFrame
        dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names"
        runSource={fixtureRunSource()}
      />,
    );
    await waitFor(() => expect(screen.getByTestId('processing-canvas')).toBeInTheDocument());
    expect(screen.getByTestId('truth-chip')).toHaveTextContent('face=processing');
  });
});
