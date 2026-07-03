import { describe, it, expect } from 'vitest';
import { DiagnosticCode } from '@modeler/parser';
import { lintOne } from './helpers.js';

function codes(src: string) {
  return lintOne('file:///m.ttrm', src).map((d) => d.code);
}

describe('Stage 2C — domain validator', () => {
  it('kind on a continuous (scalar) domain → md/kind-on-scalar', () => {
    expect(codes('model md\ndef domain M { type: decimal, kind: calc }')).toContain(DiagnosticCode.MdKindOnScalar);
  });

  it('kind on a discrete int domain (calendar level) is clean', () => {
    expect(codes('model md\ndef domain Year { type: int, kind: calc }')).not.toContain(DiagnosticCode.MdKindOnScalar);
  });

  it('a range clause with a non-range value → md/bad-restrict-value', () => {
    expect(codes('model md\ndef domain M { type: int, restrict: { range: "oops" } }')).toContain(
      DiagnosticCode.MdBadRestrictValue
    );
  });

  it('a well-formed range/members domain is clean', () => {
    const clean =
      'model md\ndef domain Month { type: int, kind: calc, restrict: { range: 1..12 } }';
    const c = codes(clean);
    expect(c).not.toContain(DiagnosticCode.MdBadRestrictValue);
    expect(c).not.toContain(DiagnosticCode.MdKindOnScalar);
  });

  it('members on a continuous type → md/bad-restrict-value', () => {
    expect(
      codes('model md\ndef domain M { type: decimal, restrict: { members: { "A": { en: "A" } } } }')
    ).toContain(DiagnosticCode.MdBadRestrictValue);
  });

  it('an unknown restrict clause → md/unknown-restrict-clause (warning)', () => {
    expect(codes('model md\ndef domain M { type: int, restrict: { wibble: 3 } }')).toContain(
      DiagnosticCode.MdUnknownRestrictClause
    );
  });
});

describe('Stage 2C — attribute per-schema validator', () => {
  it('an MD attribute without domain → md/attr-needs-domain', () => {
    expect(
      codes('model md\ndef dimension D { key: x, attributes: [def attribute x { isKey: true }] }')
    ).toContain(DiagnosticCode.MdAttrNeedsDomain);
  });

  it('an MD attribute carrying type: → md/attr-type-in-md', () => {
    expect(
      codes('model md\ndef dimension D { key: x, attributes: [def attribute x { type: int }] }')
    ).toContain(DiagnosticCode.MdAttrTypeInMd);
  });

  it('an ER attribute carrying domain: → er/attr-domain-in-er', () => {
    expect(
      codes('model er schema entity\ndef entity E { attributes: [def attribute x { domain: md.X }] }')
    ).toContain(DiagnosticCode.ErAttrDomainInEr);
  });

  it('a correct MD attribute (domain, no type) is clean', () => {
    const c = codes('model md\ndef domain X { type: int }\ndef dimension D { key: x, attributes: [def attribute x { domain: md.X }] }');
    expect(c).not.toContain(DiagnosticCode.MdAttrNeedsDomain);
    expect(c).not.toContain(DiagnosticCode.MdAttrTypeInMd);
  });
});

describe('Stage 2C — measure additivity validator', () => {
  it('semi-additive with a latestValid override but no validBy → md/semiadditive-no-validby', () => {
    expect(
      codes('model md\ndef domain Money { type: decimal }\ndef measure bal { domain: md.Money, class: semiAdditive, aggregation: { default: sum, time: latestValid } }')
    ).toContain(DiagnosticCode.MdSemiadditiveNoValidby);
  });

  it('semi-additive with validBy present is clean', () => {
    expect(
      codes('model md\ndef domain Money { type: decimal }\ndef dimension Time { key: d, attributes: [def attribute d { domain: md.Money }] }\ndef measure bal { domain: md.Money, class: semiAdditive, aggregation: { default: sum, time: latestValid }, validBy: d }')
    ).not.toContain(DiagnosticCode.MdSemiadditiveNoValidby);
  });

  it('non-additive with a per-dimension recompute → md/nonadditive-recompute-unsupported', () => {
    expect(
      codes('model md\ndef domain Money { type: decimal }\ndef measure r { domain: md.Money, class: nonAdditive, aggregation: { default: sum, time: recompute } }')
    ).toContain(DiagnosticCode.MdNonadditiveRecomputeUnsupported);
  });

  it('a default additive measure with a single sum is clean', () => {
    const c = codes('model md\ndef domain Money { type: decimal }\ndef measure net { domain: md.Money, aggregation: sum }');
    expect(c).not.toContain(DiagnosticCode.MdSemiadditiveNoValidby);
    expect(c).not.toContain(DiagnosticCode.MdNonadditiveRecomputeUnsupported);
  });
});
