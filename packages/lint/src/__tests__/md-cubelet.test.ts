// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@tatrman/parser';
import { lintAllOne } from './helpers.js';

const BASE = `model md
def domain Day { type: date }
def domain Month { type: int, kind: calc, restrict: { range: 1..12 } }
def domain Money { type: decimal }
def dimension Time {
  key: day,
  attributes: [def attribute day { domain: md.Day }, def attribute month { domain: md.Month }]
}
def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }
def measure net { domain: md.Money, aggregation: sum }`;

function codes(cubelet: string) {
  return lintAllOne('file:///m.ttrm', `${BASE}\n${cubelet}`).map((d) => d.code);
}

describe('Stage 2F — cubelet validator', () => {
  it('a clean cubelet (resolvable grain + measures) has no grain diagnostics', () => {
    const c = codes('def cubelet sales { grain: [Time.day], measures: [net] }');
    expect(c).not.toContain(DiagnosticCode.MdGrainRefUnknown);
    expect(c).not.toContain(DiagnosticCode.MdGrainNotLeaf);
  });

  it('an unknown grain ref → md/grain-ref-unknown (not md/unknown-ref)', () => {
    const c = codes('def cubelet sales { grain: [Time.nope], measures: [net] }');
    expect(c).toContain(DiagnosticCode.MdGrainRefUnknown);
    expect(c).not.toContain(DiagnosticCode.MdUnknownRef);
  });

  it('an unknown measure ref → md/unknown-ref', () => {
    expect(codes('def cubelet sales { grain: [Time.day], measures: [nope] }')).toContain(
      DiagnosticCode.MdUnknownRef
    );
  });

  it('a grain attribute coarser than another in the grain → md/grain-not-leaf (warning)', () => {
    // day → month (N:1), so month is coarser than day; both in the grain.
    expect(codes('def cubelet sales { grain: [Time.day, Time.month], measures: [net] }')).toContain(
      DiagnosticCode.MdGrainNotLeaf
    );
  });
});
