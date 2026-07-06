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

describe('formatter — semantics block round-trip (grammar 4.2)', () => {
  // T2.6 — parse the golden fixtures → emit → reparse → deep-equal AST modulo
  // trivia. Proves the printer emits `semantics: { … }` on every attachment kind
  // without corrupting the block.
  for (const name of ['59-semantics.ttrm', '60-semantics-db.ttrm']) {
    it(`round-trips ${name}`, () => {
      const src = fixture(name);
      const out = format(src, name);
      const a = parseString(src, name);
      const b = parseString(out, name);
      expect(b.errors.filter((e) => e.severity === 'error')).toEqual([]);
      expect(stripMeta(b.ast)).toEqual(stripMeta(a.ast));
    });

    it(`is idempotent on ${name}`, () => {
      const once = format(fixture(name), name);
      expect(format(once, name)).toBe(once);
    });
  }
});
