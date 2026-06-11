import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { Validator } from '../validator.js';
import { resolveManifest } from '../manifest.js';
import { inferPackageFromUri } from '../package-inference.js';

const PROJECT_ROOT = '/proj';

function setup(uri: string, src: string) {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
  const namespace = ast.schemaDirective?.namespace ?? '';
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument(uri, ast, schemaCode, namespace);
  const resolver = new Resolver(symbols);
  const manifest = resolveManifest(undefined, PROJECT_ROOT);
  const validator = new Validator(symbols, resolver, manifest);
  return { ast, validator };
}

const ENTITY = 'def entity X { attributes: [def attribute id { type: int }] }';

describe('Item 1 — validatePackageDeclarations', () => {
  it('1.2 root file, no package ⇒ no diagnostic', () => {
    const uri = '/proj/main.ttr';
    // Sanity: this URI is classified as a root file.
    expect(inferPackageFromUri(uri, PROJECT_ROOT).isRootFile).toBe(true);
    const { validator, ast } = setup(uri, `schema er namespace entity\n${ENTITY}`);
    expect(validator.validatePackageDeclarations(uri, ast)).toHaveLength(0);
  });

  it('1.3 non-root file, no package ⇒ info MissingPackageDeclaration naming the inferred package', () => {
    const uri = '/proj/billing/invoicing/x.ttr';
    const { inferred, isRootFile } = inferPackageFromUri(uri, PROJECT_ROOT);
    expect(isRootFile).toBe(false);
    expect(inferred).toBe('billing.invoicing');
    const { validator, ast } = setup(uri, `schema er namespace entity\n${ENTITY}`);
    const diags = validator.validatePackageDeclarations(uri, ast);
    expect(diags).toHaveLength(1);
    expect(diags[0].code).toBe(DiagnosticCode.MissingPackageDeclaration);
    expect(diags[0].severity).toBe('info');
    expect(diags[0].message).toContain('billing.invoicing');
  });

  it('1.4 .ttrg file is exempt ⇒ no package diagnostic', () => {
    const uri = '/proj/billing/invoicing/main.ttrg';
    const { validator, ast } = setup(
      uri,
      'graph main { schema: er, objects: [er.entity.X] }\n'
    );
    expect(validator.validatePackageDeclarations(uri, ast)).toHaveLength(0);
  });

  it('1.5 declared ≠ inferred ⇒ error PackageDeclarationMismatch', () => {
    const uri = '/proj/x/y/file.ttr';
    expect(inferPackageFromUri(uri, PROJECT_ROOT).inferred).toBe('x.y');
    const { validator, ast } = setup(uri, `package z\nschema er namespace entity\n${ENTITY}`);
    const diags = validator.validatePackageDeclarations(uri, ast);
    expect(diags).toHaveLength(1);
    expect(diags[0].code).toBe(DiagnosticCode.PackageDeclarationMismatch);
    expect(diags[0].severity).toBe('error');
  });
});
