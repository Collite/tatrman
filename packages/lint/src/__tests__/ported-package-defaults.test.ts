import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { inferPackageFromUri } from '@modeler/semantics';
import { lintOne } from './helpers.js';

// Ported from packages/semantics/__tests__/package-defaults.test.ts — the
// package-declaration checks now run through the @modeler/lint runner; the
// inferPackageFromUri sanity assertions remain (it is still a semantics export).

const PROJECT_ROOT = '/proj';
const ENTITY = 'def entity X { attributes: [def attribute id { type: int }] }';

function packageDiags(uri: string, src: string) {
  return lintOne(uri, src, { projectRoot: PROJECT_ROOT }).filter(
    (d) =>
      d.code === DiagnosticCode.MissingPackageDeclaration ||
      d.code === DiagnosticCode.PackageDeclarationMismatch
  );
}

describe('validatePackageDeclarations (via lint runner)', () => {
  it('1.2 root file, no package ⇒ no diagnostic', () => {
    const uri = '/proj/main.ttrm';
    expect(inferPackageFromUri(uri, PROJECT_ROOT).isRootFile).toBe(true);
    expect(packageDiags(uri, `model er schema entity\n${ENTITY}`)).toHaveLength(0);
  });

  it('1.3 non-root file, no package ⇒ info MissingPackageDeclaration naming the inferred package', () => {
    const uri = '/proj/billing/invoicing/x.ttrm';
    const { inferred, isRootFile } = inferPackageFromUri(uri, PROJECT_ROOT);
    expect(isRootFile).toBe(false);
    expect(inferred).toBe('billing.invoicing');
    const diags = packageDiags(uri, `model er schema entity\n${ENTITY}`);
    expect(diags).toHaveLength(1);
    expect(diags[0].code).toBe(DiagnosticCode.MissingPackageDeclaration);
    expect(diags[0].severity).toBe('info');
    expect(diags[0].message).toContain('billing.invoicing');
  });

  it('1.4 .ttrg file is exempt ⇒ no package diagnostic', () => {
    const uri = '/proj/billing/invoicing/main.ttrg';
    expect(packageDiags(uri, 'graph main { model: er, objects: [er.entity.X] }\n')).toHaveLength(0);
  });

  it('1.5 leaf-only mismatch ⇒ warning PackageDeclarationMismatch (flexible default, PD1.5)', () => {
    const uri = '/proj/x/y/file.ttrm';
    expect(inferPackageFromUri(uri, PROJECT_ROOT).inferred).toBe('x.y');
    // `x.renamed` keeps the prefix `x`, overriding only the leaf segment.
    const diags = packageDiags(uri, `package x.renamed\nmodel er schema entity\n${ENTITY}`);
    expect(diags).toHaveLength(1);
    expect(diags[0].code).toBe(DiagnosticCode.PackageDeclarationMismatch);
    // No longer a fixed Error: severity is now driven by [packages].layout,
    // defaulting to Warning under "flexible" (B16).
    expect(diags[0].severity).toBe('warning');
  });
});
