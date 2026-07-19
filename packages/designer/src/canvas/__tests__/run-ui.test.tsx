// SPDX-License-Identifier: Apache-2.0
// DM-P4.S2: ported processing render/run suite (canvas-core namespace rewritten to @tatrman).
import { describe, it, expect, beforeAll, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, within } from '@testing-library/react';
import { ProcessingCanvas } from '../ProcessingCanvas.js';
import { fixtureProcessingSource } from '../../model/processing-source.js';
import { fixtureRunSource, type RunEvent, type RunSource } from '../../model/run-source.js';
import { installBrowserPolyfills } from './test-utils.js';

// DS-P5.S2.T3 — a run walks the display node's StatusBadge idle→running→done ON the canvas (base
// layer, so parametrized over both skins); the bottom drawer + the base-layer preview chip carry
// the result; a failed run shows ✕ + diagnostics; results are NEVER in-canvas cards (D-5).

beforeAll(() => installBrowserPolyfills());
afterEach(() => cleanup());

const procSource = fixtureProcessingSource();

// a gated run source so we can observe the intermediate 'running' state deterministically.
function gatedRunSource(): { source: RunSource; release: () => void } {
  let release!: () => void;
  const gate = new Promise<void>((r) => { release = r; });
  const source: RunSource = {
    available: true,
    async *run(): AsyncIterable<RunEvent> {
      yield { status: 'idle' };
      yield { status: 'running' };
      await gate;
      yield { status: 'done', sinkRef: 'top_customers' };
    },
    readDisplayResult: fixtureRunSource().readDisplayResult,
  };
  return { source, release };
}

describe.each(['stage', 'script'])('run status walk on the canvas — %s skin', (skin) => {
  it('display node walks running → done; drawer + preview chip land the result', async () => {
    const { source, release } = gatedRunSource();
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} initialSkin={skin} runSource={source} />);
    fireEvent.click(await screen.findByTestId('run-button'));

    // running: the display node's StatusBadge shows ▶ running
    await waitFor(() => expect(screen.getByTestId('status-badge')).toHaveAttribute('data-badge', 'running'));
    expect(screen.queryByTestId('result-drawer')).toBeNull(); // no result yet

    release();
    // done: badge flips, the drawer opens with the top_customers table, preview chip shows rows
    await waitFor(() => expect(screen.getByTestId('status-badge')).toHaveAttribute('data-badge', 'done'));
    await waitFor(() => expect(screen.getByTestId('result-drawer')).toBeInTheDocument());
    expect(screen.getByTestId('result-sink')).toHaveTextContent('top_customers');
    expect(screen.getAllByTestId('result-row')).toHaveLength(5);
    expect(screen.getByTestId('preview-chip')).toHaveTextContent('5 rows');
  });
});

describe('run results — D-5 (drawer + chip, never in-canvas cards)', () => {
  it('the result table lives ONLY in the drawer; no in-canvas result card exists', async () => {
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={fixtureRunSource()} />);
    fireEvent.click(await screen.findByTestId('run-button'));
    await waitFor(() => expect(screen.getByTestId('result-drawer')).toBeInTheDocument());
    // the one result table is inside the drawer, not on the canvas
    const tables = screen.getAllByTestId('result-table');
    expect(tables).toHaveLength(1);
    expect(within(screen.getByTestId('result-drawer')).getByTestId('result-table')).toBe(tables[0]);
    expect(screen.queryByTestId('result-card')).toBeNull(); // D-5: no in-canvas result cards
  });

  it('closing the drawer keeps the preview chip (result summary stays on the node)', async () => {
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={fixtureRunSource()} />);
    fireEvent.click(await screen.findByTestId('run-button'));
    await waitFor(() => expect(screen.getByTestId('result-drawer')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('result-close'));
    expect(screen.queryByTestId('result-drawer')).toBeNull();
    expect(screen.getByTestId('preview-chip')).toBeInTheDocument();
  });
});

describe('run superseded by a drill (live-path guard)', () => {
  it('a run in flight when the canvas drills does NOT land its result on the new canvas', async () => {
    const { source, release } = gatedRunSource();
    const { rerender } = render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={source} />);
    fireEvent.click(await screen.findByTestId('run-button'));
    await waitFor(() => expect(screen.getByTestId('status-badge')).toHaveAttribute('data-badge', 'running'));
    // drill into a container while the run is mid-flight (the async loop is parked on the gate)
    rerender(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={['crunch']} runSource={source} />);
    await waitFor(() => expect(screen.queryByTestId('status-badge')).toBeNull()); // reset on drill
    release();
    // the superseded run's done event must not open a drawer on the crunch canvas
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByTestId('result-drawer')).toBeNull();
  });
});

describe('run failure', () => {
  it('a failed run shows the ✕ status badge with the diagnostics count (no result)', async () => {
    const failing: RunSource = fixtureRunSource({ outcome: 'failed', diagnostics: { errorCount: 3, warnCount: 0 } });
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={failing} />);
    fireEvent.click(await screen.findByTestId('run-button'));
    await waitFor(() => expect(screen.getByTestId('status-badge')).toHaveAttribute('data-badge', 'error'));
    expect(screen.getByTestId('diag-error')).toHaveTextContent('3');
    expect(screen.queryByTestId('result-drawer')).toBeNull();
  });
});
