// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import type { AreaDef } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { effectivePackage } from '../derivation.js';
import { AreaTableBuilder, areaPackageClosure } from '../area-table.js';
import type { PackagesConfig } from '../manifest.js';

const ROOT = '/proj';

function entityFile(pkg: string, entity: string): string {
  return `package ${pkg}\nmodel er schema entity\ndef entity ${entity} { attributes: [def attribute id { type: int }] }`;
}

/** Build a project symbol table + resolver from package files. */
function buildProject(files: Array<{ uri: string; src: string }>, cfg: PackagesConfig) {
  const symbols = new ProjectSymbolTable();
  for (const { uri, src } of files) {
    const ast = parseString(src, uri).ast!;
    const pkg = effectivePackage(ast, uri, ROOT, cfg);
    symbols.upsertDocument(uri, ast, ast.modelDirective?.modelCode ?? '', ast.modelDirective?.schema ?? '', pkg);
  }
  return { symbols, resolver: new Resolver(symbols, cfg.root) };
}

function areaOf(src: string): AreaDef {
  const ast = parseString(src, 'file:///proj/d.ttrm').ast!;
  return ast.definitions.find((d): d is AreaDef => d.kind === 'area')!;
}

const flexible: PackagesConfig = { root: '', layout: 'flexible' };
const withRoot: PackagesConfig = { root: 'cz.dfpartner', layout: 'flexible' };

describe('PD3 — DomainTable recursive closure', () => {
  it('domain { packages: [a] } pulls a and all a.* descendants (recursive)', () => {
    const { symbols, resolver } = buildProject(
      [
        { uri: 'file:///proj/a/er.ttrm', src: entityFile('a', 'topA') },
        { uri: 'file:///proj/a/b/er.ttrm', src: entityFile('a.b', 'inB') },
        { uri: 'file:///proj/a/b/c/er.ttrm', src: entityFile('a.b.c', 'inC') },
      ],
      flexible
    );
    const table = new AreaTableBuilder(symbols, resolver).build([
      { area: areaOf('def area D { packages: [a] }'), documentUri: 'file:///proj/d.ttrm' },
    ]);
    expect(table.get('D')!.resolvedPackages).toEqual(['a', 'a.b', 'a.b.c']);
  });

  it('CONTRAST: import a.* is NON-recursive (B20) — exposes a top-level only, not a.b', () => {
    const { symbols, resolver } = buildProject(
      [
        { uri: 'file:///proj/a/er.ttrm', src: entityFile('a', 'topA') },
        { uri: 'file:///proj/a/b/er.ttrm', src: entityFile('a.b', 'inB') },
      ],
      flexible
    );
    const imports = [
      { kind: 'importDecl' as const, target: 'a', targetParts: ['a'], wildcard: true, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 } },
    ];
    const ctx = { schemaCode: 'er', namespace: 'entity', imports, packageName: 'caller' };

    const top = resolver.resolveReference({ path: 'topA', parts: ['topA'] }, ctx);
    expect(top.resolved && top.viaStep).toBe('wildcard-import');

    // `inB` lives in a.b — `import a.*` must NOT expose it through the wildcard
    // step. (It may still resolve via the unique fully-qualified fallback, but
    // never via wildcard-import — that is the recursion contrast.)
    const nested = resolver.resolveReference({ path: 'inB', parts: ['inB'] }, ctx);
    expect(nested.resolved && nested.viaStep === 'wildcard-import').toBe(false);

    // Domain membership, by contrast, IS recursive over the same project.
    const table = new AreaTableBuilder(symbols, resolver).build([
      { area: areaOf('def area D { packages: [a] }'), documentUri: 'file:///proj/d.ttrm' },
    ]);
    expect(table.get('D')!.resolvedPackages).toEqual(['a', 'a.b']);
  });

  it('entities: members resolve to canonical qnames', () => {
    const { symbols, resolver } = buildProject(
      [{ uri: 'file:///proj/a/er.ttrm', src: entityFile('a', 'artikl') }],
      flexible
    );
    const table = new AreaTableBuilder(symbols, resolver).build([
      { area: areaOf('def area D { packages: [], entities: [a.er.entity.artikl] }'), documentUri: 'file:///proj/d.ttrm' },
    ]);
    expect(table.get('D')!.resolvedEntities).toEqual(['a.er.entity.artikl']);
  });

  it('root elision: domain { packages: [a] } resolves a to cz.dfpartner.a and its closure', () => {
    // Undeclared files under root → canonical packages carry the prefix.
    const undeclared = (dir: string, entity: string) =>
      `model er schema entity\ndef entity ${entity} { attributes: [def attribute id { type: int }] }`;
    const { symbols, resolver } = buildProject(
      [
        { uri: 'file:///proj/a/er.ttrm', src: undeclared('a', 'topA') },
        { uri: 'file:///proj/a/b/er.ttrm', src: undeclared('a/b', 'inB') },
      ],
      withRoot
    );
    expect(symbols.listPackages().sort()).toEqual(['cz.dfpartner.a', 'cz.dfpartner.a.b']);

    const table = new AreaTableBuilder(symbols, resolver, withRoot.root).build([
      { area: areaOf('def area D { packages: [a] }'), documentUri: 'file:///proj/d.ttrm' },
    ]);
    expect(table.get('D')!.resolvedPackages).toEqual(['cz.dfpartner.a', 'cz.dfpartner.a.b']);
  });

  it('areaPackageClosure ignores the default (empty) package', () => {
    const { symbols } = buildProject(
      [{ uri: 'file:///proj/main.ttrm', src: 'model er schema entity\ndef entity x { attributes: [def attribute id { type: int }] }' }],
      flexible
    );
    // The root-level file is in the default package "" — never a domain member.
    expect(areaPackageClosure(symbols, '', '')).toEqual([]);
  });
});
