import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

// PD2 — `.ttrd` domain file grammar + DomainBlock AST.

describe('PD2 — .ttrd domain grammar', () => {
  it('parses a minimal domain with packages, empty entities', () => {
    const { ast, errors } = parseString(
      'package domains\ndomain accounting { packages: [ucetnictvi, obchodni_doklady] }',
      'file:///d/accounting.ttrd'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.domain?.kind).toBe('domainBlock');
    expect(ast?.domain?.name).toBe('accounting');
    expect(ast?.domain?.packages).toEqual(['ucetnictvi', 'obchodni_doklady']);
    expect(ast?.domain?.entities).toEqual([]);
  });

  it('parses description, tags, and entities', () => {
    const src = `package domains
domain accounting {
  description: "Účetnictví",
  tags: ["finance", "core"],
  packages: [ucetnictvi],
  entities: [artikl.er.entity.artikl]
}`;
    const { ast, errors } = parseString(src, 'file:///d/accounting.ttrd');
    expect(errors).toHaveLength(0);
    const d = ast?.domain;
    expect(d?.description).toBe('Účetnictví');
    expect(d?.tags).toEqual(['finance', 'core']);
    expect(d?.packages).toEqual(['ucetnictvi']);
    expect(d?.entities).toEqual(['artikl.er.entity.artikl']);
  });

  it('keeps a nested-package member as a single dotted string (not split)', () => {
    const { ast, errors } = parseString(
      'domain sales { packages: [prodeje.regional, prodeje] }',
      'file:///d/sales.ttrd'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.domain?.packages).toEqual(['prodeje.regional', 'prodeje']);
  });

  it('tolerates trailing commas (mirrors graphBlock)', () => {
    const src = `domain d {
  packages: [a, b,],
  entities: [a.er.entity.x,],
}`;
    const { ast, errors } = parseString(src, 'file:///d/d.ttrd');
    expect(errors).toHaveLength(0);
    expect(ast?.domain?.packages).toEqual(['a', 'b']);
    expect(ast?.domain?.entities).toEqual(['a.er.entity.x']);
  });

  it('an empty domain block parses with empty member arrays', () => {
    const { ast, errors } = parseString('domain empty { }', 'file:///d/empty.ttrd');
    expect(errors).toHaveLength(0);
    expect(ast?.domain?.name).toBe('empty');
    expect(ast?.domain?.packages).toEqual([]);
    expect(ast?.domain?.entities).toEqual([]);
  });

  it('carries source locations on the block and each member (for PD3 go-to-def)', () => {
    const { ast } = parseString(
      'domain d { packages: [a, b.c], entities: [a.er.entity.x] }',
      'file:///d/d.ttrd'
    );
    const d = ast!.domain!;
    expect(d.source.line).toBe(1);
    expect(d.packageSources).toHaveLength(2);
    expect(d.entitySources).toHaveLength(1);
    // The second package member `b.c` starts after `domain d { packages: [a, `.
    expect(d.packageSources![1].column).toBeGreaterThan(d.packageSources![0].column);
    // A member location spans only the member, not the whole block.
    expect(d.packageSources![0].offsetEnd).toBeLessThan(d.source.offsetEnd);
  });

  it('parser is permissive: a file with both a domain block and a def keeps both nodes (file-kind is a PD3 semantic concern)', () => {
    const { ast } = parseString(
      'domain d { packages: [a] }\ndef entity x { attributes: [def attribute id { type: int }] }',
      'file:///d/mixed.ttrd'
    );
    expect(ast?.domain?.name).toBe('d');
    expect(ast?.definitions.length).toBe(1);
  });

  it('domain/packages/entities remain usable as identifier fragments', () => {
    const { ast, errors } = parseString(
      'schema er namespace entity\ndef entity domain { attributes: [def attribute packages { type: int }] }',
      'file:///d/x.ttr'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.definitions[0].name).toBe('domain');
  });
});
