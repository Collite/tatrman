import { describe, it, expect } from 'vitest';
import { resolveManifest } from '@modeler/semantics';
import { rewriteV4Keywords } from '../keyword-rewrite.js';
import { liftManifest } from '../manifest-lift.js';
import { unifiedDiff } from '../text-diff.js';
import { planQnameMigration } from '../qname-migrate-driver.js';
import type { ModelFile } from '../resolve-packages.js';

const cfg = resolveManifest(undefined, '/proj').packages;

describe('rewriteV4Keywords (D1–D3)', () => {
  it('renames def model → def project', () => {
    expect(rewriteV4Keywords('def model erp_v1 { version: "1.0.0" }')).toBe('def project erp_v1 { version: "1.0.0" }');
  });

  it('rewrites a directive `schema <code> namespace <id>` → `model <code> schema <id>`', () => {
    expect(rewriteV4Keywords('schema db namespace dbo\ndef table T {}')).toBe('model db schema dbo\ndef table T {}');
  });

  it('rewrites a schema-less directive `schema er` → `model er`', () => {
    expect(rewriteV4Keywords('schema er\ndef entity E {}')).toBe('model er\ndef entity E {}');
  });

  it('rewrites a graph `schema: <code>` property → `model: <code>`', () => {
    expect(rewriteV4Keywords('graph g {\n  schema: db,\n}')).toBe('graph g {\n  model: db,\n}');
  });

  it('leaves a namespace VALUE (schema dbo) untouched — only model codes flip', () => {
    // `model db schema dbo` is already v4.0; the `schema dbo` must not become `model dbo`.
    expect(rewriteV4Keywords('model db schema dbo')).toBe('model db schema dbo');
  });

  it('is idempotent', () => {
    const once = rewriteV4Keywords('def model M\nschema db namespace dbo\ndef table T {}');
    expect(rewriteV4Keywords(once)).toBe(once);
  });
});

describe('liftManifest (Phase 7 §2)', () => {
  const RETAIL = `[project]
name = "retail-shop"

[schemas]
declared = ["db", "er", "map"]
namespaces = { db = "dbo", er = "entity", map = "er2db" }

[stock]
load = ["cnc-roles"]
`;

  it('lifts the legacy [schemas] block to a named [schemas.dbo] binding + [defaults]', () => {
    const r = liftManifest(RETAIL);
    expect(r.changed).toBe(true);
    expect(r.schemas).toEqual(['dbo']);
    expect(r.text).toContain('[schemas.dbo]');
    expect(r.text).toContain('db-schema = "dbo"');
    expect(r.text).toContain('dialect   = "tsql"');
    expect(r.text).toContain('[defaults]\nschema = "dbo"');
    // legacy keys are gone; other tables are preserved.
    expect(r.text).not.toContain('namespaces =');
    expect(r.text).not.toContain('declared =');
    expect(r.text).toContain('[project]');
    expect(r.text).toContain('[stock]');
  });

  it('is idempotent — re-lifting an already-lifted manifest is a no-op', () => {
    const once = liftManifest(RETAIL).text;
    const twice = liftManifest(once);
    expect(twice.changed).toBe(false);
    expect(twice.text).toBe(once);
  });

  it('leaves a manifest with no legacy [schemas] unchanged', () => {
    const r = liftManifest('[project]\nname = "x"\n');
    expect(r.changed).toBe(false);
  });
});

describe('unifiedDiff', () => {
  it('returns empty string for identical text', () => {
    expect(unifiedDiff('f', 'a\nb', 'a\nb')).toBe('');
  });
  it('produces a hunk with - / + lines for a change', () => {
    const d = unifiedDiff('f.ttrm', 'a\nb\nc', 'a\nB\nc');
    expect(d).toContain('--- a/f.ttrm');
    expect(d).toContain('+++ b/f.ttrm');
    expect(d).toContain('-b');
    expect(d).toContain('+B');
    expect(d).toContain(' a');
    expect(d).toContain(' c');
  });
});

describe('planQnameMigration — keyword + canonical-key in one shot', () => {
  const files = (): ModelFile[] => [
    {
      path: '/proj/db.ttrm',
      // OLD keywords (pre-cut): must migrate to `model db schema dbo`.
      text: 'schema db namespace dbo\ndef table Orders { columns: [def column id { type: int }] }\n',
    },
    {
      path: '/proj/sales.ttrg',
      // Legacy canonical key (no kind segment): must gain `.table.`.
      text: 'graph g {\n  model: db,\n  objects: [\n    db.dbo.Orders\n  ]\n}\n',
    },
    {
      path: '/proj/er.ttrm',
      // Already v4.0, schema-less er — must NOT change.
      text: 'model er\ndef entity Customer { attributes: [def attribute id { type: int }] }\n',
    },
  ];

  it('migrates old keywords and remaps the legacy graph key, leaving the er file untouched', () => {
    const plan = planQnameMigration(files(), '/proj', cfg);
    expect(plan.reparseFailures).toEqual([]);
    const byPath = new Map(plan.writes.map((w) => [w.path, w.after]));

    // keyword rewrite on the db file.
    expect(byPath.get('/proj/db.ttrm')).toContain('model db schema dbo');
    expect(byPath.get('/proj/db.ttrm')).not.toContain('namespace');

    // canonical-key remap in the graph file (db.dbo.Orders → db.dbo.table.Orders).
    expect(byPath.get('/proj/sales.ttrg')).toContain('db.dbo.table.Orders');

    // the er file is unchanged → not in the write set.
    expect(byPath.has('/proj/er.ttrm')).toBe(false);

    // every change carries a unified diff for --dry-run.
    for (const w of plan.writes) expect(w.diff).toContain(`--- a/${w.path}`);
  });

  it('is idempotent — re-running over migrated output produces no writes', () => {
    const first = planQnameMigration(files(), '/proj', cfg);
    const migrated: ModelFile[] = files().map((f) => {
      const w = first.writes.find((x) => x.path === f.path);
      return w ? { path: f.path, text: w.after } : f;
    });
    const second = planQnameMigration(migrated, '/proj', cfg);
    expect(second.writes).toEqual([]);
  });

  it('surfaces the manifest lift through the plan when given modeler.toml', () => {
    const manifestToml = '[schemas]\nnamespaces = { db = "dbo" }\n';
    const plan = planQnameMigration(files(), '/proj', cfg, manifestToml);
    expect(plan.manifestWrite).toBeDefined();
    expect(plan.manifestWrite!.after).toContain('[schemas.dbo]');
    expect(plan.manifestWrite!.diff).toContain('+++ b/modeler.toml');
  });
});
