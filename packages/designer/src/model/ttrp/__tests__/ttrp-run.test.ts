// SPDX-License-Identifier: Apache-2.0
// DM-P4.S4 — the live RunSource over `ttrp/run` (single-shot). Finding A: no streaming + no Arrow, so
// the single call is wrapped as running→done/failed and there is NO display result (honest no-display).
import { describe, it, expect, vi } from 'vitest';
import { TtrpServerRunSource, type TtrpReadRunClient } from '../ttrp-source.js';
import type { RunEvent } from '../../run-source.js';

function client(over: Partial<TtrpReadRunClient> = {}): TtrpReadRunClient {
  return { getGraph: vi.fn(), run: vi.fn().mockResolvedValue({ runId: 'r1', exitCode: 0, out: ['ok'] }), ...over };
}

async function collect(it: AsyncIterable<RunEvent>): Promise<RunEvent[]> {
  const out: RunEvent[] = [];
  for await (const e of it) out.push(e);
  return out;
}

describe('TtrpServerRunSource — single-shot run wrapped as events (Finding A)', () => {
  it('is available:true', () => {
    expect(new TtrpServerRunSource(client()).available).toBe(true);
  });

  it('exitCode 0 → running then done, with NO sinkRef (honest no-display)', async () => {
    const events = await collect(new TtrpServerRunSource(client()).run('hero'));
    expect(events.map((e) => e.status)).toEqual(['running', 'done']);
    expect(events.at(-1)!.sinkRef).toBeUndefined();
  });

  it('nonzero exitCode → running then failed with diagnostics', async () => {
    const c = client({ run: vi.fn().mockResolvedValue({ runId: 'r2', exitCode: 2, out: [] }) });
    const events = await collect(new TtrpServerRunSource(c).run('hero'));
    expect(events.map((e) => e.status)).toEqual(['running', 'failed']);
    expect(events.at(-1)!.diagnostics).toEqual({ errorCount: 1, warnCount: 0 });
  });

  it('a run RPC failure degrades to a failed event (never throws out of the iterable)', async () => {
    const c = client({ run: vi.fn().mockRejectedValue(new Error('socket closed')) });
    const events = await collect(new TtrpServerRunSource(c).run('hero'));
    expect(events.map((e) => e.status)).toEqual(['running', 'failed']);
  });

  it('readDisplayResult throws — there is no Arrow display sink on the live backend', async () => {
    await expect(new TtrpServerRunSource(client()).readDisplayResult('x')).rejects.toThrow(/no-display/);
  });
});
