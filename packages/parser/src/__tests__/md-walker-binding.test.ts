// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type {
  Definition,
  Md2DbCubeletDef,
  Md2DbDomainDef,
  Md2DbMapDef,
  Md2ErCubeletDef,
  AttrColumnBinding,
} from '../ast.js';
import { MD_BINDING } from './md-fixtures.js';

const { ast, errors } = parseString(MD_BINDING, 'file:///binding.ttrm');
const defs = ast?.definitions ?? [];
function byName<T extends Definition>(name: string): T {
  const d = defs.find((x) => x.name === name);
  if (!d) throw new Error(`no def ${name}`);
  return d as T;
}

describe('Stage 1D — MD binding AST', () => {
  it('parses with no parse errors', () => {
    expect(errors.filter((e) => e.code === 'ttr/parse-error')).toEqual([]);
  });

  it('md2db_cubelet (wide): shape, column attributes incl. map-mediated, measures, journaling', () => {
    const f = byName<Md2DbCubeletDef>('sales_fact');
    expect(f.kind).toBe('md2dbCubelet');
    expect(f.cubeletRef).toBe('md.sales');
    expect(f.table).toBe('db.dbo.SALES_FACT');
    expect(f.shape).toEqual({ shape: 'wide' });
    expect(f.attributes['Customer.code']).toEqual({ column: 'CUST_CODE' });
    expect(f.attributes['Time.day']).toEqual({ column: 'TXN_DATE' });
    const mediated = f.attributes['CostCenter.code'] as Extract<AttrColumnBinding, { via: string }>;
    expect(mediated.via).toBe('md.cc_to_building');
    expect(mediated.from).toEqual({ table: 'db.dbo.CC_MAP', column: 'BUILDING' });
    expect(f.measures).toEqual({ net: { column: 'NET_AMT' }, balance: { column: 'BAL_AMT' } });
    expect(f.journaling).toEqual({ mode: 'overwrite' });
    // v0.10 — uniform spread strategy (applies to every spread dimension).
    expect(f.allocation).toEqual({ uniform: 'proportional' });
  });

  it('md2db_cubelet (long): long shape, code-form measures, invalidate journaling', () => {
    const f = byName<Md2DbCubeletDef>('drivers_fact');
    expect(f.shape).toEqual({ shape: 'long', codeColumn: 'DRIVER_CODE', valueColumn: 'AMOUNT' });
    expect(f.measures).toEqual({ amount: { code: 'AMT' } });
    expect(f.journaling).toEqual({ mode: 'invalidate', validColumn: 'VALID_TO' });
    // v0.10 — per-dimension spread strategy map (dotted grain keys).
    expect(f.allocation).toEqual({ byDimension: { 'Time.month': 'equal', 'CostCenter.code': 'proportional' } });
  });

  it('md2db_domain: domainRef + source table/column', () => {
    const d = byName<Md2DbDomainDef>('account_kind_src');
    expect(d.kind).toBe('md2dbDomain');
    expect(d.domainRef).toBe('md.AccountKind');
    expect(d.source_).toEqual({ table: 'db.dbo.ACCOUNTS', column: 'KIND' });
  });

  it('md2db_map: mapRef, table, columns record (domain → column)', () => {
    const m = byName<Md2DbMapDef>('cc_building_map');
    expect(m.kind).toBe('md2dbMap');
    expect(m.mapRef).toBe('md.cc_to_building');
    expect(m.table).toBe('db.dbo.CC_BUILDING');
    expect(m.columns).toEqual({ CostCenterCode: 'CC_COL', CustomerCode: 'BLDG_COL' });
  });

  it('md2er_cubelet: structural-only — entity + attribute map, no physical fields', () => {
    const e = byName<Md2ErCubeletDef>('sales_er');
    expect(e.kind).toBe('md2erCubelet');
    expect(e.cubeletRef).toBe('md.sales');
    expect(e.entity).toBe('er.entity.Sale');
    expect(e.attributes).toEqual({ 'Customer.code': 'customerCode', 'Time.day': 'saleDate' });
    // The node carries no shape/measures/journaling — those are md2db-only.
    expect((e as unknown as Record<string, unknown>).shape).toBeUndefined();
    expect((e as unknown as Record<string, unknown>).measures).toBeUndefined();
    expect((e as unknown as Record<string, unknown>).journaling).toBeUndefined();
  });

  it('source locations: nested column binding carries a non-empty span', () => {
    const f = byName<Md2DbCubeletDef>('sales_fact');
    expect(f.source.offsetEnd).toBeGreaterThan(f.source.offsetStart);
  });
});
