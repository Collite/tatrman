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
    // The directive is a leading comment on `def project m` (line 2).
    const idx = index(`model db schema dbo
// ttr-disable-next-line missing-description
def project m {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('some-other-rule', 3)).toBe(false);
  });

  it('ttr-disable-next-line with no ids suppresses all rules on that line', () => {
    const idx = index(`model db schema dbo
// ttr-disable-next-line
def project m {}
`);
    expect(idx.isSuppressed('anything', 3)).toBe(true);
  });

  it('ttr-disable-line (trailing) suppresses its own line', () => {
    const idx = index(`model db schema dbo
def project m {} // ttr-disable-line missing-description
`);
    expect(idx.isSuppressed('missing-description', 2)).toBe(true);
  });

  it('ttr-disable / ttr-enable form a range', () => {
    const idx = index(`model db schema dbo
// ttr-disable missing-description
def project a {}
def project b {}
// ttr-enable missing-description
def project c {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('missing-description', 4)).toBe(true);
    expect(idx.isSuppressed('missing-description', 6)).toBe(false);
  });

  it('ttr-disable-file suppresses every line', () => {
    const idx = index(`model db schema dbo
// ttr-disable-file missing-description
def project a {}
`);
    expect(idx.isSuppressed('missing-description', 3)).toBe(true);
    expect(idx.isSuppressed('missing-description', 99)).toBe(true);
  });

  it('unused() reports a directive that matched nothing', () => {
    const idx = index(`model db schema dbo
// ttr-disable-next-line missing-description
def project m {}
`);
    // Never query → the directive is unused.
    const u = idx.unused();
    expect(u.some((x) => x.ruleId === 'missing-description')).toBe(true);
  });

  it('a matched directive is not reported as unused', () => {
    const idx = index(`model db schema dbo
// ttr-disable-next-line missing-description
def project m {}
`);
    idx.isSuppressed('missing-description', 3);
    expect(idx.unused()).toHaveLength(0);
  });
});

describe('suppression integration with the runner', () => {
  it('suppresses a non-correctness rule via ttr-disable-next-line', () => {
    const src = `model db schema dbo
// ttr-disable-next-line missing-description
def table t { columns: [def column id { type: int }] }
`;
    const enabled = recommendedConfig({ 'missing-description': 'warning' });
    const diags = lintOne('s.ttrm', src, { config: enabled });
    expect(diags.some((d) => d.ruleId === 'missing-description')).toBe(false);
  });

  it('cannot suppress a correctness rule — emits ttrlint/cannot-suppress and keeps the diagnostic', () => {
    // table-no-columns is correctness; a directive on its line must not drop it.
    const src = `model db schema dbo
// ttr-disable-next-line table-no-columns
def table empty { description: "x" }
`;
    const diags = lintOne('s.ttrm', src);
    expect(diags.some((d) => d.ruleId === 'table-no-columns')).toBe(true);
    expect(diags.some((d) => d.code === 'ttrlint/cannot-suppress')).toBe(true);
  });
});
