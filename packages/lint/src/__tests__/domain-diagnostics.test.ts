import { describe, it, expect } from 'vitest';
import { DiagnosticCode, parseString } from '@modeler/parser';
import { lintDocInProject, lintProj, codesOf, type ProjectFile } from './helpers.js';

// PD3.5 — domain validators (emitted by @modeler/lint; the validator lives here,
// not in @modeler/semantics as the architecture sketch implies). wrong-file-kind
// is parser-emitted and asserted via parseString.

const ROOT = '/proj';
const entityFile = (pkg: string, e: string): ProjectFile['src'] =>
  `package ${pkg}\nschema er namespace entity\ndef entity ${e} { attributes: [def attribute id { type: int }] }`;

const PKG_FILES: ProjectFile[] = [
  { uri: '/proj/a/er.ttr', src: entityFile('a', 'artikl') },
  { uri: '/proj/a/b/er.ttr', src: entityFile('a.b', 'sub') },
];

const D_URI = '/proj/d.ttrd';
function domainDiags(domainSrc: string) {
  return lintDocInProject([...PKG_FILES, { uri: D_URI, src: domainSrc }], D_URI, { projectRoot: ROOT });
}

describe('PD3.5 — domain diagnostics', () => {
  it('unresolved package and entity members → ttr/domain-member-not-found (warning)', () => {
    const diags = domainDiags('domain D { packages: [a, nope], entities: [a.er.entity.ghost] }');
    const nf = diags.filter((d) => d.code === DiagnosticCode.DomainMemberNotFound);
    expect(nf).toHaveLength(2); // `nope` and `a.er.entity.ghost`
    expect(nf.every((d) => d.severity === 'warning')).toBe(true);
    expect(nf.some((d) => d.message.includes('nope'))).toBe(true);
    expect(nf.some((d) => d.message.includes('ghost'))).toBe(true);
  });

  it('a resolvable domain raises no member-not-found', () => {
    const diags = domainDiags('domain D { packages: [a] }');
    expect(codesOf(diags)).not.toContain(DiagnosticCode.DomainMemberNotFound);
  });

  it('empty domain → ttr/domain-empty (warning)', () => {
    const diags = domainDiags('domain Empty { }');
    const empty = diags.filter((d) => d.code === DiagnosticCode.DomainEmpty);
    expect(empty).toHaveLength(1);
    expect(empty[0].severity).toBe('warning');
  });

  it('entity already covered by a recursive packages member → ttr/domain-redundant-member (info)', () => {
    const diags = domainDiags('domain D { packages: [a], entities: [a.er.entity.artikl] }');
    const redundant = diags.filter((d) => d.code === DiagnosticCode.DomainRedundantMember);
    expect(redundant).toHaveLength(1);
    expect(redundant[0].severity).toBe('info');
    // a.b.sub is under `a` too, but artikl is the only entity member here.
    expect(redundant[0].message).toContain('artikl');
  });

  it('an entity NOT covered by any packages member is not redundant', () => {
    // domain pulls only a.b, but the entity is from a → not covered.
    const diags = domainDiags('domain D { packages: [a.b], entities: [a.er.entity.artikl] }');
    expect(codesOf(diags)).not.toContain(DiagnosticCode.DomainRedundantMember);
  });

  it('two .ttrd files with the same domain name → ttr/duplicate-domain (error)', () => {
    const files: ProjectFile[] = [
      ...PKG_FILES,
      { uri: '/proj/d1.ttrd', src: 'domain accounting { packages: [a] }' },
      { uri: '/proj/d2.ttrd', src: 'domain accounting { packages: [a.b] }' },
    ];
    const byUri = lintProj(files, { projectRoot: ROOT });
    const d1 = byUri.get('/proj/d1.ttrd') ?? [];
    const d2 = byUri.get('/proj/d2.ttrd') ?? [];
    expect(d1.some((d) => d.code === DiagnosticCode.DuplicateDomain && d.severity === 'error')).toBe(true);
    expect(d2.some((d) => d.code === DiagnosticCode.DuplicateDomain && d.severity === 'error')).toBe(true);
  });

  it('distinct domain names do not collide', () => {
    const files: ProjectFile[] = [
      ...PKG_FILES,
      { uri: '/proj/d1.ttrd', src: 'domain accounting { packages: [a] }' },
      { uri: '/proj/d2.ttrd', src: 'domain sales { packages: [a.b] }' },
    ];
    const byUri = lintProj(files, { projectRoot: ROOT });
    for (const [, diags] of byUri) {
      expect(codesOf(diags)).not.toContain(DiagnosticCode.DuplicateDomain);
    }
  });
});

describe('PD3.4 — .ttrd file-kind enforcement (parser-emitted ttr/wrong-file-kind)', () => {
  const wrongFileKind = (errs: { code?: string }[]) =>
    errs.filter((e) => e.code === DiagnosticCode.WrongFileKind);

  it('a .ttrd with no domain block → wrong-file-kind', () => {
    const { errors } = parseString('schema er namespace entity\n', 'file:///proj/x.ttrd');
    expect(wrongFileKind(errors).length).toBeGreaterThan(0);
  });

  it('a domain block inside a .ttr → wrong-file-kind', () => {
    const { errors } = parseString('domain D { packages: [a] }', 'file:///proj/x.ttr');
    expect(wrongFileKind(errors).length).toBeGreaterThan(0);
  });

  it('a domain block plus a def in a .ttrd → wrong-file-kind', () => {
    const { errors } = parseString(
      'domain D { packages: [a] }\ndef entity x { attributes: [def attribute id { type: int }] }',
      'file:///proj/x.ttrd'
    );
    expect(wrongFileKind(errors).length).toBeGreaterThan(0);
  });

  it('a well-formed .ttrd domain file has no wrong-file-kind error', () => {
    const { errors } = parseString('domain D { packages: [a] }', 'file:///proj/x.ttrd');
    expect(wrongFileKind(errors)).toHaveLength(0);
  });
});
