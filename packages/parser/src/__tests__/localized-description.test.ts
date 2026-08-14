// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type {
  Definition,
  TableDef,
  ColumnDef,
  EntityDef,
  AttributeDef,
  QueryDef,
  RoleDef,
} from '../index.js';

// NLS-P10 (⚑GXP-D7, grammar 0.13) — `description:` accepts the localised map form
// `{ en: "...", cs: "..." }` everywhere it already accepted a string. The precedent
// copied at every layer is `displayLabel` (`localizedString`).
//
// Two AST fields, never one: `description` keeps the PLAIN form only (so every
// existing consumer is byte-unchanged), and `descriptionLocalized` carries the map.
// The parser stays mechanical — it does NOT fold the map down to one locale; that
// choice belongs to the reader (Veles' D7 fallback chain, contracts §7).

function getDef<T extends Definition>(src: string, index = 0): T {
  return parseString(src).ast!.definitions[index] as T;
}

describe('NLS-P10 — localized description (plain form unchanged)', () => {
  it('(a) a plain string description still lands in `description`', () => {
    const src = 'def entity Product { description: "the product" }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.description?.value).toBe('the product');
    expect(def.descriptionLocalized).toBeUndefined();
  });

  it('(b) a triple-string description is still dedented into `description`', () => {
    const src = 'def entity Product { description: """\n    line one\n    line two\n    """ }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.description?.value).toBe('line one\nline two\n');
    expect(def.descriptionLocalized).toBeUndefined();
  });
});

describe('NLS-P10 — localized description (map form)', () => {
  it('(c) a two-locale map lands in `descriptionLocalized`, `description` stays unset', () => {
    const src = 'def entity Product { description: { en: "product name", cs: "Název produktu" } }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.description).toBeUndefined();
    expect(def.descriptionLocalized?.entries).toEqual({
      en: 'product name',
      cs: 'Název produktu',
    });
  });

  it('(d) a single-entry map is legal', () => {
    const src = 'def entity Product { description: { cs: "Produkt" } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.descriptionLocalized?.entries).toEqual({ cs: 'Produkt' });
  });

  it('(e) an EMPTY map parses (no parse error) and yields an empty entry set', () => {
    // Ruling (NLS-P10 T1): the empty map is NOT a parse error — the parser stays
    // mechanical (repo invariant) and the D7 fallback chain already ends at "".
    // It is a LINT warning instead (`localized-description-empty`, @tatrman/lint).
    const src = 'def entity Product { description: {} }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.description).toBeUndefined();
    expect(def.descriptionLocalized?.entries).toEqual({});
  });

  it('(f) a triple-string value inside the map is dedented like anywhere else', () => {
    const src = 'def entity Product { description: { en: """\n    a\n    b\n    """ } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<EntityDef>(src);
    expect(def.descriptionLocalized?.entries.en).toBe('a\nb\n');
  });

  it('(g) the map carries a source location spanning the `{ … }`', () => {
    const src = 'def entity Product { description: { en: "x" } }';
    const def = getDef<EntityDef>(src);
    const loc = def.descriptionLocalized!.source;
    expect(src.slice(loc.offsetStart, loc.offsetEnd)).toBe('{ en: "x" }');
  });
});

describe('NLS-P10 — the map form is legal on every kind that has `description`', () => {
  it('(h) table + column', () => {
    const src = [
      'def table sales {',
      '  description: { en: "sales", cs: "prodeje" },',
      '  columns: [',
      '    def column amount { type: decimal, description: { en: "amount", cs: "částka" } }',
      '  ]',
      '}',
    ].join('\n');
    expect(parseString(src).errors).toHaveLength(0);
    const table = getDef<TableDef>(src);
    expect(table.descriptionLocalized?.entries).toEqual({ en: 'sales', cs: 'prodeje' });
    const column = table.columns![0] as ColumnDef;
    expect(column.descriptionLocalized?.entries).toEqual({ en: 'amount', cs: 'částka' });
  });

  it('(i) entity + attribute', () => {
    const src = [
      'def entity Product {',
      '  description: { en: "a product", cs: "produkt" },',
      '  attributes: [',
      '    def attribute name { type: string, description: { en: "its name", cs: "jméno" } }',
      '  ]',
      '}',
    ].join('\n');
    expect(parseString(src).errors).toHaveLength(0);
    const entity = getDef<EntityDef>(src);
    expect(entity.descriptionLocalized?.entries).toEqual({ en: 'a product', cs: 'produkt' });
    const attr = entity.attributes![0] as AttributeDef;
    expect(attr.descriptionLocalized?.entries).toEqual({ en: 'its name', cs: 'jméno' });
  });

  it('(j) query', () => {
    const src = 'def query top_products { description: { en: "top", cs: "nej" }, language: SQL }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<QueryDef>(src);
    expect(def.descriptionLocalized?.entries).toEqual({ en: 'top', cs: 'nej' });
  });

  it('(k) role', () => {
    const src = 'def role customer { description: { en: "buyer", cs: "kupující" } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<RoleDef>(src);
    expect(def.descriptionLocalized?.entries).toEqual({ en: 'buyer', cs: 'kupující' });
  });

  it('(l) the plain and map forms coexist across definitions in one document', () => {
    const src = [
      'def entity A { description: "plain" }',
      'def entity B { description: { en: "mapped" } }',
    ].join('\n');
    expect(parseString(src).errors).toHaveLength(0);
    const a = getDef<EntityDef>(src, 0);
    const b = getDef<EntityDef>(src, 1);
    expect(a.description?.value).toBe('plain');
    expect(a.descriptionLocalized).toBeUndefined();
    expect(b.description).toBeUndefined();
    expect(b.descriptionLocalized?.entries).toEqual({ en: 'mapped' });
  });
});
