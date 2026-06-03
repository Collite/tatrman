import { describe, it, expect, beforeAll } from 'vitest';
import { parseFile, DiagnosticCode } from '@modeler/parser';
import { ProjectSymbolTable, Resolver, Validator, resolveManifest, PackageGraphBuilder, synthesizeMappings } from '@modeler/semantics';
import path from 'path';
import { readdirSync } from 'fs';

const brokenDir = path.resolve(__dirname, '../../../samples/broken/v2.1');

async function collectFixtureCodes(rootDir: string): Promise<Map<string, Set<string>>> {
  const fs = await import('fs/promises');
  const root = rootDir.endsWith('/') ? rootDir : rootDir + '/';
  const files = (await fs.readdir(root, { withFileTypes: true }))
    .filter((e) => e.isFile() && e.name.endsWith('.ttr'))
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
      result.ast.schemaDirective?.schemaCode ?? 'db',
      result.ast.schemaDirective?.namespace ?? '',
      result.ast.packageDecl?.name ?? '',
    );
    synthesizeMappings(symbols, uri, result.ast);
  }

  const validator = new Validator(symbols, new Resolver(symbols), resolveManifest(undefined, root));
  const packageGraph = new PackageGraphBuilder(
    symbols,
    new Map([...asts].map(([uri, v]) => [uri, v.ast])),
  ).build();

  const byFile = new Map<string, Set<string>>();
  for (const file of files) {
    const uri = `file://${file}`;
    const entry = asts.get(uri);
    if (!entry) continue;
    const codes = new Set<string>();
    for (const e of entry.errors) codes.add(e.code);
    for (const d of validator.validateDocument(uri, entry.ast)) codes.add(d.code);
    for (const d of validator.validateReferences(uri, entry.ast)) codes.add(d.code);
    for (const d of validator.validateImports(uri, entry.ast)) codes.add(d.code);
    for (const d of validator.validateFileOrdering(uri, entry.ast)) codes.add(d.code);
    for (const d of validator.validatePackageDeclarations(uri, entry.ast)) codes.add(d.code);
    for (const d of validator.validateCircularDependencies(packageGraph)) if (d.source.file === file) codes.add(d.code);
    for (const d of validator.validateProject()) if (d.source.file === file) codes.add(d.code);
    byFile.set(path.relative(root, file), codes);
  }
  return byFile;
}

describe('samples/broken/v2.1 — duplicate-mapping fixtures produce ttr/duplicate-mapping', () => {
  const fixtureDirs = ['duplicate-mapping-entity', 'duplicate-mapping-attribute', 'duplicate-mapping-relation', 'duplicate-mapping-mixed'];

  for (const fixtureDir of fixtureDirs) {
    describe(fixtureDir, () => {
      let codes: Map<string, Set<string>>;

      beforeAll(async () => {
        codes = await collectFixtureCodes(path.join(brokenDir, fixtureDir));
      });

      it('er.ttr emits ttr/duplicate-mapping', () => {
        const erCodes = codes.get('er.ttr') ?? new Set<string>();
        expect(erCodes.has('ttr/duplicate-mapping')).toBe(true);
      });

      it('map.ttr emits ttr/duplicate-mapping', () => {
        const mapCodes = codes.get('map.ttr') ?? new Set<string>();
        expect(mapCodes.has('ttr/duplicate-mapping')).toBe(true);
      });

      it('db.ttr emits no diagnostics', () => {
        const dbCodes = codes.get('db.ttr') ?? new Set<string>();
        expect(dbCodes).toEqual(new Set<string>());
      });
    });
  }
});