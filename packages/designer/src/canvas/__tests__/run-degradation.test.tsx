// SPDX-License-Identifier: Apache-2.0
// DM-P4.S2: ported processing render/run suite (canvas-core namespace rewritten to @tatrman).
import { describe, it, expect, beforeAll, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ProcessingCanvas } from '../ProcessingCanvas.js';
import { fixtureProcessingSource, type ProcessingGraphSource } from '../../model/processing-source.js';
import { absentRunSource, fixtureRunSource } from '../../model/run-source.js';
import { installBrowserPolyfills } from './test-utils.js';
import type { ProcessingGraph } from '@tatrman/canvas-core';

// DS-P5.S2.T4 — with no run backend the run controls render DISABLED-WITH-HINT (DS-RUN-001),
// present not hidden (P-3); the drawer and chip simply don't populate; no error spam.

beforeAll(() => installBrowserPolyfills());
afterEach(() => cleanup());

const procSource = fixtureProcessingSource();

describe('run degradation — no backend (DS-RUN-001)', () => {
  it('the run button renders disabled with the DS-RUN-001 hint (never hidden)', async () => {
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={absentRunSource()} />);
    const btn = await screen.findByTestId('run-button');
    expect(btn).toBeDisabled();
    expect(screen.getByTestId('ds-run-001')).toBeInTheDocument();
  });

  it('an absent runSource prop is treated the same (disabled-with-hint)', async () => {
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} />);
    expect(await screen.findByTestId('run-button')).toBeDisabled();
    expect(screen.getByTestId('ds-run-001')).toBeInTheDocument();
  });

  it('clicking the disabled control does nothing — no drawer, no chip, no crash', async () => {
    render(<ProcessingCanvas source={procSource} programRef="monthly_sales" drillPath={[]} runSource={absentRunSource()} />);
    fireEvent.click(await screen.findByTestId('run-button'));
    expect(screen.queryByTestId('result-drawer')).toBeNull();
    expect(screen.queryByTestId('preview-chip')).toBeNull();
    expect(screen.queryByTestId('status-badge')).toBeNull();
  });
});

// Review Finding 3 — "available backend but no display sink" must NOT be mislabeled "no backend".
describe('run gate — program with no display sink (available backend)', () => {
  const STORE_ONLY: ProcessingGraph = {
    id: 'materialize_only', face: 'processing',
    nodes: [
      { id: 'crunch', qname: 'materialize_only.crunch', kind: 'container', label: 'crunch', engine: 'polars', collapsed: true, ports: [{ id: 'crunch.out', direction: 'out', role: 'data', connected: true }] },
      { id: 'store', qname: 'materialize_only.store', kind: 'store', label: 'store result', ports: [{ id: 'store.in', direction: 'in', role: 'data', connected: true }] },
    ],
    edges: [{ id: 'e', from: 'crunch', to: 'store', role: 'data' }],
  };
  const storeOnlySource: ProcessingGraphSource = {
    getProgramGraph: async () => STORE_ONLY,
    getContainerGraph: async () => ({ id: 'x', face: 'processing', nodes: [], edges: [] }),
  };

  it('Run is disabled with a DISTINCT "no display sink" hint, not DS-RUN-001', async () => {
    render(<ProcessingCanvas source={storeOnlySource} programRef="materialize_only" drillPath={[]} runSource={fixtureRunSource()} />);
    expect(await screen.findByTestId('run-button')).toBeDisabled();
    expect(screen.getByTestId('no-display-hint')).toBeInTheDocument();
    expect(screen.queryByTestId('ds-run-001')).toBeNull(); // the backend IS available — not "no backend"
  });
});
