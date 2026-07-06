import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import { lintOne, lintProj, lintDocInProject, recommendedConfig, buildProject } from './helpers.js';
import { lintDocument } from '../runner.js';

function rulesOf(diags: { ruleId: string }[]): string[] {
  return diags.map((d) => d.ruleId);
}

describe('structure rules', () => {
  it('entity-no-attributes on an empty entity', () => {
    const d = lintOne('er.ttrm', `model er schema entity\ndef entity empty { description: "x" }`);
    expect(rulesOf(d)).toContain('entity-no-attributes');
    expect(d.find((x) => x.ruleId === 'entity-no-attributes')!.code).toBe(DiagnosticCode.RequiredPropertyMissing);
  });

  it('table-no-columns on an empty table', () => {
    const d = lintOne('db.ttrm', `model db schema dbo\ndef table empty { description: "x" }`);
    expect(rulesOf(d)).toContain('table-no-columns');
  });

  it('column-missing-type (top-level def column, matching old Validator scope)', () => {
    const d = lintOne('db.ttrm', `model db schema dbo\ndef column c { description: "x" }`);
    expect(rulesOf(d)).toContain('column-missing-type');
  });

  it('attribute-missing-type (top-level def attribute, matching old Validator scope)', () => {
    const d = lintOne('er.ttrm', `model er schema entity\ndef attribute a { description: "x" }`);
    expect(rulesOf(d)).toContain('attribute-missing-type');
  });

  it('entity-attribute-not-found for a bad nameAttribute', () => {
    const d = lintOne('er.ttrm', `model er schema entity\ndef entity e {\n attributes: [def attribute id { type: int }]\n nameAttribute: ghost\n}`);
    const f = d.find((x) => x.ruleId === 'entity-attribute-not-found');
    expect(f).toBeDefined();
    expect(f!.message).toContain("nameAttribute 'ghost'");
  });

  it('primary-key-column-not-found', () => {
    const d = lintOne('db.ttrm', `model db schema dbo\ndef table t {\n columns: [def column id { type: int }]\n primaryKey: ["bogus"]\n}`);
    expect(rulesOf(d)).toContain('primary-key-column-not-found');
  });

  it('missing-description is off under recommended', () => {
    const d = lintOne('db.ttrm', `model db schema dbo\ndef table t { columns: [def column id { type: int }] }`);
    expect(rulesOf(d)).not.toContain('missing-description');
  });

  it('missing-description fires when enabled', () => {
    const d = lintOne(
      'db.ttrm',
      `model db schema dbo\ndef table t { columns: [def column id { type: int, description: "x" }] }`,
      { config: recommendedConfig({ 'missing-description': 'warning' }) }
    );
    expect(rulesOf(d)).toContain('missing-description');
  });

  it('clean entity yields no structural diagnostics', () => {
    const d = lintOne('er.ttrm', `model er\ndef entity e { attributes: [def attribute id { type: int }] }`);
    expect(d).toHaveLength(0);
  });
});

describe('search rules', () => {
  it('fuzzy-without-searchable', () => {
    const d = lintOne(
      'er.ttrm',
      `model er schema entity\ndef entity e {\n attributes: [def attribute id { type: int }]\n search: { fuzzy: true }\n}`
    );
    expect(rulesOf(d)).toContain('fuzzy-without-searchable');
  });
});

describe('reference rules', () => {
  it('unresolved-reference (warning by default)', () => {
    const d = lintOne(
      'er.ttrm',
      `model er schema entity\ndef relation r {\n from: ghost_a\n to: ghost_b\n}`
    );
    const f = d.filter((x) => x.ruleId === 'unresolved-reference');
    expect(f.length).toBeGreaterThan(0);
    expect(f[0].severity).toBe('warning');
  });
});

