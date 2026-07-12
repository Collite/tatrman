// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { parseString } from '../walker.js';

const samplesDir = join(__dirname, '../../../../samples');

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name === '.modeler') continue;
    const f = join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(f));
    else if (e.name.endsWith('.ttrm') || e.name.endsWith('.ttrg')) out.push(f);
  }
  return out;
}

// Every file under the migrated v1.1 samples must parse with zero errors.
for (const sample of ['v1.1-mini', 'v1.1-metadata']) {
  const dir = join(samplesDir, sample);
  describe(`samples/${sample} parses cleanly`, () => {
    const files = existsSync(dir) ? walk(dir) : [];

    it('has files to check', () => {
      expect(files.length, `no .ttrm/.ttrg files found under ${sample}`).toBeGreaterThan(0);
    });

    for (const file of files) {
      const rel = file.slice(samplesDir.length + 1);
      it(`${rel} parses with 0 errors`, () => {
        const result = parseString(readFileSync(file, 'utf-8'), file);
        const errors = result.errors.filter(e => e.severity === 'error');
        expect(errors, `${rel}: ${errors.map(e => e.message).join('; ')}`).toHaveLength(0);
      });
    }
  });
}
