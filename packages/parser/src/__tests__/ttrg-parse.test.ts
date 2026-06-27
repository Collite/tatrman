import { describe, it, expect } from 'vitest';
import { parseString, parseFile } from '../index.js';
import { DiagnosticCode } from '../diagnostics.js';
import path from 'path';
import fs from 'fs/promises';

const samplesDir = path.resolve(__dirname, '../../../../samples');

async function getAllTtrgFiles(dir: string, excludeDirs: string[] = []): Promise<string[]> {
  const results: string[] = [];
  const entries = await fs.readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (excludeDirs.includes(entry.name)) continue;
      results.push(...await getAllTtrgFiles(fullPath, excludeDirs));
    } else if (entry.isFile() && entry.name.endsWith('.ttrg')) {
      results.push(fullPath);
    }
  }
  return results;
}

describe('C1 — .ttrg parsing', () => {
  it('.ttrg with graph block parses to Document with graph populated and empty definitions', () => {
    const result = parseString(
      `graph artikl_overview { model: er, objects: [er.entity.artikl] }`,
      'artikl_overview.ttrg'
    );
    expect(result.errors).toHaveLength(0);
    expect(result.ast).toBeDefined();
    expect(result.ast!.graph).toBeDefined();
    expect(result.ast!.graph!.name).toBe('artikl_overview');
    expect(result.ast!.graph!.schema).toBe('er');
    expect(result.ast!.graph!.objects).toEqual(['er.entity.artikl']);
    expect(result.ast!.definitions).toHaveLength(0);
  });

  it('.ttrg layout with unquoted dotted-id node keys parses into graph.layout.nodes', () => {
    const result = parseString(
      `graph artikl {
        model: er,
        objects: [er.entity.artikl, er.entity.dobropis],
        layout: { nodes: { er.entity.artikl: { x: 320, y: 180 }, er.entity.dobropis: { x: 500, y: 180 } } }
      }`,
      'artikl.ttrg'
    );
    expect(result.errors).toHaveLength(0);
    expect(result.ast!.graph!.layout).toBeDefined();
    expect(result.ast!.graph!.layout!.nodes).toBeDefined();
    const nodes = result.ast!.graph!.layout!.nodes;
    expect(nodes['er.entity.artikl']).toEqual({ x: 320, y: 180 });
    expect(nodes['er.entity.dobropis']).toEqual({ x: 500, y: 180 });
  });

  it('.ttrg containing only a schema directive emits WrongFileKind', () => {
    const result = parseString(`model er schema entity`, 'test.ttrg');
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeDefined();
    expect(wrongKind!.severity).toBe('error');
  });

  it('.ttrg containing only defs (no graph block) emits WrongFileKind', () => {
    const result = parseString(
      `def entity artikl { attributes: [def attribute id { type: int }] }`,
      'test.ttrg'
    );
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeDefined();
    expect(wrongKind!.severity).toBe('error');
  });

  it('.ttrg with graph block and no definitions parses without WrongFileKind', () => {
    const result = parseString(`graph test { model: er, objects: [] }`, 'test.ttrg');
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeUndefined();
  });

  it('.ttrg with graph block and top-level definitions emits WrongFileKind', () => {
    const result = parseString(
      `graph test { model: er }
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'test.ttrg'
    );
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeDefined();
    expect(wrongKind!.severity).toBe('error');
  });

  it('.ttrm file with graph block and definitions emits WrongFileKind (graph + defs together)', () => {
    const result = parseString(
      `graph test { model: er }
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'test.ttrm'
    );
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeDefined();
    expect(wrongKind!.severity).toBe('error');
  });

  it('.ttrm file with graph block (no definitions) parses without WrongFileKind', () => {
    const result = parseString(
      `graph test { model: er }`,
      'test.ttrm'
    );
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeUndefined();
  });

  it('.ttrg with invalid schema token emits parse error', () => {
    const result = parseString(`graph test { model: xyz }`, 'test.ttrg');
    expect(result.errors.length).toBeGreaterThan(0);
    expect(result.errors.some((e) => e.code === DiagnosticCode.ParseError)).toBe(true);
  });

  it('.ttrg with all five schema codes parses successfully', () => {
    for (const schema of ['db', 'er', 'binding', 'query', 'cnc']) {
      const result = parseString(`graph test { model: ${schema}, objects: [] }`, 'test.ttrg');
      expect(result.errors, `schema ${schema} should parse`).toHaveLength(0);
      expect(result.ast!.graph!.schema).toBe(schema);
    }
  });

  it('.ttrg with description and tags parses', () => {
    const result = parseString(
      `graph artikl { description: "Artikl prehled", tags: ["main", "report"], model: er, objects: [] }`,
      'test.ttrg'
    );
    expect(result.errors).toHaveLength(0);
    expect(result.ast!.graph!.description).toBe('Artikl prehled');
    expect(result.ast!.graph!.tags).toEqual(['main', 'report']);
  });

  it('all existing .ttrg fixture files parse without errors', async () => {
    const files = await getAllTtrgFiles(samplesDir, ['broken']);
    for (const file of files) {
      const result = await parseFile(file);
      expect(result.errors, `Errors in ${file}: ${result.errors.map((e) => e.message).join(', ')}`).toHaveLength(0);
    }
  });
});