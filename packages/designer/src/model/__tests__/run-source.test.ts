// SPDX-License-Identifier: Apache-2.0
// DM-P4.S1 (ported from modeler DS-P5.S2.T1) — the RunSource contract (contracts §5 / §1.3). Status
// walks the FIXED vocabulary (no invented states); readDisplayResult parses the canned Arrow IPC into
// the drawer's table shape; an absent backend exposes available:false (⇒ DS-RUN-001 disabled-with-hint).
// The corpus is co-located under ./fixtures (was repo-root samples/ in modeler).
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { fixtureRunSource, absentRunSource, parseArrowTable, type RunEvent } from '../run-source.js';

const here = dirname(fileURLToPath(import.meta.url));
const cannedArrow = () => readFileSync(join(here, 'fixtures/top_customers.arrow'));

async function collect(it: AsyncIterable<RunEvent>): Promise<RunEvent[]> {
  const out: RunEvent[] = [];
  for await (const e of it) out.push(e);
  return out;
}

describe('fixtureRunSource — status walk (§1.3 vocabulary)', () => {
  it('walks idle → running → done and names the display sink on done', async () => {
    const events = await collect(fixtureRunSource().run('monthly_sales'));
    expect(events.map((e) => e.status)).toEqual(['idle', 'running', 'done']);
    expect(events.at(-1)!.sinkRef).toBe('top_customers');
  });

  it('walks idle → running → failed with diagnostics (no invented states)', async () => {
    const events = await collect(fixtureRunSource({ outcome: 'failed', diagnostics: { errorCount: 2, warnCount: 1 } }).run('monthly_sales'));
    expect(events.map((e) => e.status)).toEqual(['idle', 'running', 'failed']);
    expect(events.at(-1)!.diagnostics).toEqual({ errorCount: 2, warnCount: 1 });
    const VOCAB = new Set(['idle', 'running', 'done', 'failed']);
    expect(events.every((e) => VOCAB.has(e.status))).toBe(true);
  });

  it('is available:true (a real backend)', () => {
    expect(fixtureRunSource().available).toBe(true);
  });
});

describe('display results — Arrow IPC parsing', () => {
  it('parseArrowTable parses the canned top_customers.arrow into columns + 5 rows', () => {
    const table = parseArrowTable(new Uint8Array(cannedArrow()));
    expect(table.columns).toEqual(['customer', 'region', 'net_amount', 'qty']);
    expect(table.numRows).toBe(5);
    expect(table.rows[0]).toEqual(['Acme Corp', 'North', 182450.5, 1240]);
  });

  it('readDisplayResult(top_customers) returns the same parsed table (embedded canned Arrow)', async () => {
    const table = await fixtureRunSource().readDisplayResult('top_customers');
    expect(table.numRows).toBe(5);
    expect(table.columns).toContain('net_amount');
    expect(table.rows[0][0]).toBe('Acme Corp');
  });

  it('the embedded canned Arrow byte-matches the committed corpus file (no drift)', async () => {
    const fromFixture = await fixtureRunSource().readDisplayResult('top_customers');
    const fromFile = parseArrowTable(new Uint8Array(cannedArrow()));
    expect(fromFixture).toEqual(fromFile);
  });
});

describe('absentRunSource — no backend (gate closed)', () => {
  it('exposes available:false', () => {
    expect(absentRunSource().available).toBe(false);
  });
});
