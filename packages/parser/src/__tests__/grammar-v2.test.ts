import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import path from 'path';
import fs from 'fs/promises';

const SAMPLES_ROOT = path.resolve(__dirname, '../../../../samples');

async function getAllTtrFiles(dir: string, excludeDirs: string[] = []): Promise<string[]> {
  const results: string[] = [];
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return results;
  }
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (excludeDirs.includes(entry.name)) continue;
      results.push(...await getAllTtrFiles(fullPath, excludeDirs));
    } else if (entry.isFile() && entry.name.endsWith('.ttr')) {
      results.push(fullPath);
    }
  }
  return results;
}

describe('grammar v1.1 — package / import / graph', () => {
  it('parses a package declaration', () => {
    const result = parseString(
      'package billing.invoicing\n' +
      'schema er namespace entity\n' +
      'def entity X {}\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast).toBeDefined();
    expect(result.ast?.definitions).toHaveLength(1);
    expect(result.ast?.definitions[0].name).toBe('X');
  });

  it('parses named and wildcard imports', () => {
    const result = parseString(
      'package a.b\n' +
      'import x.y.*\n' +
      'import p.q.r.S\n' +
      'schema er namespace entity\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast).toBeDefined();
  });

  it('parses a graph block', () => {
    const result = parseString(
      'package a.b\n' +
      'graph my_view { schema: er, objects: [a.b.er.entity.X] }\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast).toBeDefined();
  });

  it('every existing v1 sample still parses without errors', async () => {
    const v1Dirs = [
      path.join(SAMPLES_ROOT, 'v1-metadata'),
      path.join(SAMPLES_ROOT, 'v1-mini'),
      path.join(SAMPLES_ROOT, 'builtin'),
    ];
    const samples: string[] = [];
    for (const dir of v1Dirs) {
      samples.push(...await getAllTtrFiles(dir, ['.git', 'node_modules', '.modeler']));
    }
    expect(samples.length).toBeGreaterThan(0);
    for (const f of samples) {
      const src = await fs.readFile(f, 'utf-8');
      const r = parseString(src, f);
      expect(r.errors, `${f} should parse cleanly`).toEqual([]);
    }
  });
});