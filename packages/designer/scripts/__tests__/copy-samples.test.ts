import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { copySamples } from '../copy-samples';

describe('copySamples', () => {
  const repoRoot = path.resolve(__dirname, '../../../..');
  const samplesSrc = path.join(repoRoot, 'samples/v1-metadata');
  let tmpDest: string;

  beforeEach(() => {
    tmpDest = fs.mkdtempSync(path.join(os.tmpdir(), 'copy-samples-'));
  });

  afterEach(() => {
    fs.rmSync(tmpDest, { recursive: true, force: true });
  });

  it('copies all non-hidden files and writes index.json', () => {
    const result = copySamples(samplesSrc, tmpDest);

    const got = fs.readdirSync(tmpDest).sort();
    expect(got).toContain('modeler.toml');
    expect(got).toContain('db.ttr');
    expect(got).toContain('er.ttr');
    expect(got).toContain('index.json');
    expect(got).toContain('map.ttr');
    expect(got).toContain('query.ttr');

    const manifest: string[] = JSON.parse(
      fs.readFileSync(path.join(tmpDest, 'index.json'), 'utf-8')
    );
    expect(manifest).not.toContain('index.json');
    expect(manifest.every((p: string) => !p.startsWith('.'))).toBe(true);
    expect(manifest.every((p: string) => !p.startsWith('.modeler'))).toBe(true);
    expect(result).toEqual(manifest);
  });

  it('modeler.toml content round-trips byte-for-byte', () => {
    copySamples(samplesSrc, tmpDest);
    const expected = fs.readFileSync(path.join(samplesSrc, 'modeler.toml'), 'utf-8');
    const actual = fs.readFileSync(path.join(tmpDest, 'modeler.toml'), 'utf-8');
    expect(actual).toBe(expected);
  });

  it('returns manifest of copied files in copy order', () => {
    const manifest = copySamples(samplesSrc, tmpDest);
    const expectedCount = fs
      .readdirSync(samplesSrc, { withFileTypes: true })
      .filter((d) => d.isFile() && !d.name.startsWith('.'))
      .length;
    expect(manifest).toHaveLength(expectedCount);
    expect(manifest).toContain('modeler.toml');
    expect(manifest).toContain('db.ttr');
    expect(manifest).toContain('er.ttr');
  });
});