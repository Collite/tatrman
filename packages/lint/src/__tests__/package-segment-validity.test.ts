import { describe, it, expect } from 'vitest';
import { DiagnosticCode, parseString } from '@tatrman/parser';
import { effectivePackage, resolveManifest } from '@tatrman/semantics';
import { lintOne, codesOf, type LintHelperOpts } from './helpers.js';

// PD1.8 / B24 — directory segments must be valid IDENTs (letters/digits/
// underscore, no hyphen). A non-IDENT folder with no `package` declaration
// raises `ttr/invalid-package-segment`; there is NO `-`→`_` normalization
// (reject, don't rewrite — avoids `my-pkg/` vs `my_pkg/` collision). A valid
// declaration is the sanctioned escape hatch and suppresses the segment,
// mismatch, and prefix-divergence checks for that file.

const ROOT = '/proj';
const ENTITY = 'def entity X { attributes: [def attribute id { type: int }] }';

/** Effective package a file resolves to under a `[packages]` config. */
function effectivePkg(uri: string, src: string, packages?: LintHelperOpts['packages']): string {
  const ast = parseString(src, uri).ast!;
  const manifest = resolveManifest({ packages }, ROOT);
  return effectivePackage(ast, uri, ROOT, manifest.packages);
}

const PACKAGE_CODES = new Set<string>([
  DiagnosticCode.InvalidPackageSegment,
  DiagnosticCode.PackageDeclarationMismatch,
  DiagnosticCode.PackagePrefixDivergence,
]);

describe('PD1.8 / B24 — invalid-package-segment', () => {
  it('hyphenated folder with no declaration → invalid-package-segment (no normalization)', () => {
    const uri = '/proj/my-pkg/x.ttrm';
    const src = `model er schema entity\n${ENTITY}`;
    const d = lintOne(uri, src, { projectRoot: ROOT, packages: { layout: 'flexible' } });
    const seg = d.filter((x) => x.code === DiagnosticCode.InvalidPackageSegment);
    expect(seg).toHaveLength(1);
    expect(seg[0].severity).toBe('warning');

    // No `-`→`_` normalization: the effective package is the raw `my-pkg`, NOT `my_pkg`.
    expect(effectivePkg(uri, src)).toBe('my-pkg');
    expect(effectivePkg(uri, src)).not.toBe('my_pkg');
  });

  it('strict layout → invalid-package-segment is an Error', () => {
    const uri = '/proj/my-pkg/x.ttrm';
    const src = `model er schema entity\n${ENTITY}`;
    const d = lintOne(uri, src, { projectRoot: ROOT, packages: { layout: 'strict' } });
    const seg = d.filter((x) => x.code === DiagnosticCode.InvalidPackageSegment);
    expect(seg).toHaveLength(1);
    expect(seg[0].severity).toBe('error');
  });

  it('valid declaration wins: segment / mismatch / prefix-divergence all suppressed', () => {
    const uri = '/proj/my-pkg/x.ttrm';
    const src = `package my_pkg\nmodel er schema entity\n${ENTITY}`;
    const d = lintOne(uri, src, { projectRoot: ROOT, packages: { layout: 'strict' } });
    expect(codesOf(d).filter((c) => PACKAGE_CODES.has(c))).toEqual([]);
    // The declaration is authoritative — the file resolves to the declared name.
    expect(effectivePkg(uri, src)).toBe('my_pkg');
  });

  it('underscore folder → no diagnostic (project convention stays clean)', () => {
    const uri = '/proj/obchodni_doklady/x.ttrm';
    const src = `model er schema entity\n${ENTITY}`;
    const d = lintOne(uri, src, { projectRoot: ROOT, packages: { layout: 'strict' } });
    expect(d.filter((x) => x.code === DiagnosticCode.InvalidPackageSegment)).toHaveLength(0);
    expect(effectivePkg(uri, src)).toBe('obchodni_doklady');
  });
});
