import { describe, it, expect } from 'vitest';
import { maskPlaceholders } from '../mask.js';

/** Every char of a `{name}` placeholder becomes a space except the inner text. */
const SP = ' ';

describe('maskPlaceholders (contracts §3a)', () => {
  it('masks a single placeholder, preserving the inner text and span', () => {
    const r = maskPlaceholders('WHERE n = {nazev}');
    expect(r.masked).toBe(`WHERE n = ${SP}nazev${SP}`);
    expect(r.placeholders).toEqual([{ offset: 10, length: 7, name: 'nazev' }]);
  });

  it('length invariant holds (masked === input length)', () => {
    const input = 'WHERE n = {nazev}';
    expect(maskPlaceholders(input).masked.length).toBe(input.length);
  });

  it('masks multiple placeholders with original-string offsets', () => {
    const input = 'a={x} AND b={yy}';
    const r = maskPlaceholders(input);
    expect(r.masked).toBe('a= x  AND b= yy ');
    expect(r.masked.length).toBe(input.length);
    expect(r.placeholders).toEqual([
      { offset: 2, length: 3, name: 'x' },
      { offset: 12, length: 4, name: 'yy' },
    ]);
  });

  it('handles a placeholder at the very start and end', () => {
    const r = maskPlaceholders('{a} = {b}');
    expect(r.masked).toBe(' a  =  b ');
    expect(r.placeholders.map((p) => p.name)).toEqual(['a', 'b']);
  });

  it('masks accented identifiers (TTR IDENT allows Latin-Extended)', () => {
    const input = 'WHERE x = {název_produktu}';
    const r = maskPlaceholders(input);
    expect(r.masked).toBe(`WHERE x = ${SP}název_produktu${SP}`);
    expect(r.masked.length).toBe(input.length);
    expect(r.placeholders[0]).toEqual({ offset: 10, length: 16, name: 'název_produktu' });
  });

  it('leaves an unbalanced opening brace untouched', () => {
    const input = 'SELECT {oops FROM t';
    const r = maskPlaceholders(input);
    expect(r.masked).toBe(input);
    expect(r.placeholders).toEqual([]);
  });

  it('leaves a stray closing brace untouched', () => {
    const input = 'a } b';
    expect(maskPlaceholders(input).masked).toBe(input);
  });

  it('leaves a `{` not followed by an identifier untouched', () => {
    const input = '{ 1+1 }';
    const r = maskPlaceholders(input);
    expect(r.masked).toBe(input);
    expect(r.placeholders).toEqual([]);
  });

  it('property: length is preserved for awkward inputs', () => {
    const cases = [
      '',
      '{}',
      '{{a}}',
      '{a}{b}{c}',
      'no braces here',
      '{a} {1} {b_2}',
      'trailing {',
      '} leading',
      'SELECT {a}, {b} FROM t WHERE c = {d_3} AND e IN ({f})',
      '{Å}{ž}{_x9}',
    ];
    for (const input of cases) {
      expect(maskPlaceholders(input).masked.length).toBe(input.length);
    }
  });

  it('restores 100% lex: zero residual braces over real S0.2 corpus shapes', () => {
    // The real `{param}` shapes from the project corpus (snake_case + accented).
    const sql = [
      'SELECT * FROM dbo.Produkt',
      'WHERE id_produktu = {id_produktu}',
      "  AND nazev_artiklu LIKE {hledany_vyraz}",
      '  AND datum >= {datum_sloupec}',
      '  AND id_obchodniho_zastupce = {id_obchodniho_zastupce}',
      '  AND kod_trzni_skupiny = {kod_trzni_skupiny}',
    ].join('\n');
    const r = maskPlaceholders(sql);
    expect(r.masked).not.toMatch(/[{}]/);
    expect(r.masked.length).toBe(sql.length);
    expect(r.placeholders).toHaveLength(5);
  });
});
