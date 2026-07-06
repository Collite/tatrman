import { describe, it, expect } from 'vitest';
import { DiagnosticCode, parseString } from '@tatrman/parser';
import { lintDocInProject, lintProj, codesOf, type ProjectFile } from './helpers.js';

// v3.0 (Phase 0 Stage B) — area validators (emitted by @tatrman/lint). Subject
// areas are now `def area` definitions in ordinary model files; diagnostic codes
// are `ttr/area-*` (renamed from the v2.3 `ttr/domain-*`).

const ROOT = '/proj';
const entityFile = (pkg: string, e: string): ProjectFile['src'] =>
  `package ${pkg}\nmodel er schema entity\ndef entity ${e} { attributes: [def attribute id { type: int }] }`;

const PKG_FILES: ProjectFile[] = [
  { uri: '/proj/a/er.ttrm', src: entityFile('a', 'artikl') },
  { uri: '/proj/a/b/er.ttrm', src: entityFile('a.b', 'sub') },
];

const D_URI = '/proj/areas.ttrm';
function areaDiags(areaSrc: string) {
  return lintDocInProject([...PKG_FILES, { uri: D_URI, src: areaSrc }], D_URI, { projectRoot: ROOT });
}

describe('v3.0 — area diagnostics', () => {
  it('unresolved package and entity members → ttr/area-member-not-found (warning)', () => {
    const diags = areaDiags('def area D { packages: [a, nope], entities: [a.er.entity.ghost] }');
    const nf = diags.filter((d) => d.code === DiagnosticCode.AreaMemberNotFound);
    expect(nf).toHaveLength(2); // `nope` and `a.er.entity.ghost`
    expect(nf.every((d) => d.severity === 'warning')).toBe(true);
    expect(nf.some((d) => d.message.includes('nope'))).toBe(true);
    expect(nf.some((d) => d.message.includes('ghost'))).toBe(true);
  });

  it('a resolvable area raises no member-not-found', () => {
    const diags = areaDiags('def area D { packages: [a] }');
    expect(codesOf(diags)).not.toContain(DiagnosticCode.AreaMemberNotFound);
  });

  it('empty area → ttr/area-empty (warning)', () => {
    const diags = areaDiags('def area Empty { }');
    const empty = diags.filter((d) => d.code === DiagnosticCode.AreaEmpty);
    expect(empty).toHaveLength(1);
    expect(empty[0].severity).toBe('warning');
  });

  it('entity already covered by a recursive packages member → ttr/area-redundant-member (info)', () => {
    const diags = areaDiags('def area D { packages: [a], entities: [a.er.entity.artikl] }');
    const redundant = diags.filter((d) => d.code === DiagnosticCode.AreaRedundantMember);
    expect(redundant).toHaveLength(1);
    expect(redundant[0].severity).toBe('info');
    expect(redundant[0].message).toContain('artikl');
  });

  it('an entity NOT covered by any packages member is not redundant', () => {
    const diags = areaDiags('def area D { packages: [a.b], entities: [a.er.entity.artikl] }');
    expect(codesOf(diags)).not.toContain(DiagnosticCode.AreaRedundantMember);
  });

  it('two files with the same area name → ttr/duplicate-area (error)', () => {
    const files: ProjectFile[] = [
      ...PKG_FILES,
      { uri: '/proj/a1.ttrm', src: 'def area accounting { packages: [a] }' },
      { uri: '/proj/a2.ttrm', src: 'def area accounting { packages: [a.b] }' },
    ];
    const byUri = lintProj(files, { projectRoot: ROOT });
    const d1 = byUri.get('/proj/a1.ttrm') ?? [];
    const d2 = byUri.get('/proj/a2.ttrm') ?? [];
    expect(d1.some((d) => d.code === DiagnosticCode.DuplicateArea && d.severity === 'error')).toBe(true);
    expect(d2.some((d) => d.code === DiagnosticCode.DuplicateArea && d.severity === 'error')).toBe(true);
  });

  it('distinct area names do not collide', () => {
    const files: ProjectFile[] = [
      ...PKG_FILES,
      { uri: '/proj/a1.ttrm', src: 'def area accounting { packages: [a] }' },
      { uri: '/proj/a2.ttrm', src: 'def area sales { packages: [a.b] }' },
    ];
    const byUri = lintProj(files, { projectRoot: ROOT });
    for (const [, diags] of byUri) {
      expect(codesOf(diags)).not.toContain(DiagnosticCode.DuplicateArea);
    }
  });

  it('an area coexists with other defs in the same file (no wrong-file-kind)', () => {
    const { errors } = parseString(
      'def area D { packages: [a] }\ndef entity x { attributes: [def attribute id { type: int }] }',
      'file:///proj/mixed.ttrm'
    );
    expect(errors.filter((e) => e.code === DiagnosticCode.WrongFileKind)).toHaveLength(0);
  });

  it('a bare top-level `domain { ... }` block is a parse error (domain keyword removed)', () => {
    const { errors } = parseString('domain D { packages: [a] }', 'file:///proj/x.ttrm');
    expect(errors.length).toBeGreaterThan(0);
  });
});
