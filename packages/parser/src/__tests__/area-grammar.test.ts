import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { AreaDef } from '../ast.js';

// v3.0 (Phase 0 Stage B) — `def area` replaces the v2.3 `.ttrd` domain block.
// Areas are now plain definitions living in ordinary model files.

function areaOf(src: string, uri = 'file:///d/a.ttrm'): { area: AreaDef | undefined; errors: number } {
  const { ast, errors } = parseString(src, uri);
  const area = ast?.definitions.find((d): d is AreaDef => d.kind === 'area');
  return { area, errors: errors.length };
}

describe('v3.0 — def area grammar', () => {
  it('parses a minimal area with packages, empty entities', () => {
    const { area, errors } = areaOf('package domains\ndef area accounting { packages: [ucetnictvi, obchodni_doklady] }');
    expect(errors).toBe(0);
    expect(area?.kind).toBe('area');
    expect(area?.name).toBe('accounting');
    expect(area?.packages).toEqual(['ucetnictvi', 'obchodni_doklady']);
    expect(area?.entities).toEqual([]);
  });

  it('parses description, tags, and entities', () => {
    const src = `package domains
def area accounting {
  description: "Účetnictví",
  tags: ["finance", "core"],
  packages: [ucetnictvi],
  entities: [artikl.er.entity.artikl]
}`;
    const { area, errors } = areaOf(src);
    expect(errors).toBe(0);
    expect(area?.description?.value).toBe('Účetnictví');
    expect(area?.tags).toEqual(['finance', 'core']);
    expect(area?.packages).toEqual(['ucetnictvi']);
    expect(area?.entities).toEqual(['artikl.er.entity.artikl']);
  });

  it('keeps a nested-package member as a single dotted string (not split)', () => {
    const { area, errors } = areaOf('def area sales { packages: [prodeje.regional, prodeje] }');
    expect(errors).toBe(0);
    expect(area?.packages).toEqual(['prodeje.regional', 'prodeje']);
  });

  it('tolerates trailing commas', () => {
    const src = `def area d {
  packages: [a, b,],
  entities: [a.er.entity.x,],
}`;
    const { area, errors } = areaOf(src);
    expect(errors).toBe(0);
    expect(area?.packages).toEqual(['a', 'b']);
    expect(area?.entities).toEqual(['a.er.entity.x']);
  });

  it('an empty area parses with empty member arrays', () => {
    const { area, errors } = areaOf('def area empty { }');
    expect(errors).toBe(0);
    expect(area?.name).toBe('empty');
    expect(area?.packages).toEqual([]);
    expect(area?.entities).toEqual([]);
  });

  it('carries source locations on the def and each member (for go-to-def)', () => {
    const { ast } = parseString('def area d { packages: [a, b.c], entities: [a.er.entity.x] }', 'file:///d/d.ttrm');
    const d = ast!.definitions.find((x): x is AreaDef => x.kind === 'area')!;
    expect(d.source.line).toBe(1);
    expect(d.packageSources).toHaveLength(2);
    expect(d.entitySources).toHaveLength(1);
    expect(d.packageSources![1].column).toBeGreaterThan(d.packageSources![0].column);
    expect(d.packageSources![0].offsetEnd).toBeLessThan(d.source.offsetEnd);
  });

  it('an area coexists with other defs in the same file (no file-kind error)', () => {
    const { ast, errors } = parseString(
      'def area d { packages: [a] }\ndef entity x { attributes: [def attribute id { type: int }] }',
      'file:///d/mixed.ttrm'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.definitions.filter((x) => x.kind === 'area')).toHaveLength(1);
    expect(ast?.definitions.filter((x) => x.kind === 'entity')).toHaveLength(1);
  });

  it('area/packages/entities remain usable as identifier fragments', () => {
    const { ast, errors } = parseString(
      'schema er namespace entity\ndef entity area { attributes: [def attribute packages { type: int }] }',
      'file:///d/x.ttrm'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.definitions[0].name).toBe('area');
  });

  it('a bare top-level `domain { ... }` block is now a parse error', () => {
    const { errors } = parseString('domain accounting { packages: [a] }', 'file:///d/legacy.ttrm');
    expect(errors.length).toBeGreaterThan(0);
  });
});
