// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { buildCatalog, filterCatalog, type GraphMeta, type SymbolMeta } from '../catalog.js';

// Hero-shaped fixtures: two renderable schema graphs, one binding graph (skipped),
// plus symbols for a cube, a concept, a program, and noise kinds.
const graphs: GraphMeta[] = [
  { uri: 'file:///ws/all_er.ttrg', name: 'all_er', schema: 'er' },
  { uri: 'file:///ws/all_db.ttrg', name: 'all_db', schema: 'db' },
  { uri: 'file:///ws/map.ttrg', name: 'map', schema: 'binding' },
];

const symbols: SymbolMeta[] = [
  { qname: 'sales.Sales', kind: 'cubelet', name: 'Sales', packageName: 'sales' },
  { qname: 'cnc.Customer', kind: 'concept', name: 'Customer', packageName: null },
  { qname: 'cnc.Master', kind: 'role', name: 'Master', packageName: 'cnc' },
  { qname: 'proc.monthly_sales', kind: 'program', name: 'monthly_sales', packageName: 'proc' },
  // noise — must not surface in any group
  { qname: 'db.orders', kind: 'table', name: 'orders', packageName: null },
  { qname: 'er.person', kind: 'entity', name: 'person', packageName: null },
  { qname: 'er.knows', kind: 'relation', name: 'knows', packageName: null },
];

describe('buildCatalog', () => {
  it('groups graphs and symbols into ordered, kind-prefixed groups', () => {
    const groups = buildCatalog(graphs, symbols);

    // Group order among present groups: Schemas, Cubes, Concepts, Programs
    expect(groups.map((g) => g.kind)).toEqual(['schema', 'cube', 'concept', 'program']);
    expect(groups.map((g) => g.label)).toEqual(['Schemas', 'Cubes', 'Concepts', 'Programs']);

    const schemas = groups.find((g) => g.kind === 'schema')!;
    // binding graph skipped -> 2 items
    expect(schemas.items).toHaveLength(2);
    // kind-prefixed labels, sorted by label: 'db · all_db' < 'er · all_er'
    expect(schemas.items.map((i) => i.label)).toEqual(['db · all_db', 'er · all_er']);
    const er = schemas.items.find((i) => i.label === 'er · all_er')!;
    expect(er.ref).toBe('file:///ws/all_er.ttrg');
    expect(er.qname).toBe('file:///ws/all_er.ttrg');
    expect(er.schemaCode).toBe('er');
    expect(er.packageName).toBeNull();

    const cubes = groups.find((g) => g.kind === 'cube')!;
    expect(cubes.items).toHaveLength(1);
    expect(cubes.items[0].label).toBe('cube Sales');
    expect(cubes.items[0].schemaCode).toBe('md');
    expect(cubes.items[0].packageName).toBe('sales');

    const concepts = groups.find((g) => g.kind === 'concept')!;
    // concept + role both accepted
    expect(concepts.items.length).toBeGreaterThanOrEqual(1);
    expect(concepts.items.map((i) => i.label)).toEqual(['Customer', 'Master']);
    expect(concepts.items.every((i) => i.schemaCode === 'cnc')).toBe(true);

    const programs = groups.find((g) => g.kind === 'program')!;
    expect(programs.items).toHaveLength(1);
    expect(programs.items[0].label).toBe('program monthly_sales');
  });

  it('omits empty groups (no dead spine) and returns [] for an empty workspace', () => {
    expect(buildCatalog([], [])).toEqual([]);
    // only a cube symbol -> only the Cubes group
    const onlyCube = buildCatalog([], [
      { qname: 'x.C', kind: 'cubelet', name: 'C', packageName: null },
    ]);
    expect(onlyCube.map((g) => g.kind)).toEqual(['cube']);
  });

  it('sorts items within a group by label', () => {
    const groups = buildCatalog(
      [
        { uri: 'file:///z.ttrg', name: 'zeta', schema: 'er' },
        { uri: 'file:///a.ttrg', name: 'alpha', schema: 'er' },
      ],
      [],
    );
    const schemas = groups[0];
    expect(schemas.items.map((i) => i.label)).toEqual(['er · alpha', 'er · zeta']);
  });
});

describe('filterCatalog', () => {
  const groups = buildCatalog(
    [{ uri: 'file:///ws/sales.ttrg', name: 'sales', schema: 'er' }],
    symbols,
  );

  it('matches across kinds by label or qname, case-insensitive, and drops empty groups', () => {
    const filtered = filterCatalog(groups, 'sales');
    // 'er · sales' schema + 'cube Sales' (qname sales.Sales) match; concepts/programs may match by qname too
    const kinds = filtered.map((g) => g.kind);
    expect(kinds).toContain('schema');
    expect(kinds).toContain('cube');

    const schema = filtered.find((g) => g.kind === 'schema')!;
    expect(schema.items.map((i) => i.label)).toEqual(['er · sales']);
    const cube = filtered.find((g) => g.kind === 'cube')!;
    expect(cube.items.map((i) => i.label)).toEqual(['cube Sales']);

    // case-insensitive
    expect(filterCatalog(groups, 'CUBE SALES').some((g) => g.kind === 'cube')).toBe(true);

    // a query matching nothing -> []
    expect(filterCatalog(groups, 'zzz-nomatch')).toEqual([]);
  });

  it('returns input unchanged for empty/whitespace query', () => {
    expect(filterCatalog(groups, '')).toBe(groups);
    expect(filterCatalog(groups, '   ')).toBe(groups);
  });
});
