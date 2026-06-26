import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintAllOne } from './helpers.js';

const BASE = `schema md
def domain Day { type: date }
def domain Month { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Quarter { type: int, kind: calc, restrict: { range: 1..4 } }
def domain Year { type: int, kind: calc }
def dimension Time {
  key: day,
  attributes: [
    def attribute day { domain: md.Day },
    def attribute month { domain: md.Month },
    def attribute quarter { domain: md.Quarter },
    def attribute year { domain: md.Year }
  ]
}
def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
def map month_to_qtr { from: md.Month, to: md.Quarter, calc: quarterOfMonth }
def map qtr_to_year  { from: md.Quarter, to: md.Year, calc: yearOfDate }`;

function codes(hierarchy: string, extra = '') {
  return lintAllOne('file:///m.ttrm', `${BASE}\n${extra}\n${hierarchy}`).map((d) => d.code);
}

describe('Stage 2E — hierarchy step inference', () => {
  it('the design Time hierarchy [day, month, quarter, year] is clean', () => {
    const c = codes('def hierarchy cal { dimension: md.Time, levels: [day, month, quarter, year] }');
    expect(c).not.toContain(DiagnosticCode.MdNoHierarchyStep);
    expect(c).not.toContain(DiagnosticCode.MdAmbiguousHierarchyStep);
    expect(c).not.toContain(DiagnosticCode.MdLevelNotInDim);
  });

  it('a level not in the dimension → md/level-not-in-dim', () => {
    expect(codes('def hierarchy cal { dimension: md.Time, levels: [day, nope] }')).toContain(
      DiagnosticCode.MdLevelNotInDim
    );
  });

  it('a non-adjacent step with no direct map → md/no-hierarchy-step', () => {
    expect(codes('def hierarchy cal { dimension: md.Time, levels: [day, year] }')).toContain(
      DiagnosticCode.MdNoHierarchyStep
    );
  });

  it('two maps connecting the same step → md/ambiguous-hierarchy-step', () => {
    const extra = 'def map day_to_month2 { from: md.Day, to: md.Month, cardinality: { from: "N", to: "1" } }';
    expect(codes('def hierarchy cal { dimension: md.Time, levels: [day, month] }', extra)).toContain(
      DiagnosticCode.MdAmbiguousHierarchyStep
    );
  });

  it('a via override resolves the ambiguity', () => {
    const extra = 'def map day_to_month2 { from: md.Day, to: md.Month, cardinality: { from: "N", to: "1" } }';
    const c = codes('def hierarchy cal { dimension: md.Time, levels: [day, month via md.day_to_month] }', extra);
    expect(c).not.toContain(DiagnosticCode.MdAmbiguousHierarchyStep);
  });
});
