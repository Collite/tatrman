import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { Validator } from '../validator.js';
import type { ValidationDiagnostic } from '../validator.js';
import { resolveManifest } from '../manifest.js';
import { synthesizeMappings } from '../mapping-synthesizer.js';

function buildAndValidate(files: Record<string, string>) {
  const symbols = new ProjectSymbolTable();
  const manifestRoot = '/project';
  const manifest = resolveManifest(undefined, manifestRoot);

  for (const [path, contents] of Object.entries(files)) {
    const parsed = parseString(contents);
    const uri = `file://${manifestRoot}/${path}`;
    const schemaCode = parsed.ast!.schemaDirective?.schemaCode ?? 'er';
    const namespace = parsed.ast!.schemaDirective?.namespace ?? '';
    symbols.upsertDocument(uri, parsed.ast!, schemaCode, namespace);
    synthesizeMappings(symbols, uri, parsed.ast!);
  }

  const resolver = new Resolver(symbols);
  const validator = new Validator(symbols, resolver, manifest);

  const allDiags = validator.validateProject();
  return [...new Map(allDiags.map((d: ValidationDiagnostic) => [`${d.code}:${d.source.line}:${d.source.column}`, d])).values()];
}

describe('ttr/duplicate-mapping — entity', () => {
  it('inline entity + explicit er2db_entity for same name → error on both', async () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity artikl {
          mapping: { target: { table: db.dbo.QZBOZI_DF } },
          attributes: [ def attribute id { type: int, isKey: true } ]
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_entity artikl { entity: er.entity.artikl, target: { table: db.dbo.QZBOZI_DF } }
      `,
    });
    const dup = diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping);
    expect(dup).toHaveLength(2);
  });

  it('only inline entity (no explicit) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo {
          mapping: { target: { table: db.dbo.QZBOZI_DF } },
          attributes: [ def attribute id { type: int, isKey: true } ]
        }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });

  it('only explicit er2db_entity (no inline) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo { attributes: [ def attribute id { type: int, isKey: true } ] }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_entity foo { entity: er.entity.foo, target: { table: db.dbo.QZBOZI_DF } }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });
});

describe('ttr/duplicate-mapping — attribute', () => {
  it('inline attribute + explicit er2db_attribute → error on both', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo {
          attributes: [
            def attribute id { type: int, isKey: true, mapping: IDX }
          ]
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }
      `,
    });
    const dup = diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping);
    expect(dup).toHaveLength(2);
  });

  it('entity-level columns + explicit er2db_attribute for same attribute → error', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo {
          mapping: { target: { table: db.dbo.QZBOZI_DF }, columns: { id: IDX } },
          attributes: [ def attribute id { type: int, isKey: true } ]
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }
      `,
    });
    const dup = diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping);
    expect(dup.length).toBeGreaterThanOrEqual(2);
  });

  it('only inline attribute (no explicit) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo {
          attributes: [
            def attribute id { type: int, isKey: true, mapping: IDX }
          ]
        }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });

  it('only explicit er2db_attribute (no inline) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def entity foo {
          attributes: [
            def attribute id { type: int, isKey: true }
          ]
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_attribute foo.id { attribute: er.entity.foo.id, target: { column: db.dbo.QZBOZI_DF.IDX } }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });
});

describe('ttr/duplicate-mapping — relation', () => {
  it('inline relation + explicit er2db_relation → error on both', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }],
          mapping: db.dbo.fk_a_b
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_relation r { relation: er.entity.r, fk: db.dbo.fk_a_b }
      `,
    });
    const dup = diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping);
    expect(dup).toHaveLength(2);
  });

  it('only inline relation (no explicit) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }],
          mapping: db.dbo.fk_a_b
        }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });

  it('only explicit er2db_relation (no inline) → no duplicate-mapping diagnostic', () => {
    const diags = buildAndValidate({
      'er.ttr': `
        package p
        schema er
        def relation r {
          from: er.entity.a, to: er.entity.b,
          cardinality: { from: "0..*", to: "1" },
          join: [{ from: er.entity.a.x, to: er.entity.b.x }]
        }
      `,
      'map.ttr': `
        package p
        schema map
        def er2db_relation r { relation: er.entity.r, fk: db.dbo.fk_a_b }
      `,
    });
    expect(diags.filter((d: ValidationDiagnostic) => d.code === DiagnosticCode.DuplicateMapping)).toHaveLength(0);
  });
});