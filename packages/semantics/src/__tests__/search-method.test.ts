// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { DiagnosticCode } from '@tatrman/parser';
import type { Document, SearchBlock } from '@tatrman/parser';
import { effectiveMatchMethod, validateSearchMethods } from '../search-method.js';

// RV-P1.5 T4 (grammar 0.12, RV-32) — the semantics layer owns the MEANING of the
// match method: the EXACT/TYPOS/TOKENS vocabulary, the arity rule, the RV-32
// default (TYPOS(1)), and the `fuzzy` deprecation that maps the old boolean onto
// the new attribute. The parser stays mechanical.

function parse(src: string): Document {
  return parseString(src, 'file:///m.ttrm').ast!;
}

function searchOf(src: string): SearchBlock | undefined {
  return (parse(src).definitions[0] as { search?: SearchBlock }).search;
}

function diagnose(src: string) {
  return validateSearchMethods(parse(src));
}

describe('effectiveMatchMethod — the authored attribute', () => {
  it('`method: TYPOS(2)` resolves to TYPOS with maxDistance 2', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable method: TYPOS(2) } }'));
    expect(m).toMatchObject({ name: 'TYPOS', maxDistance: 2, origin: 'authored' });
  });

  it('`method: TYPOS` with no argument takes the RV-32 default distance of 1', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable method: TYPOS } }'));
    expect(m).toMatchObject({ name: 'TYPOS', maxDistance: 1, origin: 'authored' });
  });

  it('`method: EXACT` and `method: TOKENS` resolve with no distance', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { searchable method: EXACT } }')))
      .toMatchObject({ name: 'EXACT', origin: 'authored' });
    const tokens = effectiveMatchMethod(searchOf('def table T { search { searchable method: TOKENS } }'));
    expect(tokens).toMatchObject({ name: 'TOKENS', origin: 'authored' });
    expect(tokens?.maxDistance).toBeUndefined();
  });

  it('the method name is case-insensitive on the way in, canonical on the way out', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { searchable method: typos(2) } }')))
      .toMatchObject({ name: 'TYPOS', maxDistance: 2 });
  });
});

describe('effectiveMatchMethod — inclusion and the RV-32 default', () => {
  it('bare `searchable` is included and takes the default TYPOS(1)', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable } }'));
    expect(m).toMatchObject({ name: 'TYPOS', maxDistance: 1, origin: 'default' });
  });

  it('`searchable: true` with no method also takes the default', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable: true } }'));
    expect(m).toMatchObject({ name: 'TYPOS', maxDistance: 1, origin: 'default' });
  });

  it('`searchable: false` is NOT included — there is no method at all', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { searchable: false } }'))).toBeUndefined();
  });

  it('a search block with no `searchable` at all is not included', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { keywords: { cs: ["a"] } } }'))).toBeUndefined();
    expect(effectiveMatchMethod(undefined)).toBeUndefined();
  });
});

describe('effectiveMatchMethod — the `fuzzy` legacy mapping (T4)', () => {
  it('`fuzzy: true` maps to TYPOS(1)', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable: true, fuzzy: true } }'));
    expect(m).toMatchObject({ name: 'TYPOS', maxDistance: 1, origin: 'legacy-fuzzy' });
  });

  it('`fuzzy: false` maps to EXACT — the authored "no fuzzy" intent survives the bump', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable: true, fuzzy: false } }'));
    expect(m).toMatchObject({ name: 'EXACT', origin: 'legacy-fuzzy' });
  });

  it('an explicit `method` wins over a legacy `fuzzy` on the same block', () => {
    const m = effectiveMatchMethod(searchOf('def table T { search { searchable method: TOKENS, fuzzy: true } }'));
    expect(m).toMatchObject({ name: 'TOKENS', origin: 'authored' });
  });
});

describe('validateSearchMethods — the deprecation diagnostic', () => {
  it('`fuzzy: true` fires SearchFuzzyDeprecated with the migration hint', () => {
    const d = diagnose('def table T { search { searchable: true, fuzzy: true } }');
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.SearchFuzzyDeprecated);
    expect(d[0].severity).toBe('warning');
    expect(d[0].message).toContain('searchable method: TYPOS(1)');
  });

  it('`fuzzy: false` fires the same deprecation, pointing at EXACT', () => {
    const d = diagnose('def table T { search { searchable: true, fuzzy: false } }');
    expect(d).toHaveLength(1);
    expect(d[0].message).toContain('searchable method: EXACT');
  });

  it('a block with a `method` and no `fuzzy` is clean', () => {
    expect(diagnose('def table T { search { searchable method: TYPOS(2) } }')).toEqual([]);
    expect(diagnose('def table T { search { searchable } }')).toEqual([]);
  });

  it('skips a TOP-LEVEL `def column`/`def attribute` — the portable-validator contract', () => {
    // Excluded in all three targets (see `searchBlocksOf`), so the conformance
    // semantic dumps stay byte-identical.
    expect(diagnose('def attribute a { type: text, search { searchable: true, fuzzy: true } }')).toEqual([]);
    expect(diagnose('def column c { type: varchar, search { searchable: true, fuzzy: true } }')).toEqual([]);
  });

  it('fires on nested columns and attributes, not just top-level defs', () => {
    const col = diagnose('def table T { columns: [def column C { type: varchar, search { searchable: true, fuzzy: true } }] }');
    expect(col).toHaveLength(1);
    const attr = diagnose('def entity E { attributes: [def attribute A { type: text, search { searchable: true, fuzzy: true } }] }');
    expect(attr).toHaveLength(1);
  });
});

describe('validateSearchMethods — the closed vocabulary and the arity rule', () => {
  it('an unknown method name is an error naming the three legal methods', () => {
    const d = diagnose('def table T { search { searchable method: TYPSO(2) } }');
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.UnknownMatchMethod);
    expect(d[0].severity).toBe('error');
    expect(d[0].message).toContain('TOKENS');
  });

  it('an unknown method still resolves to the default, so consumers keep working', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { searchable method: TYPSO } }')))
      .toMatchObject({ name: 'TYPOS', maxDistance: 1, origin: 'default' });
  });

  it('EXACT and TOKENS take no argument', () => {
    const d = diagnose('def table T { search { searchable method: EXACT(2) } }');
    expect(d).toHaveLength(1);
    expect(d[0].code).toBe(DiagnosticCode.InvalidMatchMethodArgument);
    expect(d[0].message).toContain('takes no argument');
    expect(diagnose('def table T { search { searchable method: TOKENS(1) } }')).toHaveLength(1);
  });

  it("TYPOS's distance must be a whole number in 1..3 — the range ttr-lexicon accepts", () => {
    for (const bad of ['TYPOS(0)', 'TYPOS(-1)', 'TYPOS(1.5)', 'TYPOS(4)', 'TYPOS(7)']) {
      const d = diagnose(`def table T { search { searchable method: ${bad} } }`);
      expect(d, bad).toHaveLength(1);
      expect(d[0].code, bad).toBe(DiagnosticCode.InvalidMatchMethodArgument);
      expect(d[0].message, bad).toContain('1..3');
    }
    for (const ok of ['TYPOS(1)', 'TYPOS(2)', 'TYPOS(3)']) {
      expect(diagnose(`def table T { search { searchable method: ${ok} } }`), ok).toHaveLength(0);
    }
  });

  it('an out-of-range TYPOS distance falls back to the default distance', () => {
    expect(effectiveMatchMethod(searchOf('def table T { search { searchable method: TYPOS(0) } }')))
      .toMatchObject({ name: 'TYPOS', maxDistance: 1 });
  });
});
