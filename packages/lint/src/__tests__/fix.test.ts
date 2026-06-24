import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { applyWorkspaceEditToText } from '@modeler/edit';
import {
  ProjectSymbolTable,
  Resolver,
  resolveManifest,
  synthesizeMappings,
} from '@modeler/semantics';
import { lintDocument } from '../runner.js';
import { ruleForCode } from '../registry.js';
import { recommendedConfig } from '../config.js';
import type { DocumentRuleContext, LintDiagnostic } from '../rule.js';
import { DiagnosticCode } from '@modeler/parser';

const URI = '/proj/sub/a.ttrm';

/** Build a document fix-context (with text) plus the diagnostics for `src`. */
function setup(src: string, uri = URI, projectRoot = '/proj') {
  const ast = parseString(src, uri).ast!;
  const symbols = new ProjectSymbolTable();
  symbols.upsertDocument(uri, ast, ast.schemaDirective?.schemaCode ?? '', ast.schemaDirective?.namespace ?? '', ast.packageDecl?.name ?? '');
  synthesizeMappings(symbols, uri, ast);
  const resolver = new Resolver(symbols);
  const manifest = resolveManifest(undefined, projectRoot);
  const deps = { manifest, symbols, resolver };
  const config = recommendedConfig({ overrides: { 'missing-description': 'off' } });
  const diags = lintDocument(uri, ast, deps, config);
  const ctx: DocumentRuleContext = {
    scope: 'document', uri, ast, text: src,
    refs: [], manifest, symbols, resolver,
    report: () => {},
  };
  return { diags, ctx };
}

/** Apply a rule's safe fix for one diagnostic and return the resulting text. */
function applyFix(src: string, code: DiagnosticCode, uri = URI, projectRoot = '/proj'): string {
  const { diags, ctx } = setup(src, uri, projectRoot);
  const diag = diags.find((d) => d.code === code) as LintDiagnostic;
  expect(diag, `expected a ${code} diagnostic`).toBeDefined();
  const rule = ruleForCode(code)!;
  const ws = rule.fix!.build(ctx, diag);
  return applyWorkspaceEditToText(src, ws, uri);
}

describe('safe fixes produce the expected text', () => {
  it('unused-import removes the import line', () => {
    const src = `package sub\nimport other.db.dbo.thing\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const out = applyFix(src, DiagnosticCode.UnusedImport);
    expect(out).not.toContain('import other.db.dbo.thing');
    expect(out).toContain('def table t');
  });

  it('duplicate-import removes one of the duplicate lines', () => {
    const src = `package sub\nimport other.db.dbo.thing\nimport other.db.dbo.thing\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const out = applyFix(src, DiagnosticCode.DuplicateImport);
    expect((out.match(/import other\.db\.dbo\.thing/g) ?? []).length).toBe(1);
  });

  it('missing-package-declaration inserts the inferred package', () => {
    const src = `schema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const out = applyFix(src, DiagnosticCode.MissingPackageDeclaration);
    expect(out.startsWith('package sub\n')).toBe(true);
  });

  it('fuzzy-without-searchable inserts searchable: true', () => {
    const src = `package sub\nschema er namespace ent\ndef entity e {\n attributes: [def attribute id { type: int }]\n search: { fuzzy: true }\n}\n`;
    const out = applyFix(src, DiagnosticCode.FuzzyWithoutSearchable);
    expect(out).toContain('searchable: true');
    // The fixed source must parse and no longer warn.
    const re = parseString(out, URI);
    expect(re.errors.filter((e) => e.severity === 'error')).toEqual([]);
  });

  it('graph-layout-stale-node drops the stale layout entry', () => {
    const src = `graph a {\n schema: er,\n objects: [er.entity.x],\n layout: { nodes: { 'er.entity.x': { x: 0, y: 0 }, 'er.entity.stale': { x: 1, y: 1 } } }\n}\n`;
    const out = applyFix(src, DiagnosticCode.GraphLayoutStaleNode, '/proj/a.ttrg', '/proj');
    expect(out).not.toContain('er.entity.stale');
    expect(out).toContain('er.entity.x');
  });
});

describe('judgment fixes are suggestions, not safe', () => {
  const SUGGESTIONS: DiagnosticCode[] = [
    DiagnosticCode.AmbiguousReference,
    DiagnosticCode.PackageDeclarationMismatch,
    DiagnosticCode.GraphNameMismatch,
    DiagnosticCode.DuplicateSearchProperty,
  ];
  for (const code of SUGGESTIONS) {
    it(`${code} fix is kind: 'suggestion'`, () => {
      const rule = ruleForCode(code)!;
      expect(rule.fix?.kind).toBe('suggestion');
    });
  }

  it('the safe-fix subset is kind: \'safe\'', () => {
    const SAFE: DiagnosticCode[] = [
      DiagnosticCode.UnusedImport,
      DiagnosticCode.DuplicateImport,
      DiagnosticCode.WildcardWithNoMatches,
      DiagnosticCode.UnimportedReference,
      DiagnosticCode.MissingPackageDeclaration,
      DiagnosticCode.FuzzyWithoutSearchable,
      DiagnosticCode.GraphLayoutStaleNode,
    ];
    for (const code of SAFE) expect(ruleForCode(code)?.fix?.kind).toBe('safe');
  });
});
