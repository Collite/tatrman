// SPDX-License-Identifier: Apache-2.0
// DM-P4.S2: ported processing render/run suite (canvas-core namespace rewritten to @tatrman).
import { describe, it, expect, vi, beforeAll, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, within } from '@testing-library/react';
import { ProcessingCanvas } from '../ProcessingCanvas.js';
import { fixtureProcessingSource } from '../../model/processing-source.js';
import { installBrowserPolyfills } from './test-utils.js';

// DS-P5 review (Finding 4) — the region ⌕ button is a REAL drill trigger, not a dead affordance.
// Uses the real kernel (no mock) so the ⌕ the user actually sees fires onDrillIn on single-click.

beforeAll(() => installBrowserPolyfills());
afterEach(() => cleanup());

const source = fixtureProcessingSource();

describe('region ⌕ drill (real kernel)', () => {
  it('single-clicking a region ⌕ emits onDrillIn for that container', async () => {
    const onDrillIn = vi.fn();
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={[]} onDrillIn={onDrillIn} />);
    // find the crunch region's node body, then its ⌕ button
    await screen.findByTestId('processing-canvas');
    const crunchNode = await waitFor(() => {
      const node = screen.getAllByTestId('processing-node').find((n) => within(n).getByTestId('node-label').textContent === 'crunch');
      if (!node) throw new Error('crunch region not rendered yet');
      return node;
    });
    fireEvent.click(within(crunchNode).getByTestId('region-drill'));
    expect(onDrillIn).toHaveBeenCalledWith('crunch', 'crunch');
  });

  it('a leaf (store/display) has no ⌕ — only regions drill', async () => {
    render(<ProcessingCanvas source={source} programRef="monthly_sales" drillPath={[]} onDrillIn={vi.fn()} />);
    const storeNode = await waitFor(() => {
      const node = screen.getAllByTestId('processing-node').find((n) => within(n).getByTestId('node-label').textContent?.includes('store'));
      if (!node) throw new Error('store leaf not rendered yet');
      return node;
    });
    expect(within(storeNode).queryByTestId('region-drill')).toBeNull();
  });
});
