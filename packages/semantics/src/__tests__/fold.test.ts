// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { foldForCollision } from '../lexicon/fold.js';

interface ParityTable {
  cases: Array<[string, string]>;
}

const parity = JSON.parse(
  readFileSync(fileURLToPath(new URL('../lexicon/fold-parity.json', import.meta.url)), 'utf8')
) as ParityTable;

describe('foldForCollision — the MH collision fold (contracts §1)', () => {
  it('has the full parity table', () => {
    expect(parity.cases.length).toBe(12);
  });

  for (const [input, expected] of parity.cases) {
    it(`folds ${JSON.stringify(input)} → ${JSON.stringify(expected)}`, () => {
      expect(foldForCollision(input)).toBe(expected);
    });
  }

  it('is idempotent', () => {
    for (const [input] of parity.cases) {
      expect(foldForCollision(foldForCollision(input))).toBe(foldForCollision(input));
    }
  });

  it('collapses internal whitespace and diacritics to one key', () => {
    expect(foldForCollision('Kamenná  prodejna ')).toBe(foldForCollision('kamenna prodejna'));
  });

  it('keeps precomposed letters without a canonical decomposition (Đ/Ł)', () => {
    // Only *combining* marks are stripped — `Normalization.fold` behaves the same way.
    expect(foldForCollision('Łódź')).toBe('łodz');
  });
});
