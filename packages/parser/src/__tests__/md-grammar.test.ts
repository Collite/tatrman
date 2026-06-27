import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import { DiagnosticCode } from '../diagnostics.js';
import { MD_LOGICAL, MD_BINDING, MD_IDPART } from './md-fixtures.js';

// Stage 1B — grammar 3.1 makes every MD construct PARSE. AST shape/spans are
// asserted in 1C/1D; here we only assert parse-success (no parse errors) and
// that each definition is recognised (none silently dropped).

function parseErrors(src: string, uri: string) {
  const { errors } = parseString(src, uri);
  return errors.filter((e) => e.code === DiagnosticCode.ParseError);
}

describe('MD grammar 3.1 — parse-success', () => {
  it('model md logical model parses with no errors', () => {
    const { ast, errors } = parseString(MD_LOGICAL, 'file:///model.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.schemaDirective?.schemaCode).toBe('md');
    // 12 domains + 2 dimensions + 6 maps + 1 hierarchy + 2 measures + 2 cubelets.
    expect(ast?.definitions).toHaveLength(25);
  });

  it('model binding md2* model parses with no errors', () => {
    const { ast, errors } = parseString(MD_BINDING, 'file:///binding.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.schemaDirective?.schemaCode).toBe('binding');
    // md2db_cubelet ×2, md2db_domain, md2db_map, md2er_cubelet.
    expect(ast?.definitions).toHaveLength(5);
  });

  it('new MD keywords still work as cross-reference fragments / bare ids (idPart)', () => {
    const { ast, errors } = parseString(MD_IDPART, 'file:///db.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.definitions).toHaveLength(2);
  });

  it('a domain with a range-literal restrict parses (DOTDOT)', () => {
    expect(parseErrors('model md\ndef domain M { type: int, restrict: { range: 1..12 } }', 'file:///m.ttrm')).toEqual([]);
  });

  it('a calc map with named parens args parses', () => {
    expect(
      parseErrors('model md\ndef map f { from: md.A, to: md.B, calc: fiscalYearOfDate(fiscalYearStartMonth: 4) }', 'file:///m.ttrm')
    ).toEqual([]);
  });

  it('a hierarchy with via-pinned levels parses', () => {
    expect(
      parseErrors('model md\ndef hierarchy h { dimension: md.T, levels: [day, month via md.d2m, quarter] }', 'file:///m.ttrm')
    ).toEqual([]);
  });

  it('a measure with the per-dimension aggregation object parses', () => {
    expect(
      parseErrors('model md\ndef measure m { domain: md.X, class: semiAdditive, aggregation: { default: sum, time: latestValid }, validBy: asOf }', 'file:///m.ttrm')
    ).toEqual([]);
  });
});
