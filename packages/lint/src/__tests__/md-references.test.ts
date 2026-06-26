import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintOne } from './helpers.js';

const CLEAN = `schema md
def domain Money { type: decimal }
def domain Day { type: date }
def domain Month { type: int }
def dimension Time {
  key: day,
  attributes: [def attribute day { domain: md.Day }, def attribute month { domain: md.Month }],
  hierarchies: [cal]
}
def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
def hierarchy cal { dimension: md.Time, levels: [day, month via md.day_to_month] }
def measure net { domain: md.Money, aggregation: sum }
def cubelet sales { grain: [Time.day], measures: [net] }
`;

function mdCodes(diags: ReturnType<typeof lintOne>, code: DiagnosticCode) {
  return diags.filter((d) => d.code === code);
}

describe('Stage 2B — md/unknown-ref', () => {
  it('a fully-resolving MD model has no unknown-ref diagnostics', () => {
    const d = lintOne('file:///m.ttrm', CLEAN);
    expect(mdCodes(d, DiagnosticCode.MdUnknownRef)).toEqual([]);
  });

  it('a dangling domain ref → md/unknown-ref at the ref range', () => {
    const src = CLEAN.replace('domain: md.Money', 'domain: md.Nope');
    const d = mdCodes(lintOne('file:///m.ttrm', src), DiagnosticCode.MdUnknownRef);
    expect(d).toHaveLength(1);
    expect(d[0].message).toContain('md.Nope');
    // points at the ref, not column 0
    expect(d[0].source.column).toBeGreaterThan(0);
  });

  it('a dangling grain ref → md/unknown-ref', () => {
    const src = CLEAN.replace('grain: [Time.day]', 'grain: [Time.nope]');
    const d = mdCodes(lintOne('file:///m.ttrm', src), DiagnosticCode.MdUnknownRef);
    expect(d.map((x) => x.message).join()).toContain('Time.nope');
  });

  it('a dangling via map ref → md/unknown-ref', () => {
    const src = CLEAN.replace('via md.day_to_month', 'via md.nope_map');
    const d = mdCodes(lintOne('file:///m.ttrm', src), DiagnosticCode.MdUnknownRef);
    expect(d.map((x) => x.message).join()).toContain('md.nope_map');
  });
});

describe('Stage 2B — md/unknown-schema-def', () => {
  it('an MD logical def under schema er → md/unknown-schema-def', () => {
    const d = lintOne('file:///x.ttrm', 'schema er namespace entity\ndef domain X { type: int }');
    const found = mdCodes(d, DiagnosticCode.MdUnknownSchemaDef);
    expect(found).toHaveLength(1);
    expect(found[0].message).toContain('schema md');
  });

  it('a binding def under schema md → md/unknown-schema-def', () => {
    const d = lintOne(
      'file:///x.ttrm',
      'schema md\ndef md2db_domain s { domain: md.X, source: { table: db.dbo.T, column: C } }'
    );
    expect(mdCodes(d, DiagnosticCode.MdUnknownSchemaDef)).toHaveLength(1);
  });

  it('a correctly-placed MD model has no schema-def diagnostic', () => {
    expect(mdCodes(lintOne('file:///m.ttrm', CLEAN), DiagnosticCode.MdUnknownSchemaDef)).toEqual([]);
  });
});
