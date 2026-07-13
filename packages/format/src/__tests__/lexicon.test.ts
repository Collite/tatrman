// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';
import { format } from '../index.js';

const here = dirname(fileURLToPath(import.meta.url));
const fixture = (name: string) =>
  readFileSync(resolve(here, '../../../../tests/conformance/fixtures', name), 'utf-8');

/** Deep-strip `source`/trivia so two ASTs compare for semantic equality. */
function stripMeta(value: unknown): unknown {
  if (value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(stripMeta);
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    if (k === 'source' || k === 'leadingTrivia' || k === 'trailingTrivia') continue;
    out[k] = stripMeta(v);
  }
  return out;
}

// RG-P4.S1.T6 — the printer emits `for:`/`forms:`/`match:`/`text:` on the lexicon
// def kinds and the inline `lexicon: { … }` block on carriers, without corrupting
// the vocabulary. Parse → emit → reparse → deep-equal AST (modulo trivia).
describe('formatter — lexicon round-trip (grammar 4.4)', () => {
  for (const name of ['62-lexicon.ttrm', '63-lexicon-inline.ttrm']) {
    it(`round-trips ${name}`, () => {
      const src = fixture(name);
      const uri = `file:///${name}`;
      const before = parseString(src, uri);
      expect(before.errors.filter((e) => e.code === 'ttr/parse-error')).toEqual([]);
      const formatted = format(src, uri);
      const after = parseString(formatted, uri);
      expect(after.errors.filter((e) => e.code === 'ttr/parse-error')).toEqual([]);
      expect(stripMeta(after.ast!.definitions)).toEqual(stripMeta(before.ast!.definitions));
    });

    it(`is idempotent for ${name}`, () => {
      const src = fixture(name);
      const uri = `file:///${name}`;
      const once = format(src, uri);
      const twice = format(once, uri);
      expect(twice).toBe(once);
    });
  }
});
