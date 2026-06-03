import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@modeler/parser';
import type { Document, SourceLocation, Definition } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { Validator } from '../validator.js';
import { resolveManifest } from '../manifest.js';
import { PackageGraphBuilder } from '../package-graph.js';

function makeManifest() {
  return resolveManifest({ lint: { strict: false } }, '/test/project/');
}

function setupValidator(src: string, uri = 'test.ttr') {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
  const namespace = ast.schemaDirective?.namespace ?? '';
  const symbols = new ProjectSymbolTable();
  const packageName = ast.packageDecl?.name ?? '';
  symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);
  const resolver = new Resolver(symbols);
  const manifest = makeManifest();
  manifest.projectRoot = '/test/project/';
  const validator = new Validator(symbols, resolver, manifest);
  return { ast, symbols, resolver, validator, manifest };
}

describe('B4 diagnostics — ttr/unimported-reference', () => {
  it('emits Info when a fully-qualified reference targets a def in a non-imported package', () => {
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    manifest.projectRoot = '/test/project/';

    const astB = parseString(
      `package pkg_b
       schema er namespace entity
       def entity some_rel { attributes: [def attribute id { type: int }] }`,
      'pkg_b/b.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_b/b.ttr', astB, 'er', 'entity', 'pkg_b');

    const astA = parseString(
      `package pkg_a
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }
       def er2db_relation r { relation: pkg_b.er.entity.some_rel }`,
      'pkg_a/a.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_a/a.ttr', astA, 'er', 'entity', 'pkg_a');

    const resolver = new Resolver(symbols);
    const validator = new Validator(symbols, resolver, manifest);
    const diags = validator.validateReferences('pkg_a/a.ttr', astA);
    const unimported = diags.find((d) => d.code === DiagnosticCode.UnimportedReference);
    expect(unimported).toBeDefined();
    expect(unimported!.severity).toBe('info');
  });
});

