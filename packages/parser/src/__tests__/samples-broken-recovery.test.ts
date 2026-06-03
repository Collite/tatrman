import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { parseString } from '../walker.js';
import { DiagnosticCode } from '../diagnostics.js';

const samplesBrokenDir = join(__dirname, '../../../../samples/broken');

describe('samples/broken/*.ttr → parse-recovery-info', () => {
  const files = readdirSync(samplesBrokenDir)
    .filter((f) => f.endsWith('.ttr'));

  for (const file of files) {
    it(`${file} produces ≥1 parse-error and ≥1 parse-recovery-info`, () => {
      const content = readFileSync(join(samplesBrokenDir, file), 'utf-8');
      const result = parseString(content, file);
      const errors = result.errors.filter(e => e.code === DiagnosticCode.ParseError);
      const infos = result.errors.filter(e => e.code === DiagnosticCode.ParseRecoveryInfo);
      expect(errors.length, `${file}: expected ≥1 parse-error`).toBeGreaterThanOrEqual(1);
      expect(infos.length, `${file}: expected ≥1 parse-recovery-info`).toBeGreaterThanOrEqual(1);
    });
  }
});