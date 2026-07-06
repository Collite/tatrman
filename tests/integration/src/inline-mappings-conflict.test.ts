import { describe, it, expect, beforeAll } from 'vitest';
import { parseFile, DiagnosticCode } from '@tatrman/parser';
import { ProjectSymbolTable, Resolver, resolveManifest, PackageGraphBuilder, synthesizeMappings } from '@tatrman/semantics';
import { lintDocument, lintProject, recommendedConfig } from '@tatrman/lint';
import path from 'path';
import { readdirSync } from 'fs';

const brokenDir = path.resolve(__dirname, '../../../samples/broken/v2.1');

async function collectFixtureCodes(rootDir: string): Promise<Map<string, Set<string>>> {
  const fs = await import('fs/promises');
  const root = rootDir.endsWith('/') ? rootDir : rootDir + '/';
  const files = (await fs.readdir(root, { withFileTypes: true }))
    .filter((e) => e.isFile() && e.name.endsWith('.ttrm'))
    .map((e) => path.join(root, e.name));

  const symbols = new ProjectSymbolTable();
  const asts = new Map<string, { ast: any; errors: any[] }>();

  for (const file of files) {
    const uri = `file://${file}`;
    const result = await parseFile(file);
    if (!result.ast) continue;
    asts.set(uri, { ast: result.ast, errors: result.errors });
    symbols.upsertDocument(
      uri,
      result.ast,
      result.ast.modelDirective?.modelCode ?? 'db',
      result.ast.modelDirective?.schema ?? '',
      result.ast.packageDecl?.name ?? '',
    );
    synthesizeMappings(symbols, uri, result.ast);
  }

  const deps = { manifest: resolveManifest(undefined, root), symbols, resolver: new Resolver(symbols) };
  const docs = new Map([...asts].map(([uri, v]) => [uri, v.ast]));
  const packageGraph = new PackageGraphBuilder(symbols, docs).build();
  const config = recommendedConfig();
  // Project diagnostics carry the AST node's source.file (here the plain path,
  // since these fixtures are parsed via parseFile); flatten + filter by it.
  const projectDiags = [...lintProject(docs, packageGraph, deps, config).values()].flat();

  const byFile = new Map<string, Set<string>>();
  for (const file of files) {
    const uri = `file://${file}`;
    const entry = asts.get(uri);
    if (!entry) continue;
    const codes = new Set<string>();
    for (const e of entry.errors) codes.add(e.code);
    for (const d of lintDocument(uri, entry.ast, deps, config)) codes.add(d.code);
    for (const d of projectDiags) if (d.source.file === file) codes.add(d.code);
    byFile.set(path.relative(root, file), codes);
  }
  return byFile;
}

describe('samples/broken/v2.1 — duplicate-mapping fixtures produce ttr/duplicate-binding', () => {
  const fixtureDirs = ['duplicate-mapping-entity', 'duplicate-mapping-attribute', 'duplicate-mapping-relation', 'duplicate-mapping-mixed'];

  for (const fixtureDir of fixtureDirs) {
    describe(fixtureDir, () => {
      let codes: Map<string, Set<string>>;

      beforeAll(async () => {
        codes = await collectFixtureCodes(path.join(brokenDir, fixtureDir));
      });

      it('er.ttrm emits ttr/duplicate-binding', () => {
        const erCodes = codes.get('er.ttrm') ?? new Set<string>();
        expect(erCodes.has('ttr/duplicate-binding')).toBe(true);
      });

      it('map.ttrm emits ttr/duplicate-binding', () => {
        const mapCodes = codes.get('map.ttrm') ?? new Set<string>();
        expect(mapCodes.has('ttr/duplicate-binding')).toBe(true);
      });

      it('db.ttrm emits no diagnostics', () => {
        const dbCodes = codes.get('db.ttrm') ?? new Set<string>();
        expect(dbCodes).toEqual(new Set<string>());
      });
    });
  }
});