describe('B4 diagnostics — ttr/unused-import', () => {
  it('emits Warning when a named import is never referenced', () => {
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    manifest.projectRoot = '/test/project/';

    const astB = parseString(
      `package pkg_b
       schema er namespace entity
       def entity other_entity { attributes: [def attribute id { type: int }] }`,
      'pkg_b/b.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_b/b.ttr', astB, 'er', 'entity', 'pkg_b');

    const astA = parseString(
      `package pkg_a
       import pkg_b.other_entity
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'pkg_a/a.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_a/a.ttr', astA, 'er', 'entity', 'pkg_a');

    const resolver = new Resolver(symbols);
    const validator = new Validator(symbols, resolver, manifest);
    const diags = validator.validateImports('pkg_a/a.ttr', astA);
    const unused = diags.find((d) => d.code === DiagnosticCode.UnusedImport);
    expect(unused).toBeDefined();
    expect(unused!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/wildcard-with-no-matches', () => {
  it('emits Warning when a wildcard import targets a package with no definitions', () => {
    const { validator, ast } = setupValidator(
      `package pkg_a
       import pkg_b.*
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'pkg_a/test.ttr'
    );
    const diags = validator.validateImports('pkg_a/test.ttr', ast);
    const noMatch = diags.find((d) => d.code === DiagnosticCode.WildcardWithNoMatches);
    expect(noMatch).toBeDefined();
    expect(noMatch!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/duplicate-import', () => {
  it('emits Warning when the same target is imported twice', () => {
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    manifest.projectRoot = '/test/project/';

    const astA = parseString(
      `package pkg_a
       import pkg_b.entity_x
       import pkg_b.entity_x
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'pkg_a/a.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_a/a.ttr', astA, 'er', 'entity', 'pkg_a');

    const resolver = new Resolver(symbols);
    const validator = new Validator(symbols, resolver, manifest);
    const diags = validator.validateImports('pkg_a/a.ttr', astA);
    const dup = diags.find((d) => d.code === DiagnosticCode.DuplicateImport);
    expect(dup).toBeDefined();
    expect(dup!.severity).toBe('warning');
    expect(dup!.message).toContain('Duplicate import');
  });
});

describe('B4 diagnostics — ttr/circular-package-dependency', () => {
  it('emits Warning when two packages import each other', () => {
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    manifest.projectRoot = '/test/project/';

    const astA = parseString(
      `package pkg_a
       import pkg_b.*
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'pkg_a/a.ttr'
    ).ast!;
    const astB = parseString(
      `package pkg_b
       import pkg_a.*
       schema db namespace dbo
       def table foo { columns: [def column id { type: int }] }`,
      'pkg_b/b.ttr'
    ).ast!;

    symbols.upsertDocument('pkg_a/a.ttr', astA, 'er', 'entity', 'pkg_a');
    symbols.upsertDocument('pkg_b/b.ttr', astB, 'db', 'dbo', 'pkg_b');

    const docs = new Map<string, Document>();
    docs.set('pkg_a/a.ttr', astA);
    docs.set('pkg_b/b.ttr', astB);

    const resolver = new Resolver(symbols);
    const validator = new Validator(symbols, resolver, manifest);
    const pkgGraph = new PackageGraphBuilder(symbols, docs).build();
    const diags = validator.validateCircularDependencies(pkgGraph);
    const circular = diags.find((d) => d.code === DiagnosticCode.CircularPackageDependency);
    expect(circular).toBeDefined();
    expect(circular!.severity).toBe('warning');
    expect(circular!.source.file).not.toBe('');
  });
});

describe('B4 diagnostics — ttr/package-declaration-mismatch', () => {
  it('emits Error when declared package does not match file path', () => {
    const { validator, ast } = setupValidator(
      `package wrong.pkg
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      '/test/project/pkg_a/test.ttr'
    );
    const diags = validator.validatePackageDeclarations('/test/project/pkg_a/test.ttr', ast);
    const mismatch = diags.find((d) => d.code === DiagnosticCode.PackageDeclarationMismatch);
    expect(mismatch).toBeDefined();
    expect(mismatch!.severity).toBe('error');
  });
});

describe('B4 diagnostics — ttr/missing-package-declaration', () => {
  it('emits Info when a file in a subdirectory has no package declaration', () => {
    const { validator, ast } = setupValidator(
      `schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      '/test/project/pkg_a/test.ttr'
    );
    const diags = validator.validatePackageDeclarations('/test/project/pkg_a/test.ttr', ast);
    const missing = diags.find((d) => d.code === DiagnosticCode.MissingPackageDeclaration);
    expect(missing).toBeDefined();
    expect(missing!.severity).toBe('info');
  });

  it('does NOT emit MissingPackageDeclaration for a root-level file', () => {
    const { validator, ast } = setupValidator(
      `schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      '/test/project/main.ttr'
    );
    const diags = validator.validatePackageDeclarations('/test/project/main.ttr', ast);
    const missing = diags.find((d) => d.code === DiagnosticCode.MissingPackageDeclaration);
    expect(missing).toBeUndefined();
  });

  it('does NOT emit MissingPackageDeclaration for a .ttrg graph file in a subdirectory', () => {
    const { validator, ast } = setupValidator(
      `graph all_er { schema: er, objects: [er.entity.artikl] }`,
      '/test/project/graphs/all_er.ttrg'
    );
    const diags = validator.validatePackageDeclarations('/test/project/graphs/all_er.ttrg', ast);
    expect(diags.find((d) => d.code === DiagnosticCode.MissingPackageDeclaration)).toBeUndefined();
  });

  it('emits PackageDeclarationMismatch for a file:// URI (vs file:// scheme)', () => {
    const { validator, ast } = setupValidator(
      `package wrong.pkg
       schema er namespace entity
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'file:///test/project/pkg_a/test.ttr'
    );
    const diags = validator.validatePackageDeclarations('file:///test/project/pkg_a/test.ttr', ast);
    const mismatch = diags.find((d) => d.code === DiagnosticCode.PackageDeclarationMismatch);
    expect(mismatch).toBeDefined();
    expect(mismatch!.severity).toBe('error');
  });
});

describe('B4 diagnostics — ttr/ambiguous-reference', () => {
  it('emits Error when a bare reference matches defs in 2+ wildcard-imported packages', () => {
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    manifest.projectRoot = '/test/project/';

    const astB = parseString(
      `package pkg_b
       schema er namespace entity
       def entity shared_name { attributes: [def attribute id { type: int }] }`,
      'pkg_b/b.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_b/b.ttr', astB, 'er', 'entity', 'pkg_b');

    const astC = parseString(
      `package pkg_c
       schema er namespace entity
       def entity shared_name { attributes: [def attribute id { type: int }] }`,
      'pkg_c/c.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_c/c.ttr', astC, 'er', 'entity', 'pkg_c');

    const astA = parseString(
      `package pkg_a
       import pkg_b.*
       import pkg_c.*
       schema er namespace entity
       def entity artikl {
         attributes: [def attribute id { type: int }]
         nameAttribute: shared_name
       }`,
      'pkg_a/a.ttr'
    ).ast!;
    symbols.upsertDocument('pkg_a/a.ttr', astA, 'er', 'entity', 'pkg_a');

    const resolver = new Resolver(symbols);
    const validator = new Validator(symbols, resolver, manifest);
    const diags = validator.validateReferences('pkg_a/a.ttr', astA);
    const ambiguous = diags.find((d) => d.code === DiagnosticCode.AmbiguousReference);
    expect(ambiguous).toBeDefined();
    expect(ambiguous!.severity).toBe('error');
  });
});

describe('B4 diagnostics — ttr/wrong-file-kind', () => {
  it('emits Error when a .ttr file contains both a graph block and definitions', () => {
    const result = parseString(
      `graph test { schema: er }
       def entity artikl { attributes: [def attribute id { type: int }] }`,
      'test.ttr'
    );
    const wrongKind = result.errors.find((e) => e.code === DiagnosticCode.WrongFileKind);
    expect(wrongKind).toBeDefined();
    expect(wrongKind!.severity).toBe('error');
  });
});

describe('C1 — ttr/graph-schema-required', () => {
  it('emits Error when graph block omits schema', () => {
    const result = parseString(`graph test { objects: [] }`, 'test.ttrg');
    const ast = result.ast!;
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    const resolver = new Resolver(symbols);
    const v = new Validator(symbols, resolver, manifest);
    const diags = v.validateTtrgGraph('test.ttrg', ast);
    const schemaMissing = diags.find((d) => d.code === DiagnosticCode.RequiredPropertyMissing && d.message.includes("graph requires a 'schema'"));
    expect(schemaMissing).toBeDefined();
    expect(schemaMissing!.severity).toBe('error');
  });
});

describe('B4 diagnostics — ttr/graph-object-not-found', () => {
  it('emits Warning when a graph objects entry does not resolve', () => {
    const result = parseString(
      `graph test { schema: er, objects: [er.entity.nonexistent] }`,
      'test.ttrg'
    );
    const ast = result.ast!;
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    const resolver = new Resolver(symbols);
    const v = new Validator(symbols, resolver, manifest);
    const diags = v.validateTtrgGraph('test.ttrg', ast);
    const notFound = diags.find((d) => d.code === DiagnosticCode.GraphObjectNotFound);
    expect(notFound).toBeDefined();
    expect(notFound!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/graph-layout-stale-node', () => {
  it('emits Warning when a layout node references a qname not in objects', () => {
    const result = parseString(
      `graph test {
         schema: er,
         objects: [er.entity.artikl],
         layout: { nodes: { 'some_unknown_object': { x: 0, y: 0 } } }
       }`,
      'test.ttrg'
    );
    const ast = result.ast!;
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    const resolver = new Resolver(symbols);
    const v = new Validator(symbols, resolver, manifest);
    const diags = v.validateTtrgGraph('test.ttrg', ast);
    const stale = diags.find((d) => d.code === DiagnosticCode.GraphLayoutStaleNode);
    expect(stale).toBeDefined();
    expect(stale!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/graph-objects-empty', () => {
  it('emits Warning when objects list is empty', () => {
    const result = parseString(
      `graph test { schema: er, objects: [] }`,
      'test.ttrg'
    );
    const ast = result.ast!;
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    const resolver = new Resolver(symbols);
    const v = new Validator(symbols, resolver, manifest);
    const diags = v.validateTtrgGraph('test.ttrg', ast);
    const empty = diags.find((d) => d.code === DiagnosticCode.GraphObjectsEmpty);
    expect(empty).toBeDefined();
    expect(empty!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/graph-name-mismatch', () => {
  it('emits Warning when graph name does not match filename', () => {
    const result = parseString(
      `graph wrong_name { schema: er, objects: [] }`,
      'test.ttrg'
    );
    const ast = result.ast!;
    const symbols = new ProjectSymbolTable();
    const manifest = makeManifest();
    const resolver = new Resolver(symbols);
    const v = new Validator(symbols, resolver, manifest);
    const diags = v.validateTtrgGraph('test.ttrg', ast);
    const mismatch = diags.find((d) => d.code === DiagnosticCode.GraphNameMismatch);
    expect(mismatch).toBeDefined();
    expect(mismatch!.severity).toBe('warning');
  });
});

describe('B4 diagnostics — ttr/file-ordering', () => {
  function makeSrcLocation(line: number): SourceLocation {
    return { file: 'test.ttr', line, column: 0, endLine: line, endColumn: 0, offsetStart: 0, offsetEnd: 0 };
  }

  it('emits Warning when imports appear after schema directive', () => {
    const { validator } = setupValidator(`schema er namespace entity`, 'test.ttr');
    const ast: Document = {
      packageDecl: undefined,
      imports: [{ kind: 'importDecl', target: 'pkg_b', targetParts: ['pkg_b'], wildcard: true, source: makeSrcLocation(2) }],
      schemaDirective: { schemaCode: 'er', namespace: 'entity', source: makeSrcLocation(1) },
      definitions: [],
      source: makeSrcLocation(1),
    };
    const diags = validator.validateFileOrdering('test.ttr', ast);
    const ordering = diags.find((d) => d.code === DiagnosticCode.FileOrdering);
    expect(ordering).toBeDefined();
    expect(ordering!.message).toContain('import declarations must appear before schema directive');
    expect(ordering!.severity).toBe('warning');
  });

  it('returns empty array for canonical order (package, imports, schema, defs)', () => {
    const { validator } = setupValidator(`package pkg_a`, 'test.ttr');
    const ast: Document = {
      packageDecl: { kind: 'packageDecl', name: 'pkg_a', parts: ['pkg_a'], source: makeSrcLocation(1) },
      imports: [{ kind: 'importDecl', target: 'pkg_b', targetParts: ['pkg_b'], wildcard: true, source: makeSrcLocation(2) }],
      schemaDirective: { schemaCode: 'er', namespace: 'entity', source: makeSrcLocation(3) },
      definitions: [{ kind: 'EntityDef', name: 'artikl', attributes: [], source: makeSrcLocation(4) }] as unknown as Definition[],
      source: makeSrcLocation(1),
    };
    const diags = validator.validateFileOrdering('test.ttr', ast);
    expect(diags).toHaveLength(0);
  });

  it('emits Warning when schema directive appears after definitions (inverted order)', () => {
    const { validator } = setupValidator(`def entity artikl { }`, 'test.ttr');
    const ast: Document = {
      packageDecl: undefined,
      imports: [],
      schemaDirective: { schemaCode: 'er', namespace: 'entity', source: makeSrcLocation(5) },
      definitions: [{ kind: 'EntityDef', name: 'artikl', attributes: [], source: makeSrcLocation(2) }] as unknown as Definition[],
      source: makeSrcLocation(1),
    };
    const diags = validator.validateFileOrdering('test.ttr', ast);
    const ordering = diags.find((d) => d.code === DiagnosticCode.FileOrdering);
    expect(ordering).toBeDefined();
    expect(ordering!.message).toContain('schema directive must appear before definitions');
    expect(ordering!.severity).toBe('warning');
  });
});