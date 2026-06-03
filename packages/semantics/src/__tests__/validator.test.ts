import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { Validator } from '../validator.js';
import { resolveManifest } from '../manifest.js';

function setup(uri: string, src: string, opts?: { strict?: boolean }) {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
  const namespace = ast.schemaDirective?.namespace ?? '';
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument(uri, ast, schemaCode, namespace);
  const resolver = new Resolver(symbols);
  const manifest = resolveManifest(
    { lint: { strict: opts?.strict ?? false } },
    ''
  );
  const validator = new Validator(symbols, resolver, manifest);
  return { ast, symbols, resolver, validator };
}

describe('Validator.validateDocument', () => {
  it('emits RequiredPropertyMissing on entity with no attributes', () => {
    const { validator, ast } = setup(
      'er.ttr',
      `schema er namespace entity
       def entity empty { description: "no attrs" }`
    );
    const diags = validator.validateDocument('er.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.RequiredPropertyMissing)).toBe(true);
  });

  it('emits EntityAttributeNotFound when nameAttribute points at a missing attr', () => {
    const { validator, ast } = setup(
      'er.ttr',
      `schema er namespace entity
       def entity artikl {
         attributes: [def attribute id { type: int }]
         nameAttribute: ghost
       }`
    );
    const diags = validator.validateDocument('er.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.EntityAttributeNotFound)).toBe(true);
  });

  it('emits PrimaryKeyColumnNotFound when pk column does not exist', () => {
    const { validator, ast } = setup(
      'db.ttr',
      `schema db namespace dbo
       def table orders {
         columns: [def column id { type: int }]
         primaryKey: ["bogus"]
       }`
    );
    const diags = validator.validateDocument('db.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.PrimaryKeyColumnNotFound)).toBe(true);
  });

  it('emits no diagnostics for a well-formed entity', () => {
    const { validator, ast } = setup(
      'er.ttr',
      `schema er namespace entity
       def entity artikl {
         attributes: [def attribute id { type: int }]
       }`
    );
    expect(validator.validateDocument('er.ttr', ast)).toHaveLength(0);
  });
});

describe('Validator.validateReferences', () => {
  it('emits UnresolvedReference on a bad dotted ref (warning by default)', () => {
    const { validator, ast } = setup(
      'er.ttr',
      `schema er namespace entity
       def entity artikl {
         attributes: [def attribute id { type: int }]
       }
       def er2cnc_role x {
         entity: er.entity.nope
         role: fact
       }`
    );
    const diags = validator.validateReferences('er.ttr', ast);
    const bad = diags.find((d) => d.code === DiagnosticCode.UnresolvedReference);
    expect(bad).toBeDefined();
    expect(bad!.severity).toBe('warning');
  });

  it('respects lint.strict (warning → error)', () => {
    const { validator, ast } = setup(
      'er.ttr',
      `schema er namespace entity
       def entity artikl {
         attributes: [def attribute id { type: int }]
       }
       def er2cnc_role x {
         entity: er.entity.nope
         role: fact
       }`,
      { strict: true }
    );
    const diags = validator.validateReferences('er.ttr', ast);
    const bad = diags.find((d) => d.code === DiagnosticCode.UnresolvedReference);
    expect(bad).toBeDefined();
    expect(bad!.severity).toBe('error');
  });
});

describe('Validator.validateProject', () => {
  it('emits DuplicateDefinition when the same qname appears in two documents', () => {
    const aSrc = `schema er namespace entity
                  def entity twin { attributes: [def attribute id { type: int }] }`;
    const bSrc = `schema er namespace entity
                  def entity twin { attributes: [def attribute id { type: int }] }`;
    const aAst = parseString(aSrc, 'a.ttr').ast!;
    const bAst = parseString(bSrc, 'b.ttr').ast!;
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument('a.ttr', aAst, 'er', 'entity');
    symbols.upsertDocument('b.ttr', bAst, 'er', 'entity');
    const validator = new Validator(symbols, new Resolver(symbols), resolveManifest(undefined, ''));
    const diags = validator.validateProject();
    expect(diags.filter((d) => d.code === DiagnosticCode.DuplicateDefinition).length).toBeGreaterThanOrEqual(2);
  });
});

describe('search block validation', () => {
  function searchSetup(src: string) {
    const uri = 'test.ttr';
    const ast = parseString(src, uri).ast!;
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const symbols = new ProjectSymbolTable();
    symbols.upsertDocument(uri, ast, schemaCode, namespace);
    const resolver = new Resolver(symbols);
    const manifest = resolveManifest(undefined, '');
    const validator = new Validator(symbols, resolver, manifest);
    return { validator, ast, symbols, resolver };
  }

  it('emits FuzzyWithoutSearchable warning when fuzzy: true but searchable absent', () => {
    const { validator, ast } = searchSetup(
      'def entity E { attributes: [def attribute A { type: text, search { fuzzy: true } }] }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.FuzzyWithoutSearchable && d.severity === 'warning')).toBe(true);
  });

  it('emits no FuzzyWithoutSearchable when searchable: true and fuzzy: true', () => {
    const { validator, ast } = searchSetup(
      'def entity E { search { searchable: true, fuzzy: true } }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.FuzzyWithoutSearchable)).toBe(false);
  });

  it('emits FuzzyWithoutSearchable warning when searchable: false and fuzzy: true', () => {
    const { validator, ast } = searchSetup(
      'def entity E { search { searchable: false, fuzzy: true } }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.FuzzyWithoutSearchable && d.severity === 'warning')).toBe(true);
  });

  it('emits DuplicateSearchProperty error when keywords appears twice', () => {
    const { validator, ast } = searchSetup(
      'def entity E { search { keywords: { cs: ["a"] }, keywords: { en: ["b"] } } }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.DuplicateSearchProperty && d.severity === 'error')).toBe(true);
  });

  it('emits no new diagnostics on a clean search block on table', () => {
    const { validator, ast } = searchSetup(
      'def table T { search { searchable: true, fuzzy: true } }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.FuzzyWithoutSearchable || d.code === DiagnosticCode.DuplicateSearchProperty)).toBe(false);
  });

  it('emits no new diagnostics on a clean search block on column', () => {
    const { validator, ast } = searchSetup(
      'def table T { columns: [def column C { type: varchar, search { searchable: true } }] }'
    );
    const diags = validator.validateDocument('test.ttr', ast);
    expect(diags.some((d) => d.code === DiagnosticCode.FuzzyWithoutSearchable || d.code === DiagnosticCode.DuplicateSearchProperty)).toBe(false);
  });
});
