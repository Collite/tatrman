import { describe, it, expect } from 'vitest';
import {
  buildArtifactFromFiles,
  serializeArtifact,
  type ModelFile,
} from '../resolve-packages.js';
import type { PackagesConfig } from '@modeler/semantics';

const ROOT = '/proj';
const flexible: PackagesConfig = { root: '', layout: 'flexible' };
const withRoot: PackagesConfig = { root: 'cz.dfpartner', layout: 'flexible' };

const declared = (pkg: string, entity: string) =>
  `package ${pkg}\nschema er namespace entity\ndef entity ${entity} { attributes: [def attribute id { type: int }] }`;
const undeclared = (entity: string) =>
  `schema er namespace entity\ndef entity ${entity} { attributes: [def attribute id { type: int }] }`;

const FIXTURE: ModelFile[] = [
  { path: '/proj/a/er.ttrm', text: declared('a', 'ea') },
  { path: '/proj/a/b/er.ttrm', text: declared('a.b', 'eb') },
  { path: '/proj/a/b/c/er.ttrm', text: declared('a.b.c', 'ec') },
  { path: '/proj/domains/core.ttrm', text: 'def area D { packages: [a] }' },
];

describe('PD4 — buildArtifactFromFiles', () => {
  it('produces the contracts §13.4 shape', () => {
    const a = buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj');
    expect(a.formatVersion).toBe(1);
    expect(a.root).toBe('');
    expect(a.generatedFrom).toBe('proj');
  });

  it('packages sorted by canonicalName with nested flags and directories', () => {
    const { packages } = buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj');
    expect(packages).toEqual([
      { canonicalName: 'a', declaredName: 'a', nested: false, directory: 'a' },
      { canonicalName: 'a.b', declaredName: 'a.b', nested: true, directory: 'a/b' },
      { canonicalName: 'a.b.c', declaredName: 'a.b.c', nested: true, directory: 'a/b/c' },
    ]);
  });

  it('entities sorted by qname with owning package + schema', () => {
    const { entities } = buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj');
    expect(entities).toEqual([
      { qname: 'a.b.c.er.entity.ec', package: 'a.b.c', schema: 'er' },
      { qname: 'a.b.er.entity.eb', package: 'a.b', schema: 'er' },
      { qname: 'a.er.entity.ea', package: 'a', schema: 'er' },
    ]);
  });

  it('domains carry the RECURSIVE package closure', () => {
    const { domains } = buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj');
    expect(domains).toEqual([
      { name: 'D', resolvedPackages: ['a', 'a.b', 'a.b.c'], resolvedEntities: [] },
    ]);
  });

  it('is byte-deterministic — serialise twice → identical', () => {
    const a = serializeArtifact(buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj'));
    const b = serializeArtifact(buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj'));
    expect(a).toBe(b);
    expect(a.endsWith('\n')).toBe(true); // trailing newline
  });

  it('input file order does not affect output (sorting defeats order)', () => {
    const forward = serializeArtifact(buildArtifactFromFiles(FIXTURE, ROOT, flexible, 'proj'));
    const reversed = serializeArtifact(buildArtifactFromFiles([...FIXTURE].reverse(), ROOT, flexible, 'proj'));
    expect(reversed).toBe(forward);
  });

  it('root="cz.dfpartner": canonicalNames prefixed, declaredNames the bare written form', () => {
    // Files declare bare packages (eliding the root); the artifact re-prefixes.
    const files: ModelFile[] = [
      { path: '/proj/a/er.ttrm', text: declared('a', 'ea') },
      { path: '/proj/a/b/er.ttrm', text: declared('a.b', 'eb') },
      { path: '/proj/domains/core.ttrm', text: 'def area D { packages: [a] }' },
    ];
    const a = buildArtifactFromFiles(files, ROOT, withRoot, 'proj');
    expect(a.root).toBe('cz.dfpartner');
    expect(a.packages).toEqual([
      { canonicalName: 'cz.dfpartner.a', declaredName: 'a', nested: false, directory: 'a' },
      { canonicalName: 'cz.dfpartner.a.b', declaredName: 'a.b', nested: true, directory: 'a/b' },
    ]);
    expect(a.entities.map((e) => e.qname)).toEqual([
      'cz.dfpartner.a.b.er.entity.eb',
      'cz.dfpartner.a.er.entity.ea',
    ]);
    expect(a.domains[0].resolvedPackages).toEqual(['cz.dfpartner.a', 'cz.dfpartner.a.b']);
  });

  it('undeclared files under a root derive prefixed canonical names', () => {
    const files: ModelFile[] = [
      { path: '/proj/a/er.ttrm', text: undeclared('ea') },
      { path: '/proj/a/b/er.ttrm', text: undeclared('eb') },
    ];
    const a = buildArtifactFromFiles(files, ROOT, withRoot, 'proj');
    expect(a.packages.map((p) => p.canonicalName)).toEqual(['cz.dfpartner.a', 'cz.dfpartner.a.b']);
  });

  it('empty project → valid artifact with empty arrays (not missing keys)', () => {
    const a = buildArtifactFromFiles([], ROOT, flexible, 'proj');
    expect(a).toEqual({
      formatVersion: 1,
      generatedFrom: 'proj',
      root: '',
      packages: [],
      entities: [],
      domains: [],
    });
  });

  it('a project with no domains still emits an empty domains array', () => {
    const files: ModelFile[] = [{ path: '/proj/a/er.ttrm', text: declared('a', 'ea') }];
    const a = buildArtifactFromFiles(files, ROOT, flexible, 'proj');
    expect(a.domains).toEqual([]);
  });
});
