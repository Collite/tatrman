import { describe, it, expect } from 'vitest';
import { parseFile } from '@modeler/parser';
import path from 'path';
import { readdirSync } from 'fs';

const sampleDir = path.resolve(__dirname, '../../../samples/2.1');

describe('samples/2.1 — inline mappings parse cleanly', () => {
  for (const name of readdirSync(sampleDir).filter((n) => n.endsWith('.ttr'))) {
    it(`${name} has zero parse errors`, async () => {
      const parsed = await parseFile(path.join(sampleDir, name));
      expect(parsed.errors, `${name} parse errors: ${parsed.errors.map((e) => e.message).join(', ')}`).toEqual([]);
    });
  }
});