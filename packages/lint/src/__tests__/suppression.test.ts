import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { buildSuppressionIndex } from '../suppression.js';
import { lintOne, recommendedConfig } from './helpers.js';

function index(src: string) {
  const ast = parseString(src, 's.ttrm').ast!;
  return buildSuppressionIndex(ast);
}

describe('suppression directives', () => {
  it('ttr-disable-next-line suppresses the following def line (with explicit id)', () => {
    // The directive is a leading comment on `def model m` (line 2).
    const idx = index(`schema db namespace dbo
// ttr-disable-next-line missing-description
def model m {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('some-other-rule', 3)).toBe(false);
  });

  it('ttr-disable-next-line with no ids suppresses all rules on that line', () => {
    const idx = index(`schema db namespace dbo
// ttr-disable-next-line
def model m {}
`);
    expect(idx.isSuppressed('anything', 3)).toBe(true);
  });

  it('ttr-disable-line (trailing) suppresses its own line', () => {
    const idx = index(`schema db namespace dbo
def model m {} // ttr-disable-line missing-description
`);
    expect(idx.isSuppressed('missing-description', 2)).toBe(true);
  });

  it('ttr-disable / ttr-enable form a range', () => {
    const idx = index(`schema db namespace dbo
// ttr-disable missing-description
def model a {}
def model b {}
// ttr-enable missing-description
def model c {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('missing-description', 4)).toBe(true);
    expect(idx.isSuppressed('missing-description', 6)).toBe(false);
  });

  it('ttr-disable-file suppresses every line', () => {
    const idx = index(`schema db namespace dbo
// ttr-disable-file missing-description
def model a {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('missing-description', 99)).toBe(true);
  });

  it('unused() reports a directive that matched nothing', () => {
    const idx = index(`schema db namespace dbo
// ttr-disable-next-line missing-description
def model m {}
`);
    // Never query → the directive is unused.
    const u = idx.unused();
    expect(u.some((x) => x.ruleId === 'missing-description')).toBe(true);
  });

  it('a matched directive is not reported as unused', () => {
    const idx = index(`schema db namespace dbo
// ttr-disable-next-line missing-description
def model m {}
`);
    idx.isSuppressed('missing-description', 3);
    expect(idx.unused()).toHaveLength(0);
  });
});

describe('suppression integration with the runner', () => {
  it('suppresses a non-correctness rule via ttr-disable-next-line', () => {
    const src = `schema db namespace dbo
// ttr-disable-next-line missing-description
def table t { columns: [def column id { type: int }] }
`;
    const enabled = recommendedConfig({ 'missing-description': 'warning' });
    const diags = lintOne('s.ttrm', src, { config: enabled });
    expect(diags.some((d) => d.ruleId === 'missing-description')).toBe(false);
  });

  it('cannot suppress a correctness rule — emits ttrlint/cannot-suppress and keeps the diagnostic', () => {
    // table-no-columns is correctness; a directive on its line must not drop it.
    const src = `schema db namespace dbo
// ttr-disable-next-line table-no-columns
def table empty { description: "x" }
`;
    const diags = lintOne('s.ttrm', src);
    expect(diags.some((d) => d.ruleId === 'table-no-columns')).toBe(true);
    expect(diags.some((d) => d.code === 'ttrlint/cannot-suppress')).toBe(true);
  });
});
