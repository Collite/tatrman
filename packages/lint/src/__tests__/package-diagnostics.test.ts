import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintOne, type LintHelperOpts } from './helpers.js';

// PD1.5 / PD1.6 — package-declaration-mismatch severity is driven by
// [packages].layout, and a prefix divergence raises the louder
// package-prefix-divergence instead of the plain mismatch.
//
// These diagnostics are emitted by @modeler/lint (the validator lives here, not
// in @modeler/semantics as the architecture sketch implies); the task file
// names a semantics path but the runtime location wins (CLAUDE.md).

const ROOT = '/proj';
const URI = '/proj/a/b/er.ttrm'; // directory-derived package: a.b
const ENTITY = 'def entity X { attributes: [def attribute id { type: int }] }';

function diags(decl: string, packages: LintHelperOpts['packages']) {
  const src = `package ${decl}\nschema er namespace entity\n${ENTITY}`;
  return lintOne(URI, src, { projectRoot: ROOT, packages }).filter(
    (d) =>
      d.code === DiagnosticCode.PackageDeclarationMismatch ||
      d.code === DiagnosticCode.PackagePrefixDivergence
  );
}

describe('PD1.5 — mismatch severity by [packages].layout', () => {
  it('flexible: leaf-only mismatch → package-declaration-mismatch Warning', () => {
    const d = diags('a.renamed', { layout: 'flexible' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackageDeclarationMismatch);
    expect(d[0].severity).toBe('warning');
  });

  it('strict: same leaf-only mismatch → Error', () => {
    const d = diags('a.renamed', { layout: 'strict' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackageDeclarationMismatch);
    expect(d[0].severity).toBe('error');
  });

  it('off: same input → no diagnostic', () => {
    expect(diags('a.renamed', { layout: 'off' })).toHaveLength(0);
  });

  it('a matching declaration never fires', () => {
    expect(diags('a.b', { layout: 'strict' })).toHaveLength(0);
  });

  it('a declaration eliding the configured root still matches', () => {
    expect(diags('a.b', { root: 'cz.dfpartner', layout: 'strict' })).toHaveLength(0);
  });
});

describe('PD1.6 — prefix divergence', () => {
  it('flexible: prefix divergence → package-prefix-divergence Warning (not plain mismatch)', () => {
    const d = diags('totally.different.thing', { layout: 'flexible' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackagePrefixDivergence);
    expect(d[0].severity).toBe('warning');
  });

  it('strict: prefix divergence → Error', () => {
    const d = diags('totally.different.thing', { layout: 'strict' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackagePrefixDivergence);
    expect(d[0].severity).toBe('error');
  });

  it('off: prefix divergence still warns (never suppressed — it orphans the file)', () => {
    const d = diags('totally.different.thing', { layout: 'off' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackagePrefixDivergence);
    expect(d[0].severity).toBe('warning');
  });

  it('leaf-only override does NOT raise prefix-divergence', () => {
    const d = diags('a.renamed', { layout: 'flexible' });
    expect(d.map((x) => x.code)).not.toContain(DiagnosticCode.PackagePrefixDivergence);
  });

  it('a differing non-leaf segment of equal length still diverges', () => {
    // x.b vs a.b: same length, but the non-leaf segment differs.
    const d = diags('x.b', { layout: 'flexible' });
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.PackagePrefixDivergence);
  });
});
