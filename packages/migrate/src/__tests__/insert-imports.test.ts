import { describe, it, expect } from 'vitest';
import { insertImports, type ImportSpec } from '../index.js';

describe('insertImports', () => {
  it('produces named import when only one symbol from package B is referenced', () => {
    const content = `package pkgA
schema er namespace entity
def entity foo { nameAttribute: pkgB.er.entity.bar }`;
    const specs: ImportSpec[] = [
      { packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'bar', isWildcard: false },
    ];
    const result = insertImports(content, specs);
    expect(result).toContain('import pkgB.er.entity.bar');
  });

  it('produces wildcard import when ≥wildcard-threshold symbols from package B are referenced', () => {
    const content = `package pkgA
schema er namespace entity
def entity foo { nameAttribute: pkgB.er.entity.x }
def entity baz { nameAttribute: pkgB.er.entity.y }
def entity qux { nameAttribute: pkgB.er.entity.z }`;
    const specs: ImportSpec[] = [
      { packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'x', isWildcard: true },
      { packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'y', isWildcard: true },
      { packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'z', isWildcard: true },
    ];
    const result = insertImports(content, specs);
    expect(result).toContain('import pkgB.*');
    expect(result).not.toContain('import pkgB.er.entity.x');
  });

  it('is idempotent — does not duplicate imports already present', () => {
    const content = `package pkgA
import pkgB.*
schema er namespace entity
def entity foo { nameAttribute: pkgB.er.entity.x }`;
    const specs: ImportSpec[] = [
      { packageName: 'pkgB', schema: 'er', namespace: 'entity', defName: 'x', isWildcard: true },
    ];
    const result = insertImports(content, specs);
    const matches = result.split('\n').filter(l => l.trim() === 'import pkgB.*');
    expect(matches.length).toBe(1);
  });

  it('inserts import block before schema line', () => {
    const content = `package foo
schema er namespace entity
def entity artikl { }`;
    const specs: ImportSpec[] = [
      { packageName: 'bar', schema: 'er', namespace: 'entity', defName: 'baz', isWildcard: false },
    ];
    const result = insertImports(content, specs);
    const schemaIdx = result.indexOf('schema er namespace entity');
    const importIdx = result.indexOf('import bar.er.entity.baz');
    expect(importIdx).toBeLessThan(schemaIdx);
  });
});