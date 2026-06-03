import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { parseString } from '@modeler/parser';
import { formatDocument, DEFAULT_FORMAT_CONFIG } from '../formatter/format.js';

const samplesRoot = resolve(__dirname, '../../../../samples');

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir)) {
    if (e.startsWith('.')) continue;
    const f = join(dir, e);
    if (statSync(f).isDirectory()) out.push(...walk(f));
    else if (e.endsWith('.ttr') || e.endsWith('.ttrg')) out.push(f);
  }
  return out;
}

const files = walk(join(samplesRoot, 'v1.1-mini')).concat(walk(join(samplesRoot, 'v1.1-metadata')));

describe('formatter — v1.1 samples', () => {
  for (const file of files) {
    const rel = file.slice(samplesRoot.length + 1);
    it(`formats ${rel} → parses clean & idempotent`, () => {
      const src = readFileSync(file, 'utf-8');
      const r0 = parseString(src, file);
      if (!r0.ast) { expect.fail(`sample did not parse: ${rel}`); return; }

      const f1 = formatDocument(r0.ast, src, DEFAULT_FORMAT_CONFIG);
      const r1 = parseString(f1, file);
      expect(r1.errors.filter((e) => e.severity === 'error'), `formatted output has parse errors: ${JSON.stringify(r1.errors.slice(0, 3))}`).toEqual([]);
      expect(r1.ast, 'formatted output did not parse').toBeTruthy();

      // Idempotent: a second format makes no further change.
      const f2 = formatDocument(r1.ast!, f1, DEFAULT_FORMAT_CONFIG);
      expect(f2, 'formatter is not idempotent').toBe(f1);
    });
  }
});
