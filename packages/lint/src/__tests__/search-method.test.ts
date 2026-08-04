// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import { lintOne } from './helpers.js';

// RV-P1.5 T4 — the wiring test. The MEANING of the match method is tested in
// `@tatrman/semantics`; here we only prove the three checks reach the lint stream
// with the right rule ids, codes and severities, which is what puts them in the
// portable cross-target conformance subset.

function rulesOf(diags: { ruleId: string }[]): string[] {
  return diags.map((d) => d.ruleId);
}

describe('search method rules', () => {
  it('fuzzy-deprecated fires on a legacy `fuzzy` and carries the migration hint', () => {
    const d = lintOne('db.ttrm', 'model db schema dbo\ndef table t { columns: [def column c { type: varchar }] search { searchable: true, fuzzy: true } }');
    const f = d.find((x) => x.ruleId === 'fuzzy-deprecated');
    expect(f).toBeDefined();
    expect(f!.code).toBe(DiagnosticCode.SearchFuzzyDeprecated);
    expect(f!.severity).toBe('warning');
    expect(f!.message).toContain('searchable method: TYPOS(1)');
  });

  it('the 0.12 form fires nothing', () => {
    const d = lintOne('db.ttrm', 'model db schema dbo\ndef table t { columns: [def column c { type: varchar }] search { searchable method: TYPOS(2) } }');
    expect(rulesOf(d)).not.toContain('fuzzy-deprecated');
    expect(rulesOf(d)).not.toContain('unknown-match-method');
  });

  it('unknown-match-method is an error', () => {
    const d = lintOne('db.ttrm', 'model db schema dbo\ndef table t { columns: [def column c { type: varchar }] search { searchable method: TYPSO } }');
    const f = d.find((x) => x.ruleId === 'unknown-match-method');
    expect(f).toBeDefined();
    expect(f!.severity).toBe('error');
  });

  it('invalid-match-method-argument is an error', () => {
    const d = lintOne('db.ttrm', 'model db schema dbo\ndef table t { columns: [def column c { type: varchar }] search { searchable method: EXACT(2) } }');
    const f = d.find((x) => x.ruleId === 'invalid-match-method-argument');
    expect(f).toBeDefined();
    expect(f!.code).toBe(DiagnosticCode.InvalidMatchMethodArgument);
  });
});
