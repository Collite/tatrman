import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintProj } from './helpers.js';

// Ported from packages/semantics/__tests__/duplicate-mapping.test.ts — driving
// the @modeler/lint project runner instead of the deleted Validator.

function buildAndValidate(files: Record<string, string>) {
  const projectFiles = Object.entries(files).map(([path, src]) => ({
    uri: `file:///project/${path}`,
    src,
  }));
  const byUri = lintProj(projectFiles, { projectRoot: '/project' });
  const all = [...byUri.values()].flat();
  return [...new Map(all.map((d) => [`${d.code}:${d.source.line}:${d.source.column}`, d])).values()];
}

function dupCount(files: Record<string, string>): number {
  return buildAndValidate(files).filter((d) => d.code === DiagnosticCode.DuplicateBinding).length;
}

describe('ttr/duplicate-mapping — entity', () => {
  it('inline entity + explicit er2db_entity → error on both', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity artikl {\n binding: { target: { table: db.dbo.QZBOZI_DF } },\n attributes: [ def attribute id { type: int, isKey: true } ]\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }`,
      })
    ).toBe(2);
  });

  it('only inline entity → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo {\n binding: { target: { table: db.dbo.QZBOZI_DF } },\n attributes: [ def attribute id { type: int, isKey: true } ]\n}`,
      })
    ).toBe(0);
  });

  it('only explicit er2db_entity → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo { attributes: [ def attribute id { type: int, isKey: true } ] }`,
        'map.ttr': `package p\nschema binding\ndef er2db_entity foo { entity: er.entity.foo, target: { table: db.dbo.QZBOZI_DF } }`,
      })
    ).toBe(0);
  });
});

describe('ttr/duplicate-mapping — attribute', () => {
  it('inline attribute + explicit er2db_attribute → error on both', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo {\n attributes: [\n def attribute id { type: int, isKey: true, binding: IDX }\n ]\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }`,
      })
    ).toBe(2);
  });

  it('entity-level columns + explicit er2db_attribute → error', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo {\n binding: { target: { table: db.dbo.QZBOZI_DF }, columns: { id: IDX } },\n attributes: [ def attribute id { type: int, isKey: true } ]\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }`,
      })
    ).toBeGreaterThanOrEqual(2);
  });

  it('only inline attribute → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo {\n attributes: [\n def attribute id { type: int, isKey: true, binding: IDX }\n ]\n}`,
      })
    ).toBe(0);
  });

  it('only explicit er2db_attribute → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef entity foo {\n attributes: [\n def attribute id { type: int, isKey: true }\n ]\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }`,
      })
    ).toBe(0);
  });
});

describe('ttr/duplicate-mapping — relation', () => {
  it('inline relation + explicit er2db_relation → error on both', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef relation r {\n from: er.entity.a, to: er.entity.b,\n cardinality: { from: "0..*", to: "1" },\n join: [{ from: er.entity.a.x, to: er.entity.b.x }],\n binding: db.dbo.fk_a_b\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_relation r { relation: er.entity.r, fk: db.dbo.fk_a_b }`,
      })
    ).toBe(2);
  });

  it('only inline relation → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef relation r {\n from: er.entity.a, to: er.entity.b,\n cardinality: { from: "0..*", to: "1" },\n join: [{ from: er.entity.a.x, to: er.entity.b.x }],\n binding: db.dbo.fk_a_b\n}`,
      })
    ).toBe(0);
  });

  it('only explicit er2db_relation → no duplicate-mapping', () => {
    expect(
      dupCount({
        'er.ttr': `package p\nschema er\ndef relation r {\n from: er.entity.a, to: er.entity.b,\n cardinality: { from: "0..*", to: "1" },\n join: [{ from: er.entity.a.x, to: er.entity.b.x }]\n}`,
        'map.ttr': `package p\nschema binding\ndef er2db_relation r { relation: er.entity.r, fk: db.dbo.fk_a_b }`,
      })
    ).toBe(0);
  });
});
