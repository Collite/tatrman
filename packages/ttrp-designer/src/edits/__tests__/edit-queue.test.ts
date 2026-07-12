// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { submitEdits, type ApplyOutcome, type EditContext } from '../edit-queue.js';
import { edit } from '../graph-edits.js';

/** Versioned stale-reject → replay (T5.4.3, C1-d-iii). */
describe('edit queue', () => {
  function ctx(outcomes: ApplyOutcome[]): EditContext & { versions: number[]; refreshes: number } {
    let v = 1;
    const versions: number[] = [];
    let i = 0;
    return {
      versions,
      refreshes: 0,
      version: () => v,
      async apply(_e, version) {
        versions.push(version);
        return outcomes[i++]!;
      },
      async refresh() {
        v += 1;
        (this as { refreshes: number }).refreshes += 1;
        return v;
      },
    };
  }

  it('succeeds first try', async () => {
    const c = ctx([{ ok: true }]);
    const r = await submitEdits([edit.addNode('crunch', 'Load', 'x')], c);
    expect(r.ok).toBe(true);
    expect(c.versions).toEqual([1]);
  });

  it('on stale, re-pulls and replays against the bumped version', async () => {
    const c = ctx([{ ok: false, stale: true }, { ok: true }]);
    const r = await submitEdits([edit.connect('a', 'b')], c);
    expect(r.ok).toBe(true);
    expect(c.versions).toEqual([1, 2]); // replayed at the refreshed version
  });

  it('a non-stale rejection surfaces immediately (no replay)', async () => {
    const c = ctx([{ ok: false, stale: false, message: 'occupied single-in port' }]);
    const r = await submitEdits([edit.connect('a', 'b')], c);
    expect(r).toEqual({ ok: false, message: 'occupied single-in port' });
    expect(c.versions).toEqual([1]);
  });

  it('gives up after maxAttempts stale retries', async () => {
    const c = ctx([
      { ok: false, stale: true },
      { ok: false, stale: true },
      { ok: false, stale: true },
    ]);
    const r = await submitEdits([edit.assignTarget('crunch', 'polars')], c, 3);
    expect(r.ok).toBe(false);
  });
});
