// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1.T2 — the SPIs are implementable from the published types alone (usability = the cert lever
// in miniature). A fixture parser + canon-function implement the contracts and produce well-shaped
// output; the SDK itself ships no runtime, so the impls live here in the test.

import { describe, expect, it } from 'vitest';
import type {
  ProposalSourceParser,
  CanonFunction,
  ParseContext,
} from '../index.js';

// A fixture proposal-source parser: one CSV-ish line → one insert edit; a blank line → a diagnostic.
const fixtureParser: ProposalSourceParser = {
  id: 'fixture-parser',
  version: '1.0.0',
  parse(input, _ctx: ParseContext) {
    const text = new TextDecoder().decode(input);
    const batch = { target: { ref: 'demo.rows' }, source: { type: 'import' as const, pluginId: 'fixture-parser', pluginVersion: '1.0.0' }, rows: [] as { op: 'insert'; values: Record<string, unknown> }[] };
    const diagnostics: { row: number; code: string; detail: string }[] = [];
    text.split('\n').filter((l) => l.length > 0).forEach((line, i) => {
      if (line.trim() === '') diagnostics.push({ row: i + 1, code: 'EMPTY', detail: 'blank line' });
      else batch.rows.push({ op: 'insert', values: { raw: line } });
    });
    return { batch, diagnostics };
  },
};

// A fixture canon-function: pure sum (stands in for TWR/MWR/FIFO).
const sum: CanonFunction<readonly number[], number> = {
  id: 'sum',
  version: '1.0.0',
  signature: { params: [{ name: 'xs', type: 'number[]' }], returns: 'number' },
  eval: (...xs) => xs.reduce((a, b) => a + b, 0),
};

describe('SDK SPIs are implementable (contracts-only, §13/§3)', () => {
  it('a proposal-source parser maps bytes → a §5 batch + diagnostics, never throwing', () => {
    const { batch, diagnostics } = fixtureParser.parse(new TextEncoder().encode('a,1\nb,2\n'), { targets: ['demo.rows'] });
    expect(batch.rows).toHaveLength(2);
    expect(batch.source.pluginVersion).toBe('1.0.0'); // version pinned (P-3)
    expect(diagnostics).toEqual([]);
  });

  it('a canon-function is a pure versioned fn callable with typed args', () => {
    expect(sum.eval(1, 2, 3)).toBe(6);
    expect(sum.eval(1, 2, 3)).toBe(6); // deterministic
    expect(sum.signature.returns).toBe('number');
  });
});