describe('import rules', () => {
  it('unused-import on an unreferenced import', () => {
    const files = [
      { uri: '/proj/other.ttrm', src: `package other\nmodel db schema dbo\ndef table t { columns: [def column id { type: int }] }` },
      { uri: '/proj/main.ttrm', src: `package main\nimport other.db.dbo.t\nmodel db schema dbo\ndef table m { columns: [def column id { type: int }] }` },
    ];
    const diags = lintDocInProject(files, '/proj/main.ttrm', { projectRoot: '/proj' });
    expect(rulesOf(diags)).toContain('unused-import');
  });

  it('duplicate-import', () => {
    const d = lintOne(
      '/proj/main.ttrm',
      `package main\nimport other.db.dbo.t\nimport other.db.dbo.t\nmodel db schema dbo\ndef table m { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('duplicate-import');
  });
});

describe('package rules', () => {
  it('missing-package-declaration on a nested file with no package', () => {
    const d = lintOne(
      '/proj/sub/main.ttrm',
      `model db schema dbo\ndef table t { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('missing-package-declaration');
  });

  it('package-declaration-mismatch (leaf-only override)', () => {
    const d = lintOne(
      '/proj/sub/main.ttrm',
      `package renamed\nmodel db schema dbo\ndef table t { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('package-declaration-mismatch');
  });

  it('package-prefix-divergence (non-leaf segment diverges)', () => {
    const d = lintOne(
      '/proj/sub/main.ttrm',
      `package wrong.pkg\nmodel db schema dbo\ndef table t { columns: [def column id { type: int }] }`,
      { projectRoot: '/proj' }
    );
    expect(rulesOf(d)).toContain('package-prefix-divergence');
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

  it('graph rules do not fire on a .ttrm file', () => {
    const d = lintOne('/proj/x.ttrm', `model db schema dbo\ndef table t { columns: [def column id { type: int }] }`, { projectRoot: '/proj' });
    expect(rulesOf(d)).not.toContain('graph-missing-schema');
  });
});

describe('qname-redesign rules (D6 / D10)', () => {
  it('schema-on-logical-model fires on a `model er schema X` directive', () => {
    const d = lintOne('er.ttrm', `model er schema ent\ndef entity e { attributes: [def attribute id { type: int }] }`);
    const f = d.find((x) => x.ruleId === 'schema-on-logical-model');
    expect(f).toBeDefined();
    expect(f!.message).toContain("schema slot is db-only");
  });

  it('schema-on-logical-model does NOT fire on a db file with a schema', () => {
    const d = lintOne('db.ttrm', `model db schema dbo\ndef table t { columns: [def column id { type: int }] }`);
    expect(rulesOf(d)).not.toContain('schema-on-logical-model');
  });

  it('require-qualified-refs is off by default (no manifest flag)', () => {
    const d = lintOne(
      'er.ttrm',
      `model er\ndef entity a { attributes: [def attribute id { type: int }] }\ndef relation r { from: a, to: a }`,
    );
    expect(rulesOf(d)).not.toContain('require-qualified-refs');
  });

  it('require-qualified-refs flags a bare cross-schema unique-match when enabled', () => {
    const files = [
      { uri: '/proj/db.ttrm', src: `package p\nmodel db schema dbo\ndef table Orders { columns: [def column id { type: int }] }` },
      { uri: '/proj/er.ttrm', src: `package p\nmodel er\ndef entity cust { attributes: [def attribute id { type: int }] }\ndef relation r { from: cust, to: Orders }` },
    ];
    const project = buildProject(files, '/proj');
    project.deps.manifest.lint.requireQualifiedRefs = true;
    const d = lintDocument('/proj/er.ttrm', project.documents.get('/proj/er.ttrm')!, project.deps, recommendedConfig());
    const f = d.find((x) => x.ruleId === 'require-qualified-refs');
    expect(f).toBeDefined();
    expect(f!.message).toContain('cross-schema unique-match');
  });
});

describe('project rules', () => {
  it('duplicate-definition across two files', () => {
    const files = [
      { uri: '/proj/a.ttrm', src: `package p\nmodel db schema dbo\ndef table dup { columns: [def column id { type: int }] }` },
      { uri: '/proj/b.ttrm', src: `package p\nmodel db schema dbo\ndef table dup { columns: [def column id { type: int }] }` },
    ];
    const byUri = lintProj(files, { projectRoot: '/proj' });
    const all = [...byUri.values()].flat();
    expect(rulesOf(all)).toContain('duplicate-definition');
  });
});
