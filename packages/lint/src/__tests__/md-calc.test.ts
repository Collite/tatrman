import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintOne } from './helpers.js';

// Time domains shared by the calc-map cases.
const DOMAINS = `def domain Day { type: date }
def domain Month { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Quarter { type: int, kind: calc, restrict: { range: 1..4 } }
def domain BadInt { type: int }`;

function codes(mapSrc: string) {
  return lintOne('file:///m.ttrm', `schema md\n${DOMAINS}\n${mapSrc}`).map((d) => d.code);
}

describe('Stage 2D — calc resolution & args', () => {
  it('unknown calc name → md/unknown-calc-map', () => {
    expect(codes('def map m { from: md.Day, to: md.Month, calc: notACalc }')).toContain(
      DiagnosticCode.MdUnknownCalcMap
    );
  });

  it('the correct calendar maps validate clean', () => {
    const c = codes(
      'def map a { from: md.Day, to: md.Month, calc: monthOfDate }\n' +
        'def map b { from: md.Month, to: md.Quarter, calc: quarterOfMonth }'
    );
    expect(c).not.toContain(DiagnosticCode.MdUnknownCalcMap);
    expect(c).not.toContain(DiagnosticCode.MdCalcTypeMismatch);
    expect(c).not.toContain(DiagnosticCode.MdBadCalcArgs);
  });

  it('an out-of-range enum arg → md/bad-calc-args', () => {
    expect(codes('def map m { from: md.Day, to: md.Month, calc: weekOfYear(scheme: bogus) }')).toContain(
      DiagnosticCode.MdBadCalcArgs
    );
  });

  it('an unknown arg name → md/bad-calc-args', () => {
    expect(codes('def map m { from: md.Day, to: md.Day, calc: truncToDay(nope: 1) }')).toContain(
      DiagnosticCode.MdBadCalcArgs
    );
  });
});

describe('Stage 2D — type-check from/to', () => {
  it('truncToDay with a to of type int → md/calc-type-mismatch (output must be date)', () => {
    expect(codes('def map m { from: md.Day, to: md.BadInt, calc: truncToDay }')).toContain(
      DiagnosticCode.MdCalcTypeMismatch
    );
  });

  it('monthOfDate to a int{1..12} domain is clean', () => {
    expect(codes('def map m { from: md.Day, to: md.Month, calc: monthOfDate }')).not.toContain(
      DiagnosticCode.MdCalcTypeMismatch
    );
  });
});

describe('Stage 2D — cardinality + table-backed', () => {
  it('explicit 1:1 on a calc map → md/calc-cardinality-conflict', () => {
    expect(
      codes('def map m { from: md.Day, to: md.Month, calc: monthOfDate, cardinality: { from: "1", to: "1" } }')
    ).toContain(DiagnosticCode.MdCalcCardinalityConflict);
  });

  it('a table-backed map with no md2db_map → md/table-map-no-binding (warning)', () => {
    expect(codes('def map m { from: md.Day, to: md.Month }')).toContain(DiagnosticCode.MdTableMapNoBinding);
  });

  it('a calc map does not trigger table-map-no-binding', () => {
    expect(codes('def map m { from: md.Day, to: md.Month, calc: monthOfDate }')).not.toContain(
      DiagnosticCode.MdTableMapNoBinding
    );
  });
});
