import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@modeler/parser';
import { applyWorkspaceEditToText } from '@modeler/edit';
import {
  ProjectSymbolTable,
  Resolver,
  resolveManifest,
  collectAllReferences,
  synthesizeMappings,
} from '@modeler/semantics';
import { lintDocument } from '../runner.js';
import { ruleForCode } from '../registry.js';
import { collectSafeFixes } from '../fix.js';
import { recommendedConfig } from '../config.js';
import type { DocumentRuleContext } from '../rule.js';

const PROJECT_ROOT = '/proj';

function buildSymbols(files: Array<{ uri: string; src: string }>) {
  const symbols = new ProjectSymbolTable();
  for (const { uri, src } of files) {
    const ast = parseString(src, uri).ast!;
    symbols.upsertDocument(uri, ast, ast.schemaDirective?.schemaCode ?? '', ast.schemaDirective?.namespace ?? '', ast.packageDecl?.name ?? '');
    synthesizeMappings(symbols, uri, ast);
  }
  return symbols;
}

function docCtx(uri: string, src: string, symbols: ProjectSymbolTable): { ctx: DocumentRuleContext; deps: { manifest: ReturnType<typeof resolveManifest>; symbols: ProjectSymbolTable; resolver: Resolver } } {
  const ast = parseString(src, uri).ast!;
  const resolver = new Resolver(symbols);
  const manifest = resolveManifest(undefined, PROJECT_ROOT);
  const deps = { manifest, symbols, resolver };
  const ctx: DocumentRuleContext = {
    scope: 'document', uri, ast, text: src,
    refs: collectAllReferences(ast), manifest, symbols, resolver,
    report: () => {},
  };
  return { ctx, deps };
}

describe('collectSafeFixes', () => {
  it('includes only safe fixes and merges non-overlapping edits', () => {
    const uri = '/proj/sub/a.ttr';
    const src = `package sub\nimport other.db.dbo.one\nimport other.db.dbo.two\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const symbols = buildSymbols([{ uri, src }]);
    const { ctx, deps } = docCtx(uri, src, symbols);
    const diags = lintDocument(uri, ctx.ast, deps, recommendedConfig());
    const result = collectSafeFixes(diags, ctx);
    // Both unused imports are safe-fixable and don't overlap.
    expect(result.applied.length).toBe(2);
    expect(result.deferred.length).toBe(0);
    const out = applyWorkspaceEditToText(src, result.edit, uri);
    expect(out).not.toContain('import other.db.dbo.one');
    expect(out).not.toContain('import other.db.dbo.two');
  });

  it('does not include suggestion fixes', () => {
    // A package mismatch (suggestion) yields no safe fix.
    const uri = '/proj/sub/a.ttr';
    const src = `package wrong.pkg\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const symbols = buildSymbols([{ uri, src }]);
    const { ctx, deps } = docCtx(uri, src, symbols);
    const diags = lintDocument(uri, ctx.ast, deps, recommendedConfig());
    expect(diags.some((d) => d.code === DiagnosticCode.PackageDeclarationMismatch)).toBe(true);
    const result = collectSafeFixes(diags, ctx);
    expect(result.applied.length).toBe(0);
  });
});

describe('--fix fixpoint loop', () => {
  it('applies safe fixes to a fixpoint and is idempotent', () => {
    const uri = '/proj/sub/a.ttr';
    let text = `package sub\nimport other.db.dbo.one\nimport other.db.dbo.two\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }\n`;
    const symbols = buildSymbols([{ uri, src: text }]);

    const runPass = (src: string): { next: string; applied: number } => {
      const { ctx, deps } = docCtx(uri, src, symbols);
      const diags = lintDocument(uri, ctx.ast, deps, recommendedConfig());
      const result = collectSafeFixes(diags, ctx);
      return { next: applyWorkspaceEditToText(src, result.edit, uri), applied: result.applied.length };
    };

    let passes = 0;
    for (;;) {
      const { next, applied } = runPass(text);
      text = next;
      passes++;
      if (applied === 0 || passes > 10) break;
    }
    expect(passes).toBeLessThanOrEqual(10);
    expect(text).not.toContain('import other.db.dbo.one');
    expect(text).not.toContain('import other.db.dbo.two');

    // Idempotent: a further pass changes nothing.
    const { next, applied } = runPass(text);
    expect(applied).toBe(0);
    expect(next).toBe(text);
  });
});

describe('unimported-reference safe fix (cross-package)', () => {
  it('inserts the missing import', () => {
    const other = { uri: '/proj/other/o.ttr', src: `package other\nschema er namespace ent\ndef entity thing { attributes: [def attribute id { type: int }] }` };
    const main = { uri: '/proj/app/a.ttr', src: `package app\nschema er namespace ent\ndef entity artikl { attributes: [def attribute id { type: int }] }\ndef er2db_relation r { relation: other.er.ent.thing }` };
    const symbols = buildSymbols([other, main]);
    const { ctx, deps } = docCtx(main.uri, main.src, symbols);
    const diags = lintDocument(main.uri, ctx.ast, deps, recommendedConfig());
    const diag = diags.find((d) => d.code === DiagnosticCode.UnimportedReference);
    expect(diag).toBeDefined();
    const ws = ruleForCode(DiagnosticCode.UnimportedReference)!.fix!.build(ctx, diag!);
    const out = applyWorkspaceEditToText(main.src, ws, main.uri);
    expect(out).toContain('import other');
  });
});
