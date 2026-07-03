import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { scanCrossReferences, insertImports } from '../index.js';

// NOTE: projectSymbols are fed exactly as runMigration builds them — qnames are
// UNQUALIFIED (`<schema>.<namespace>.<def>`) with the package in `packageName`.
// A v1 source file (pre-migration) has no package, so its references are bare.
describe('scanCrossReferences', () => {
  function makeAst(content: string, file = '/test/file.ttrm') {
    return parseString(content, file).ast!;
  }

  function symbols(...entries: { qname: string; packageName: string; schemaCode: string }[]) {
    return entries;
  }

  it('one reference → named import with the real package', () => {
    const ast = makeAst(`model er schema entity
def entity foo { nameAttribute: produkt }`);
    const result = scanCrossReferences(ast, 'pkgA', symbols(
      { qname: 'er.entity.produkt', packageName: 'pkgB', schemaCode: 'er' },
    ), 3);
    expect(result.specs).toHaveLength(1);
    expect(result.specs[0]).toMatchObject({
      packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'produkt', isWildcard: false,
    });
    // The rendered line uses the real package, not the schema.
    const rendered = insertImports(`model er schema entity\n`, result.specs);
    expect(rendered).toContain('import pkgB.er.entity.produkt');
  });

  it('≥ wildcard-threshold distinct refs to same package → wildcard import', () => {
    const ast = makeAst(`model er schema entity
def entity foo { nameAttribute: x }
def entity baz { nameAttribute: y }
def entity qux { nameAttribute: z }`);
    const result = scanCrossReferences(ast, 'pkgA', symbols(
      { qname: 'er.entity.x', packageName: 'pkgB', schemaCode: 'er' },
      { qname: 'er.entity.y', packageName: 'pkgB', schemaCode: 'er' },
      { qname: 'er.entity.z', packageName: 'pkgB', schemaCode: 'er' },
    ), 3);
    expect(result.specs).toHaveLength(1);
    expect(result.specs[0]).toMatchObject({ packageName: 'pkgB', isWildcard: true });
  });

  it('below threshold → named imports', () => {
    const ast = makeAst(`model er schema entity
def entity foo { nameAttribute: x }
def entity baz { nameAttribute: y }`);
    const result = scanCrossReferences(ast, 'pkgA', symbols(
      { qname: 'er.entity.x', packageName: 'pkgB', schemaCode: 'er' },
      { qname: 'er.entity.y', packageName: 'pkgB', schemaCode: 'er' },
    ), 3);
    expect(result.specs).toHaveLength(2);
    expect(result.specs.every(s => !s.isWildcard && s.packageName === 'pkgB')).toBe(true);
  });

  it('--wildcard-threshold flips named↔wildcard boundary', () => {
    const ast = makeAst(`model er schema entity
def entity foo { nameAttribute: x }
def entity baz { nameAttribute: y }`);
    const syms = symbols(
      { qname: 'er.entity.x', packageName: 'pkgB', schemaCode: 'er' },
      { qname: 'er.entity.y', packageName: 'pkgB', schemaCode: 'er' },
    );
    expect(scanCrossReferences(ast, 'pkgA', syms, 3).specs.every(s => !s.isWildcard)).toBe(true);

    const at2 = scanCrossReferences(ast, 'pkgA', syms, 2);
    expect(at2.specs).toHaveLength(1);
    expect(at2.specs[0].isWildcard).toBe(true);
  });

  it('same-package references → no import', () => {
    const ast = makeAst(`model er schema entity
def entity artikl { nameAttribute: nazev, attributes: [ def attribute nazev { type: text } ] }`);
    const result = scanCrossReferences(ast, 'pkgA', symbols(
      { qname: 'er.entity.artikl', packageName: 'pkgA', schemaCode: 'er' },
    ), 3);
    expect(result.specs).toHaveLength(0);
  });

  it('ambiguous reference (same bare name in 2 packages) → recorded with qualified candidates', () => {
    const ast = makeAst(`model er schema entity
def entity foo { nameAttribute: x }`);
    const result = scanCrossReferences(ast, 'pkgA', [
      { qname: 'er.entity.x', packageName: 'pkgB', schemaCode: 'er' },
      { qname: 'er.entity.x', packageName: 'pkgC', schemaCode: 'er' },
    ], 3);
    expect(result.specs).toHaveLength(0);
    expect(result.ambiguous).toHaveLength(1);
    expect(result.ambiguous[0].candidates).toContain('pkgB.er.entity.x');
    expect(result.ambiguous[0].candidates).toContain('pkgC.er.entity.x');
  });
});
