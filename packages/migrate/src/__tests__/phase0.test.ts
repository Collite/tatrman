import { describe, it, expect } from 'vitest';
import { migratePhase0, type Phase0File } from '../phase0.js';

// Phase 0 (grammar 3.0) migration helper: before/after project tree.

describe('migratePhase0', () => {
  it('renames *.ttr → *.ttrm and rewrites schema map + inline mapping:', () => {
    const before: Phase0File[] = [
      {
        path: '/proj/billing/map.ttr',
        text: 'schema map\ndef er2db_entity e { entity: er.entity.x, target: db.table }\n',
      },
      {
        path: '/proj/billing/er.ttr',
        text: 'schema er\ndef entity x {\n  attributes: [def attribute id { type: int, mapping: COL }]\n  mapping: { target: { table: db.dbo.X } }\n}\n',
      },
    ];
    const { writes, deletes } = migratePhase0(before);

    expect(deletes).toEqual(['/proj/billing/map.ttr', '/proj/billing/er.ttr']);
    const byPath = new Map(writes.map((w) => [w.path, w.text]));
    expect(byPath.get('/proj/billing/map.ttrm')).toContain('schema binding');
    expect(byPath.get('/proj/billing/map.ttrm')).not.toContain('schema map');
    const er = byPath.get('/proj/billing/er.ttrm')!;
    expect(er).toContain('binding: COL');
    expect(er).toContain('binding: { target: { table: db.dbo.X } }');
    expect(er).not.toMatch(/\bmapping\s*[:{]/);
  });

  it('converts a .ttrd domain block to def area in a .ttrm file', () => {
    const before: Phase0File[] = [
      {
        path: '/proj/domains/accounting.ttrd',
        text: 'package domains\ndomain accounting {\n  description: "Účetnictví",\n  packages: [ucetnictvi]\n}\n',
      },
    ];
    const { writes, deletes } = migratePhase0(before);

    expect(deletes).toEqual(['/proj/domains/accounting.ttrd']);
    expect(writes).toHaveLength(1);
    expect(writes[0].path).toBe('/proj/domains/accounting.ttrm');
    expect(writes[0].text).toContain('def area accounting {');
    expect(writes[0].text).not.toMatch(/\bdomain\s+accounting/);
    // `package domains` declaration is untouched (plural, not the keyword).
    expect(writes[0].text).toContain('package domains');
  });

  it('rewrites schema map in a .ttrg graph but keeps its extension', () => {
    const before: Phase0File[] = [
      { path: '/proj/g.ttrg', text: 'graph g {\n  schema: map\n}\n' },
    ];
    const { writes, deletes } = migratePhase0(before);
    expect(deletes).toEqual([]);
    expect(writes).toEqual([{ path: '/proj/g.ttrg', text: 'graph g {\n  schema: binding\n}\n' }]);
  });

  it('is a no-op for an already-migrated project (only .ttrm files)', () => {
    const before: Phase0File[] = [
      { path: '/proj/a.ttrm', text: 'schema binding\ndef er2db_entity e {}' },
    ];
    const { writes, deletes } = migratePhase0(before);
    expect(writes).toEqual([]);
    expect(deletes).toEqual([]);
  });
});
