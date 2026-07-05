import { describe, it, expect, vi } from 'vitest';
import { parseString } from '@tatrman/parser';
import type { SourceLocation, Document } from '@tatrman/parser';
import { DiagnosticCode } from '@tatrman/parser';
import { lintDocument, lintProject, type LintDeps } from '../runner.js';
import type { ResolvedLintConfig } from '../config.js';
import type { Rule, RuleId, Severity } from '../rule.js';
import type { PackageGraph } from '@tatrman/semantics';

// Stub rules don't read manifest/symbols/resolver, so a structural stub suffices.
const DEPS = {} as unknown as LintDeps;

function configFrom(map: Record<string, Severity>): ResolvedLintConfig {
  return {
    severityOf: (id: RuleId): Severity => map[id] ?? 'warning',
    failOn: 'error',
    applyFixes: 'safe',
    diagnostics: [],
  };
}

function loc(file: string, line = 1): SourceLocation {
  return { file, line, column: 0, endLine: line, endColumn: 1, offsetStart: 0, offsetEnd: 1 };
}

function parse(src: string, uri: string): Document {
  const r = parseString(src, uri);
  if (!r.ast) throw new Error('parse failed');
  return r.ast;
}

describe('lint runner', () => {
  it('stamps severity from config on reported diagnostics', () => {
    const ast = parse('def project m {}', 'a.ttrm');
    const rule: Rule = {
      id: 'always-report',
      code: DiagnosticCode.RequiredPropertyMissing,
      category: 'style',
      scope: 'document',
      defaultSeverity: 'warning',
      docs: '',
      check: (ctx) => ctx.report({ source: loc('a.ttrm'), message: 'boom' }),
    };
    const diags = lintDocument('a.ttrm', ast, DEPS, configFrom({ 'always-report': 'error' }), [rule]);
    expect(diags).toHaveLength(1);
    expect(diags[0].severity).toBe('error');
    expect(diags[0].ruleId).toBe('always-report');
    expect(diags[0].code).toBe(DiagnosticCode.RequiredPropertyMissing);
    expect(diags[0].message).toBe('boom');
  });

  it('never invokes an off rule', () => {
    const ast = parse('def project m {}', 'a.ttrm');
    const check = vi.fn();
    const offRule: Rule = {
      id: 'off-rule',
      code: DiagnosticCode.FileOrdering,
      category: 'style',
      scope: 'document',
      defaultSeverity: 'warning',
      docs: '',
      check,
    };
    const diags = lintDocument('a.ttrm', ast, DEPS, configFrom({ 'off-rule': 'off' }), [offRule]);
    expect(check).not.toHaveBeenCalled();
    expect(diags).toHaveLength(0);
  });

  it('runs a project rule once and buckets results by uri', () => {
    const astA = parse('def project a {}', 'a.ttrm');
    const astB = parse('def project b {}', 'b.ttrm');
    const documents = new Map<string, Document>([
      ['a.ttrm', astA],
      ['b.ttrm', astB],
    ]);
    const check = vi.fn((ctx) => {
      ctx.report({ source: loc('a.ttrm'), message: 'on a' });
      ctx.report({ source: loc('b.ttrm'), message: 'on b' });
    });
    const projectRule: Rule = {
      id: 'project-rule',
      code: DiagnosticCode.DuplicateDefinition,
      category: 'correctness',
      scope: 'project',
      defaultSeverity: 'error',
      docs: '',
      check,
    };
    const result = lintProject(
      documents,
      {} as unknown as PackageGraph,
      DEPS,
      configFrom({ 'project-rule': 'error' }),
      [projectRule]
    );
    expect(check).toHaveBeenCalledTimes(1);
    expect(result.get('a.ttrm')).toHaveLength(1);
    expect(result.get('b.ttrm')).toHaveLength(1);
    expect(result.get('a.ttrm')![0].message).toBe('on a');
    expect(result.get('b.ttrm')![0].severity).toBe('error');
  });

  it('skips document rules when running a project and vice versa', () => {
    const ast = parse('def project m {}', 'a.ttrm');
    const docCheck = vi.fn((ctx) => ctx.report({ source: loc('a.ttrm'), message: 'd' }));
    const docRule: Rule = {
      id: 'doc-only',
      code: DiagnosticCode.RequiredPropertyMissing,
      category: 'style',
      scope: 'document',
      defaultSeverity: 'warning',
      docs: '',
      check: docCheck,
    };
    // A document run ignores project rules; a project run ignores document rules.
    const docResult = lintDocument('a.ttrm', ast, DEPS, configFrom({ 'doc-only': 'warning' }), [docRule]);
    expect(docResult).toHaveLength(1);

    const projResult = lintProject(
      new Map([['a.ttrm', ast]]),
      {} as unknown as PackageGraph,
      DEPS,
      configFrom({ 'doc-only': 'warning' }),
      [docRule]
    );
    expect([...projResult.values()].flat()).toHaveLength(0);
  });
});
