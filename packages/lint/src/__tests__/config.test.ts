// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { resolveLintConfig, parseLintConfig } from '../config.js';
import { RULES } from '../registry.js';

describe('config precedence + presets', () => {
  it('default (recommended): rule defaults, missing-description off', () => {
    const c = resolveLintConfig({ extends: 'recommended' });
    expect(c.severityOf('table-no-columns')).toBe('error');
    expect(c.severityOf('unresolved-reference')).toBe('warning');
    expect(c.severityOf('missing-description')).toBe('off');
    // a correctness rule whose default is warning stays warning under recommended
    expect(c.severityOf('fuzzy-without-searchable')).toBe('warning');
  });

  it('strict preset: unresolved=error, missing-description=warning', () => {
    const c = resolveLintConfig({ extends: 'strict' });
    expect(c.severityOf('unresolved-reference')).toBe('error');
    expect(c.severityOf('missing-description')).toBe('warning');
  });

  it('all preset: every rule error', () => {
    const c = resolveLintConfig({ extends: 'all' });
    for (const id of RULES.keys()) expect(c.severityOf(id)).toBe('error');
  });

  it('none preset: every rule off except correctness (clamped to error)', () => {
    const c = resolveLintConfig({ extends: 'none' });
    expect(c.severityOf('missing-description')).toBe('off');
    expect(c.severityOf('unused-import')).toBe('off');
    // correctness clamps up to error even under none
    expect(c.severityOf('table-no-columns')).toBe('error');
    expect(c.severityOf('fuzzy-without-searchable')).toBe('error');
    expect(c.severityOf('duplicate-definition')).toBe('error');
  });

  it('precedence: [rules] beats [categories] beats preset', () => {
    const c = resolveLintConfig({
      extends: 'recommended',
      categories: { imports: 'error' },
      rules: { 'unused-import': 'off' },
    });
    // category raised imports to error...
    expect(c.severityOf('duplicate-import')).toBe('error');
    // ...but the explicit rule wins for unused-import
    expect(c.severityOf('unused-import')).toBe('off');
  });
});

describe('config clamp + back-compat + unknowns', () => {
  it('clamps a correctness rule requested below error and reports clamped-severity', () => {
    const c = resolveLintConfig({ rules: { 'table-no-columns': 'warning' } });
    expect(c.severityOf('table-no-columns')).toBe('error');
    expect(c.diagnostics.some((d) => d.code === 'ttrlint/clamped-severity')).toBe(true);
  });

  it('modeler.toml [lint].strict=true (no .ttrlint.toml) ⇒ strict-preset behaviour', () => {
    const c = resolveLintConfig(undefined, { strict: true });
    expect(c.severityOf('unresolved-reference')).toBe('error');
  });

  it('modeler.toml [lint].requireDescriptions=true ⇒ missing-description=warning', () => {
    const c = resolveLintConfig(undefined, { requireDescriptions: true });
    expect(c.severityOf('missing-description')).toBe('warning');
  });

  it('both .ttrlint.toml and legacy [lint] present ⇒ file wins + deprecation diagnostic', () => {
    const c = resolveLintConfig({ extends: 'recommended' }, { strict: true });
    // file wins → unresolved stays warning (recommended), not error (legacy strict)
    expect(c.severityOf('unresolved-reference')).toBe('warning');
    expect(c.diagnostics.some((d) => d.code === 'ttrlint/deprecated-lint-config')).toBe(true);
  });

  it('unknown rule id / category are reported', () => {
    const c = resolveLintConfig({ rules: { 'no-such-rule': 'error' }, categories: { bogus: 'off' } as never });
    expect(c.diagnostics.some((d) => d.code === 'ttrlint/unknown-rule')).toBe(true);
    expect(c.diagnostics.some((d) => d.code === 'ttrlint/unknown-category')).toBe(true);
  });

  it('failOn and applyFixes come from [cli]/[fix]', () => {
    const c = resolveLintConfig({ cli: { 'fail-on': 'warning' }, fix: { apply: 'none' } });
    expect(c.failOn).toBe('warning');
    expect(c.applyFixes).toBe('none');
  });

  it('parseLintConfig parses TOML into a RawLintConfig', () => {
    const raw = parseLintConfig(`extends = "strict"\n[rules]\nunused-import = "off"\n`);
    expect(raw.extends).toBe('strict');
    expect(raw.rules?.['unused-import']).toBe('off');
  });
});
