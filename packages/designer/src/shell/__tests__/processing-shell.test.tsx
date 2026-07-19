// SPDX-License-Identifier: Apache-2.0
// DM-P4.S3 (adapted from modeler DS-P5.S1.T6) — the REAL ShellFrame render path for a program
// subject: a program tab renders the processing canvas from the fixture source; the picker offers
// the processing roster; run gates honestly. **Adapted to the merged seams:** the shell consumes
// `ModelDataSource` (not an LspClient), and the processing INSERTION doors mount ONLY through the
// authoring extension's `editContext.renderProcessingDoors` slot (FO-21) — so the OPEN shell (no
// editContext) shows NO palette, and an injected context supplies it.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, within } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import type { ShellEditContext } from '../edit-context.js';
import { fakeDataSource } from './fake-data-source.js';
import { fixtureRunSource, type RunSource } from '../../model/run-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'program', label: 'Programs', items: [{ ref: 'monthly_sales', qname: 'monthly_sales', kind: 'program', label: 'program monthly_sales' }] },
];

/** A fake authoring context whose processing-doors slot renders one button per insertion edge (a
 *  stand-in for the real InsertionDoors, which live in @tatrman/designer-authoring). Proves the shell
 *  forwards the slot props (edges + onApplied) marker-free. */
function fakeEditContext(over: Partial<ShellEditContext> = {}): ShellEditContext {
  return {
    editable: true,
    removeNode: vi.fn().mockResolvedValue(true),
    saveNode: vi.fn().mockResolvedValue({ ok: true }),
    renderToolbarActions: () => null,
    renderNodeMenu: () => null,
    renderMissingObjects: () => null,
    renderProcessingDoors: (slot) => (
      <div data-testid="ext-doors">
        {slot.edges.map((e) => (
          <button key={e.edgeId} data-testid={`ext-edge-${e.edgeId}`} onClick={slot.onApplied}>{e.edgeId}</button>
        ))}
      </div>
    ),
    ...over,
  };
}

function renderShell(opts: { runSource?: RunSource; editContext?: ShellEditContext } = {}) {
  return render(
    <ShellFrame dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" runSource={opts.runSource} editContext={opts.editContext} />,
  );
}

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('ShellFrame — processing face (program subject)', () => {
  it('opens a program subject to the processing canvas (Stage default) from the fixture source', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await waitFor(() => expect(screen.getByTestId('processing-canvas')).toBeInTheDocument());
    expect(screen.getByTestId('truth-chip')).toHaveTextContent('skin=stage');
    const picker = screen.getByTestId('skin-picker');
    expect(within(picker).getByText('Stage')).toBeInTheDocument();
    expect(within(picker).getByText('Script')).toBeInTheDocument();
    expect(within(picker).queryByText("crow's-foot")).toBeNull();
  });

  it('switching the picker to Script re-skins the same program canvas (truth chip follows)', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');
    fireEvent.change(screen.getByTestId('skin-picker'), { target: { value: 'script' } });
    await waitFor(() => expect(screen.getByTestId('truth-chip')).toHaveTextContent('skin=script'));
    expect(screen.getByTestId('canvas-kernel')).toHaveAttribute('data-skin', 'script');
  });

  it('no run backend (default): Run is disabled-with-hint and ⌘K does NOT offer Run program', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');
    expect(screen.getByTestId('run-button')).toBeDisabled();
    expect(screen.getByTestId('ds-run-001')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    const titles = (await screen.findAllByTestId('cmdk-item')).map((i) => i.textContent);
    expect(titles.some((t) => t?.includes('Run program'))).toBe(false);
  });

  it('with a run backend injected: ⌘K offers Run program and a run lands the drawer (E-4 parity)', async () => {
    renderShell({ runSource: fixtureRunSource() });
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');
    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    const titles = (await screen.findAllByTestId('cmdk-item')).map((i) => i.textContent);
    expect(titles.some((t) => t?.includes('Run program'))).toBe(true);
    fireEvent.keyDown(window, { key: 'Escape' });
    fireEvent.click(screen.getByTestId('run-button'));
    await waitFor(() => expect(screen.getByTestId('result-drawer')).toBeInTheDocument());
    expect(screen.getByTestId('result-sink')).toHaveTextContent('top_customers');
  });
});

describe('ShellFrame — processing insertion doors via the marker-free slot (FO-21)', () => {
  it('OPEN shell (no editContext): NO insertion doors and ⌘K does NOT offer "Insert node…"', async () => {
    renderShell();
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');
    expect(screen.queryByTestId('ext-doors')).toBeNull();
    expect(screen.queryByTestId('palette-toggle')).toBeNull(); // the palette lives in the authoring ext
    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    const titles = (await screen.findAllByTestId('cmdk-item')).map((i) => i.textContent);
    expect(titles.some((t) => t?.includes('Insert node'))).toBe(false);
  });

  it('with an authoring context: the doors slot mounts with the canvas edges + ⌘K offers "Insert node…"', async () => {
    renderShell({ editContext: fakeEditContext() });
    fireEvent.click(await screen.findByText('program monthly_sales'));
    await screen.findByTestId('processing-canvas');
    // the shell forwarded the insertion edges to the slot (marker-free — the shell named no op)
    expect(await screen.findByTestId('ext-doors')).toBeInTheDocument();
    expect(screen.getByTestId('ext-edge-e_store')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    const titles = (await screen.findAllByTestId('cmdk-item')).map((i) => i.textContent);
    expect(titles.some((t) => t?.includes('Insert node'))).toBe(true);
  });
});
