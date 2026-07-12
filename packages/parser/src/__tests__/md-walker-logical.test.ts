// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type {
  Definition,
  MdDomainDef,
  DimensionDef,
  MdMapDef,
  HierarchyDef,
  MeasureDef,
  CubeletDef,
  RangeLiteral,
  DomainMember,
} from '../ast.js';
import { MD_LOGICAL } from './md-fixtures.js';

const { ast, errors } = parseString(MD_LOGICAL, 'file:///model.ttrm');
const defs = ast?.definitions ?? [];
function byName<T extends Definition>(name: string): T {
  const d = defs.find((x) => x.name === name);
  if (!d) throw new Error(`no def ${name}`);
  return d as T;
}

describe('Stage 1C — MD logical AST', () => {
  it('parses with no parse errors', () => {
    expect(errors.filter((e) => e.code === 'ttr/parse-error')).toEqual([]);
  });

  it('domain: kind, type, and a range-literal restrict clause', () => {
    const month = byName<MdDomainDef>('Month');
    expect(month.kind).toBe('mdDomain');
    expect(month.type).toMatchObject({ kind: 'simple', name: 'int' });
    expect(month.domainKind).toBe('calc');
    expect(month.restrict).toHaveLength(1);
    expect(month.restrict![0].clause).toBe('range');
    const range = month.restrict![0].value as RangeLiteral;
    expect(range.kind).toBe('rangeLiteral');
    expect(range.lo).toBe(1);
    expect(range.hi).toBe(12);
  });

  it('domain: bound kind with a members restrict clause (labels preserved)', () => {
    const ak = byName<MdDomainDef>('AccountKind');
    expect(ak.domainKind).toBe('bound');
    expect(ak.restrict![0].clause).toBe('members');
    const members = ak.restrict![0].value as DomainMember[];
    expect(members.map((m) => m.key)).toEqual(['A', 'L']);
    expect(members[0].labels.entries).toMatchObject({ en: 'Asset', cs: 'Aktivum' });
  });

  it('dimension: key, inline attributes (domainRef + aggregation), hierarchies', () => {
    const cust = byName<DimensionDef>('Customer');
    expect(cust.kind).toBe('dimension');
    expect(cust.key).toBe('code');
    expect(cust.hierarchies).toEqual(['geo']);
    expect(cust.attributes).toHaveLength(2);
    expect(cust.attributes[0]).toMatchObject({ name: 'code', domainRef: 'md.CustomerCode', isKey: true });
    expect(cust.attributes[0].type).toBeUndefined();
    expect(cust.attributes[1].name).toBe('name');
    expect(cust.attributes[1].domainRef).toBe('md.CustomerName');
    expect(cust.attributes[1].aggregation?.default).toBe('latestValid');
  });

  it('attribute: the ER form (shared node) still populates type, not domainRef', () => {
    const er = parseString(
      'model er schema entity\ndef entity E { attributes: [def attribute id { type: int, isKey: true }] }',
      'file:///er.ttrm'
    ).ast!;
    const entity = er.definitions.find((d) => d.name === 'E') as { attributes: { type?: unknown; domainRef?: unknown }[] };
    expect(entity.attributes[0].type).toMatchObject({ kind: 'simple', name: 'int' });
    expect(entity.attributes[0].domainRef).toBeUndefined();
  });

  it('map: from/to arrays, calc ref with no args (table-trunc)', () => {
    const m = byName<MdMapDef>('ts_to_day');
    expect(m.kind).toBe('mdMap');
    expect(m.from).toEqual(['md.Timestamp']);
    expect(m.to).toEqual(['md.Day']);
    expect(m.calc?.name).toBe('truncToDay');
    expect(m.calc?.args).toEqual([]);
  });

  it('map: parameterised calc ref captures named args', () => {
    const m = byName<MdMapDef>('day_to_fy');
    expect(m.calc?.name).toBe('fiscalYearOfDate');
    expect(m.calc?.args).toHaveLength(1);
    expect(m.calc?.args[0].name).toBe('fiscalYearStartMonth');
    expect(m.calc?.args[0].value).toMatchObject({ kind: 'number', value: 4 });
  });

  it('map: table-backed (no calc) normalises cardinality to N:1', () => {
    const m = byName<MdMapDef>('cc_to_building');
    expect(m.calc).toBeUndefined();
    expect(m.from).toEqual(['md.CostCenterCode']);
    expect(m.to).toEqual(['md.CustomerCode']);
    expect(m.cardinality).toBe('N:1');
  });

  it('hierarchy: levels preserved leaf→root with optional via', () => {
    const h = byName<HierarchyDef>('calendar');
    expect(h.kind).toBe('hierarchy');
    expect(h.dimensionRef).toBe('md.Time');
    expect(h.levels.map((l) => l.attribute)).toEqual(['day', 'month', 'quarter', 'year']);
    expect(h.levels[0].via).toBeUndefined();
    expect(h.levels[1].via).toBe('md.day_to_month');
    expect(h.levels[2].via).toBe('md.month_to_qtr');
  });

  it('measure: bare-sum and the per-dimension object aggregation', () => {
    const net = byName<MeasureDef>('net');
    expect(net.kind).toBe('measure');
    expect(net.domainRef).toBe('md.Money');
    expect(net.measureClass).toBe('additive');
    expect(net.aggregation?.default).toBe('sum');
    expect(net.aggregation?.perDimension).toBeUndefined();

    const bal = byName<MeasureDef>('balance');
    expect(bal.measureClass).toBe('semiAdditive');
    expect(bal.aggregation?.default).toBe('sum');
    expect(bal.aggregation?.perDimension).toMatchObject({ time: 'latestValid' });
    expect(bal.validBy).toBe('day');
  });

  it('cubelet: dotted grain refs + measures as refs and inline defs', () => {
    const sales = byName<CubeletDef>('sales');
    expect(sales.kind).toBe('cubelet');
    expect(sales.grain).toEqual(['Customer.code', 'Time.day']);
    expect(sales.measures).toEqual(['net', 'balance']);

    const costs = byName<CubeletDef>('costs');
    expect(costs.measures).toHaveLength(1);
    const inline = costs.measures[0] as MeasureDef;
    expect(inline.kind).toBe('measure');
    expect(inline.name).toBe('amount');
    expect(inline.domainRef).toBe('md.Money');
  });

  it('source spans: a multi-token via-level entry spans the whole entry', () => {
    const h = byName<HierarchyDef>('calendar');
    const viaLevel = h.levels[1]; // `month via md.day_to_month`
    const span = viaLevel.source.offsetEnd - viaLevel.source.offsetStart;
    expect(span).toBe('month via md.day_to_month'.length);
    // restrict block clause also carries an accurate (non-empty) span.
    const month = byName<MdDomainDef>('Month');
    const clauseSrc = month.restrict![0].source;
    expect(clauseSrc.offsetEnd).toBeGreaterThan(clauseSrc.offsetStart);
  });
});
