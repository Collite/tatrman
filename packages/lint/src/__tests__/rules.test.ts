import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintOne, lintProj, lintDocInProject, recommendedConfig } from './helpers.js';

function rulesOf(diags: { ruleId: string }[]): string[] {
  return diags.map((d) => d.ruleId);
}

describe('structure rules', () => {
  it('entity-no-attributes on an empty entity', () => {
    const d = lintOne('er.ttr', `schema er namespace entity\ndef entity empty { description: "x" }`);
    expect(rulesOf(d)).toContain('entity-no-attributes');
    expect(d.find((x) => x.ruleId === 'entity-no-attributes')!.code).toBe(DiagnosticCode.RequiredPropertyMissing);
  });

  it('table-no-columns on an empty table', () => {
    const d = lintOne('db.ttr', `schema db namespace dbo\ndef table empty { description: "x" }`);
    expect(rulesOf(d)).toContain('table-no-columns');
  });

  it('column-missing-type (top-level def column, matching old Validator scope)', () => {
    const d = lintOne('db.ttr', `schema db namespace dbo\ndef column c { description: "x" }`);
    expect(rulesOf(d)).toContain('column-missing-type');
  });

  it('attribute-missing-type (top-level def attribute, matching old Validator scope)', () => {
    const d = lintOne('er.ttr', `schema er namespace entity\ndef attribute a { description: "x" }`);
    expect(rulesOf(d)).toContain('attribute-missing-type');
  });

  it('entity-attribute-not-found for a bad nameAttribute', () => {
    const d = lintOne('er.ttr', `schema er namespace entity\ndef entity e {\n attributes: [def attribute id { type: int }]\n nameAttribute: ghost\n}`);
    const f = d.find((x) => x.ruleId === 'entity-attribute-not-found');
    expect(f).toBeDefined();
    expect(f!.message).toContain("nameAttribute 'ghost'");
  });

  it('primary-key-column-not-found', () => {
    const d = lintOne('db.ttr', `schema db namespace dbo\ndef table t {\n columns: [def column id { type: int }]\n primaryKey: ["bogus"]\n}`);
    expect(rulesOf(d)).toContain('primary-key-column-not-found');
  });

  it('missing-description is off under recommended', () => {
    const d = lintOne('db.ttr', `schema db namespace dbo\ndef table t { columns: [def column id { type: int }] }`);
    expect(rulesOf(d)).not.toContain('missing-description');
  });

  it('missing-description fires when enabled', () => {
    const d = lintOne(
      'db.ttr',
      `schema db namespace dbo\ndef table t { columns: [def column id { type: int, description: "x" }] }`,
      { config: recommendedConfig({ 'missing-description': 'warning' }) }
    );
    expect(rulesOf(d)).toContain('missing-description');
  });

  it('clean entity yields no structural diagnostics', () => {
    const d = lintOne('er.ttr', `schema er namespace entity\ndef entity e { attributes: [def attribute id { type: int }] }`);
    expect(d).toHaveLength(0);
  });
});

describe('search rules', () => {
  it('fuzzy-without-searchable', () => {
    const d = lintOne(
      'er.ttr',
      `schema er namespace entity\ndef entity e {\n attributes: [def attribute id { type: int }]\n search: { fuzzy: true }\n}`
    );
    expect(rulesOf(d)).toContain('fuzzy-without-searchable');
  });
});

describe('reference rules', () => {
  it('unresolved-reference (warning by default)', () => {
    const d = lintOne(
      'er.ttr',
      `schema er namespace entity\ndef relation r {\n from: ghost_a\n to: ghost_b\n}`
    );
    const f = d.filter((x) => x.ruleId === 'unresolved-reference');
    expect(f.length).toBeGreaterThan(0);
    expect(f[0].severity).toBe('warning');
  });
});

describe('import rules', () => {
  it('unused-import on an unreferenced import', () => {
    const files = [
      { uri: '/proj/other.ttr', src: `package other\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }` },
      { uri: '/proj/main.ttr', src: `package main\nimport other.db.dbo.t\nschema db namespace dbo\ndef table m { columns: [def column id { type: int }] }` },
    ];
    const diags = lintDocInProject(files, '/proj/main.ttr', { projectRoot: '/proj' });
    expect(rulesOf(diags)).toContain('unused-import');
  });

  it('duplicate-import', () => {
    const d = lintOne(
      '/proj/main.ttr',
      `package main\nimport other.db.dbo.t\nimport other.db.dbo.t\nschema db namespace dbo\ndef table m { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('duplicate-import');
  });
});

describe('package rules', () => {
  it('missing-package-declaration on a nested file with no package', () => {
    const d = lintOne(
      '/proj/sub/main.ttr',
      `schema db namespace dbo\ndef table t { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('missing-package-declaration');
  });

  it('package-declaration-mismatch', () => {
    const d = lintOne(
      '/proj/sub/main.ttr',
      `package wrong.pkg\nschema db namespace dbo\ndef table t { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('package-declaration-mismatch');
  });
});

describe('graph rules (.ttrg)', () => {
  it('graph-missing-schema + graph-name-mismatch', () => {
    const d = lintOne(
      '/proj/all.ttrg',
      `graph mygraph {\n objects: []\n}`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('graph-missing-schema');
    expect(rulesOf(d)).toContain('graph-name-mismatch');
    expect(rulesOf(d)).toContain('graph-objects-empty');
  });

  it('graph rules do not fire on a .ttr file', () => {
    const d = lintOne('/proj/x.ttr', `schema db namespace dbo\ndef table t { columns: [def column id { type: int }] }`, { projectRoot: '/proj' });
    expect(rulesOf(d)).not.toContain('graph-missing-schema');
  });
});

describe('project rules', () => {
  it('duplicate-definition across two files', () => {
    const files = [
      { uri: '/proj/a.ttr', src: `package p\nschema db namespace dbo\ndef table dup { columns: [def column id { type: int }] }` },
      { uri: '/proj/b.ttr', src: `package p\nschema db namespace dbo\ndef table dup { columns: [def column id { type: int }] }` },
    ];
    const byUri = lintProj(files, { projectRoot: '/proj' });
    const all = [...byUri.values()].flat();
    expect(rulesOf(all)).toContain('duplicate-definition');
  });
});
