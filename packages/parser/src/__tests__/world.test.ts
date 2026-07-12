// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseString } from '../index.js';
import type { WorldDef } from '../ast.js';

// v4.1 — world model (ttr-metadata M0). Mirrors the Kotlin WorldParseSpec.
const FIXTURES = join(__dirname, '../../../../tests/conformance/fixtures');

function worldOf(src: string, uri = 'file:///w/w.ttrm'): { world: WorldDef | undefined; errors: number } {
  const { ast, errors } = parseString(src, uri);
  const world = ast?.definitions.find((d): d is WorldDef => d.kind === 'world');
  return { world, errors: errors.length };
}

describe('v4.1 — world model grammar', () => {
  it('parses the golden fixture (57-world.ttrm) roster', () => {
    const src = readFileSync(join(FIXTURES, '57-world.ttrm'), 'utf-8');
    const { ast, errors } = parseString(src, 'file:///w/57.ttrm');
    expect(errors.length).toBe(0);
    expect(ast?.modelDirective?.modelCode).toBe('world');

    const worlds = ast!.definitions.filter((d): d is WorldDef => d.kind === 'world');
    expect(worlds).toHaveLength(1);
    const dev = worlds[0];
    expect(dev.name).toBe('dev');
    expect(dev.engines).toHaveLength(2);
    expect(dev.executors).toHaveLength(1);
    expect(dev.storages).toHaveLength(3);

    const erpDb = dev.storages.find((s) => s.name === 'erp_db')!;
    expect(erpDb.hosts).toEqual(['erp']);
    expect(erpDb.via).toBe('erp_pg');

    const staging = dev.storages.filter((s) => s.staging === true);
    expect(staging).toHaveLength(1);
    expect(staging[0].name).toBe('stage');

    const files = dev.storages.find((s) => s.name === 'files')!;
    expect(files.schemas).toHaveLength(1);
    expect(files.schemas[0].name).toBe('sales_csv');
    expect(files.schemas[0].fields.map((f) => f.name)).toEqual(['customer', 'region', 'amount']);

    const erpPg = dev.engines.find((e) => e.name === 'erp_pg')!;
    expect(erpPg.type).toBe('postgres');
    expect(erpPg.version).toBe('16');
    expect(Object.keys(erpPg.manifest)).toContain('extensions');
  });

  it('parses the extends fixture (58-world-extends.ttrm)', () => {
    const src = readFileSync(join(FIXTURES, '58-world-extends.ttrm'), 'utf-8');
    const { world, errors } = worldOf(src);
    expect(errors).toBe(0);
    expect(world?.engines[0].extends).toBe('acme.types.postgres16');
    const storage = world?.storages[0];
    expect(storage?.extends).toBe('acme.types.scratch_dir');
    expect(storage?.staging).toBe(true);
  });

  it('gives WorldDef an accurate multi-line source location', () => {
    const src = 'package acme.worlds\nmodel world\n\ndef world dev {\n  def engine e { type: postgres }\n}\n';
    const { world } = worldOf(src);
    expect(world).toBeDefined();
    // `def world` starts on line 4 (1-indexed) at column 0 (0-indexed).
    expect(world!.source.line).toBe(4);
    expect(world!.source.column).toBe(0);
    expect(world!.source.endLine).toBeGreaterThanOrEqual(6);
  });
});
