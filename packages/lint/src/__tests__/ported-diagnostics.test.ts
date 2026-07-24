// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import type { Document, SourceLocation, Definition } from '@tatrman/parser';
import { ProjectSymbolTable, Resolver, resolveManifest } from '@tatrman/semantics';
import { lintDocument } from '../runner.js';
import type { LintDiagnostic } from '../rule.js';
import { lintOne, lintDocInProject, lintProj, recommendedConfig } from './helpers.js';

// Ported from packages/semantics/__tests__/diagnostics-v1.1.test.ts — now driving
// the @tatrman/lint runner instead of the deleted Validator. Asserts the same
// code + severity per diagnostic class.

function find(diags: LintDiagnostic[], code: DiagnosticCode): LintDiagnostic | undefined {
  return diags.find((d) => d.code === code);
}

describe('ported B4 diagnostics', () => {
  it('unimported-reference: Info for a fully-qualified ref into a non-imported package', () => {
    const files = [
      { uri: 'pkg_b/b.ttrm', src: `package pkg_b\nmodel er schema entity\ndef entity some_rel { attributes: [def attribute id { type: int }] }` },
      { uri: 'pkg_a/a.ttrm', src: `package pkg_a\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }\ndef er2db_relation r { relation: pkg_b.er.entity.some_rel }` },
    ];
    const d = lintDocInProject(files, 'pkg_a/a.ttrm', { projectRoot: '/test/project/' });
    const u = find(d, DiagnosticCode.UnimportedReference);
    expect(u).toBeDefined();
    expect(u!.severity).toBe('info');
  });

  it('unused-import: Warning for a never-referenced named import', () => {
    const files = [
      { uri: 'pkg_b/b.ttrm', src: `package pkg_b\nmodel er schema entity\ndef entity other_entity { attributes: [def attribute id { type: int }] }` },
      { uri: 'pkg_a/a.ttrm', src: `package pkg_a\nimport pkg_b.other_entity\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }` },
    ];
    const d = lintDocInProject(files, 'pkg_a/a.ttrm', { projectRoot: '/test/project/' });
    const u = find(d, DiagnosticCode.UnusedImport);
    expect(u).toBeDefined();
    expect(u!.severity).toBe('warning');
  });

  it('wildcard-with-no-matches: Warning for a wildcard into an empty package', () => {
    const d = lintOne('pkg_a/test.ttrm', `package pkg_a\nimport pkg_b.*\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    const w = find(d, DiagnosticCode.WildcardWithNoMatches);
    expect(w).toBeDefined();
    expect(w!.severity).toBe('warning');
  });

  it('duplicate-import: Warning + message', () => {
    const d = lintOne('pkg_a/a.ttrm', `package pkg_a\nimport pkg_b.entity_x\nimport pkg_b.entity_x\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    const dup = find(d, DiagnosticCode.DuplicateImport);
    expect(dup).toBeDefined();
    expect(dup!.severity).toBe('warning');
    expect(dup!.message).toContain('Duplicate import');
  });

  it('circular-package-dependency: Warning when two packages import each other', () => {
    const files = [
      { uri: 'pkg_a/a.ttrm', src: `package pkg_a\nimport pkg_b.*\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }` },
      { uri: 'pkg_b/b.ttrm', src: `package pkg_b\nimport pkg_a.*\nmodel db schema dbo\ndef table foo { columns: [def column id { type: int }] }` },
    ];
    // circular is project-scope: collect across all uris.
    const all = [...lintProj(files, { projectRoot: '/test/project/' }).values()].flat();
    const c = find(all, DiagnosticCode.CircularPackageDependency);
    expect(c).toBeDefined();
    expect(c!.severity).toBe('warning');
    expect(c!.source.file).not.toBe('');
  });

  it('package-declaration-mismatch: leaf-only mismatch warns under flexible (PD1.5)', () => {
    // `renamed` keeps the (empty) prefix of `pkg_a`, overriding only the leaf.
    const d = lintOne('/test/project/pkg_a/test.ttrm', `package renamed\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    const m = find(d, DiagnosticCode.PackageDeclarationMismatch);
    expect(m).toBeDefined();
    expect(m!.severity).toBe('warning');
  });

  it('missing-package-declaration: Info for a subdir file with no package', () => {
    const d = lintOne('/test/project/pkg_a/test.ttrm', `model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    const m = find(d, DiagnosticCode.MissingPackageDeclaration);
    expect(m).toBeDefined();
    expect(m!.severity).toBe('info');
  });

  it('missing-package-declaration: none for a root-level file', () => {
    const d = lintOne('/test/project/main.ttrm', `model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    expect(find(d, DiagnosticCode.MissingPackageDeclaration)).toBeUndefined();
  });

  it('missing-package-declaration: none for a .ttrg graph in a subdir', () => {
    const d = lintOne('/test/project/graphs/all_er.ttrg', `graph all_er { model: er, objects: [er.entity.artikl] }`, { projectRoot: '/test/project/' });
    expect(find(d, DiagnosticCode.MissingPackageDeclaration)).toBeUndefined();
  });

  it('package-declaration-mismatch: works with a file:// URI', () => {
    const d = lintOne('file:///test/project/pkg_a/test.ttrm', `package renamed\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }`, { projectRoot: '/test/project/' });
    const m = find(d, DiagnosticCode.PackageDeclarationMismatch);
    expect(m).toBeDefined();
    expect(m!.severity).toBe('warning');
  });

  it('ambiguous-reference: Error when a bare ref matches 2+ wildcard-imported packages', () => {
    const files = [
      { uri: 'pkg_b/b.ttrm', src: `package pkg_b\nmodel er schema entity\ndef entity shared_name { attributes: [def attribute id { type: int }] }` },
      { uri: 'pkg_c/c.ttrm', src: `package pkg_c\nmodel er schema entity\ndef entity shared_name { attributes: [def attribute id { type: int }] }` },
      { uri: 'pkg_a/a.ttrm', src: `package pkg_a\nimport pkg_b.*\nimport pkg_c.*\nmodel er schema entity\ndef entity artikl {\n attributes: [def attribute id { type: int }]\n nameAttribute: shared_name\n}` },
    ];
    const d = lintDocInProject(files, 'pkg_a/a.ttrm', { projectRoot: '/test/project/' });
    const amb = find(d, DiagnosticCode.AmbiguousReference);
    expect(amb).toBeDefined();
    expect(amb!.severity).toBe('error');
  });
});

describe('ported graph diagnostics (.ttrg)', () => {
  it('graph-missing-schema: Error', () => {
    const d = lintOne('test.ttrg', `graph test { objects: [] }`);
    const s = d.find((x) => x.code === DiagnosticCode.RequiredPropertyMissing && x.message.includes("graph requires a 'schema'"));
    expect(s).toBeDefined();
    expect(s!.severity).toBe('error');
  });

  it('graph-object-not-found: Warning', () => {
    const d = lintOne('test.ttrg', `graph test { model: er, objects: [er.entity.nonexistent] }`);
    const g = find(d, DiagnosticCode.GraphObjectNotFound);
    expect(g).toBeDefined();
    expect(g!.severity).toBe('warning');
  });

  it('graph-layout-stale-node: Warning', () => {
    const d = lintOne('test.ttrg', `graph test {\n model: er,\n objects: [er.entity.artikl],\n layout: { nodes: { 'some_unknown_object': { x: 0, y: 0 } } }\n}`);
    const g = find(d, DiagnosticCode.GraphLayoutStaleNode);
    expect(g).toBeDefined();
    expect(g!.severity).toBe('warning');
  });

  it('graph-objects-empty: Warning', () => {
    const d = lintOne('test.ttrg', `graph test { model: er, objects: [] }`);
    const g = find(d, DiagnosticCode.GraphObjectsEmpty);
    expect(g).toBeDefined();
    expect(g!.severity).toBe('warning');
  });

  it('graph-name-mismatch: Warning', () => {
    const d = lintOne('test.ttrg', `graph wrong_name { model: er, objects: [] }`);
    const g = find(d, DiagnosticCode.GraphNameMismatch);
    expect(g).toBeDefined();
    expect(g!.severity).toBe('warning');
  });
});

describe('ported file-ordering (hand-built ASTs — order-strict grammar)', () => {
  function loc(line: number): SourceLocation {
    return { file: 'test.ttrm', line, column: 0, endLine: line, endColumn: 0, offsetStart: 0, offsetEnd: 0 };
  }
  const stubDeps = {
    manifest: resolveManifest(undefined, '/test/project/'),
    symbols: new ProjectSymbolTable(),
    resolver: new Resolver(new ProjectSymbolTable()),
  };
  const lintAst = (ast: Document) => lintDocument('test.ttrm', ast, stubDeps, recommendedConfig());

  it('Warning when imports appear after the schema directive', () => {
    const ast: Document = {
      packageDecl: undefined,
      imports: [{ kind: 'importDecl', target: 'pkg_b', targetParts: ['pkg_b'], wildcard: true, source: loc(2) }],
      modelDirective: { modelCode: 'er', schema: 'entity', source: loc(1) },
      definitions: [],
      securityBlocks: [],
      source: loc(1),
    };
    const d = lintAst(ast);
    const o = find(d, DiagnosticCode.FileOrdering);
    expect(o).toBeDefined();
    expect(o!.message).toContain('import declarations must appear before schema directive');
    expect(o!.severity).toBe('warning');
  });

  it('no diagnostics for canonical order', () => {
    const ast: Document = {
      packageDecl: { kind: 'packageDecl', name: 'pkg_a', parts: ['pkg_a'], source: loc(1) },
      imports: [{ kind: 'importDecl', target: 'pkg_b', targetParts: ['pkg_b'], wildcard: true, source: loc(2) }],
      modelDirective: { modelCode: 'er', schema: 'entity', source: loc(3) },
      definitions: [{ kind: 'entity', name: 'artikl', attributes: [], source: loc(4) }] as unknown as Definition[],
      securityBlocks: [],
      source: loc(1),
    };
    expect(lintAst(ast).filter((x) => x.code === DiagnosticCode.FileOrdering)).toHaveLength(0);
  });

  it('Warning when schema appears after definitions', () => {
    const ast: Document = {
      packageDecl: undefined,
      imports: [],
      modelDirective: { modelCode: 'er', schema: 'entity', source: loc(5) },
      definitions: [{ kind: 'entity', name: 'artikl', attributes: [], source: loc(2) }] as unknown as Definition[],
      securityBlocks: [],
      source: loc(1),
    };
    const o = find(lintAst(ast), DiagnosticCode.FileOrdering);
    expect(o).toBeDefined();
    expect(o!.message).toContain('schema directive must appear before definitions');
  });
});
