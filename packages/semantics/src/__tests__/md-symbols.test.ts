import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { isKnownCalc, getCalcEntry, calcNames } from '../md-catalog-source.js';

const MD = `model md

def domain Money { type: decimal }
def domain Day { type: date }
def dimension Customer {
  key: code,
  attributes: [
    def attribute code { domain: md.CustomerCode },
    def attribute name { domain: md.CustomerName }
  ]
}
def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
def hierarchy calendar { dimension: md.Time, levels: [day, month] }
def measure net { domain: md.Money, aggregation: sum }
def cubelet sales { grain: [Customer.code], measures: [net] }
`;

function tableFor(src: string, uri = 'file:///m.ttrm'): ProjectSymbolTable {
  const ast = parseString(src, uri).ast!;
  const table = new ProjectSymbolTable();
  table.upsertDocument(uri, ast, ast.modelDirective?.modelCode ?? '', ast.modelDirective?.schema ?? '');
  return table;
}

describe('Stage 2A — MD symbol namespaces', () => {
  const table = tableFor(MD);

  it('each MD def registers in its contracts §5 namespace', () => {
    expect(table.get('md.domain.Money')?.kind).toBe('mdDomain');
    expect(table.get('md.domain.Day')?.kind).toBe('mdDomain');
    expect(table.get('md.dimension.Customer')?.kind).toBe('dimension');
    expect(table.get('md.map.day_to_month')?.kind).toBe('mdMap');
    expect(table.get('md.hierarchy.calendar')?.kind).toBe('hierarchy');
    expect(table.get('md.measure.net')?.kind).toBe('measure');
    expect(table.get('md.cubelet.sales')?.kind).toBe('cubelet');
  });

  it('dimension attributes register dimension-qualified', () => {
    const code = table.get('md.dimension.Customer.code');
    expect(code?.kind).toBe('attribute');
    expect(code?.parent).toBe('md.dimension.Customer');
    expect(table.get('md.dimension.Customer.name')?.kind).toBe('attribute');
  });

  it('binding md2* defs register under the binding schema', () => {
    const bt = tableFor(
      'model binding\ndef md2db_cubelet f { cubelet: md.sales, target: db.dbo.T, shape: wide, attributes: {}, measures: {} }',
      'file:///b.ttrm'
    );
    expect(bt.get('binding.md2db_cubelet.f')?.kind).toBe('md2dbCubelet');
  });

  it('existing ER symbols are unaffected (regression)', () => {
    const er = tableFor(
      'model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }',
      'file:///er.ttrm'
    );
    expect(er.get('er.entity.artikl')?.kind).toBe('entity');
    expect(er.get('er.entity.artikl.id')?.kind).toBe('attribute');
  });
});

describe('Stage 2A — calc-catalog preload (read-only calc source)', () => {
  it('catalog entries are known; unknown names are absent', () => {
    expect(isKnownCalc('truncToDay')).toBe(true);
    expect(isKnownCalc('monthOfDate')).toBe(true);
    expect(isKnownCalc('quarterOfMonth')).toBe(true);
    expect(isKnownCalc('notACalc')).toBe(false);
  });

  it('getCalcEntry returns the typed signature', () => {
    const e = getCalcEntry('monthOfDate');
    expect(e?.category).toBe('extraction');
    expect(e?.output).toEqual({ kind: 'int', lo: 1, hi: 12 });
    expect(getCalcEntry('nope')).toBeUndefined();
  });

  it('calcNames lists the seeded floor', () => {
    expect(calcNames()).toContain('truncToDay');
    expect(calcNames().length).toBeGreaterThanOrEqual(11);
  });
});
