// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { Definition, TableDef, EntityDef, AttributeDef, LexiconEntryDef } from '../index.js';

// RV-P1.5 (grammar 0.12, RV-31/32) — `searchable` is the INCLUSION marker ("include
// this carrier's content into the lexicon") and carries the match METHOD that
// replaces the `fuzzy` boolean: EXACT | TYPOS(n) | TOKENS. The parser stays
// mechanical — the method NAME is captured as authored text and its vocabulary is
// validated in semantics (the `allocation:` precedent).

function getDef<T extends Definition>(src: string, index = 0): T {
  return parseString(src).ast!.definitions[index] as T;
}

describe('RV-P1.5 — searchable method attribute', () => {
  it('(a) `searchable method: TYPOS(2)` parses and captures the argument', () => {
    const src = 'def table T { search { searchable method: TYPOS(2) } }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.method?.name).toBe('TYPOS');
    expect(def.search?.method?.argument).toBe(2);
  });

  it('(b) `searchable method: EXACT` parses with no argument', () => {
    const src = 'def table T { search { searchable method: EXACT } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.method?.name).toBe('EXACT');
    expect(def.search?.method?.argument).toBeUndefined();
  });

  it('(c) `searchable method: TOKENS` parses with no argument', () => {
    const src = 'def table T { search { searchable method: TOKENS } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.method?.name).toBe('TOKENS');
    expect(def.search?.method?.argument).toBeUndefined();
  });

  it('(d) bare `searchable` (no boolean, no method) parses and means included', () => {
    const src = 'def table T { search { searchable } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.method).toBeUndefined();
  });

  it('(d′) the explicit boolean form still parses and may carry a method', () => {
    const src = 'def table T { search { searchable: true method: TYPOS(1) } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.method?.name).toBe('TYPOS');
    expect(def.search?.method?.argument).toBe(1);
  });

  it('(d″) `searchable: false` still parses — inclusion is opt-out-able', () => {
    const src = 'def table T { search { searchable: false } }';
    expect(parseString(src).errors).toHaveLength(0);
    expect(getDef<TableDef>(src).search?.searchable).toBe(false);
  });

  it('(e) legacy `fuzzy: true` still parses in 0.12 (deprecation is semantic)', () => {
    const src = 'def table T { search { searchable: true, fuzzy: true } }';
    expect(parseString(src).errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.search?.fuzzy).toBe(true);
    expect(def.search?.method).toBeUndefined();
  });

  it('carries the method on a nested column and on a nested attribute', () => {
    const table = 'def table T { columns: [def column C { type: varchar, search { searchable method: TOKENS } }] }';
    expect(parseString(table).errors).toHaveLength(0);
    expect(getDef<TableDef>(table).columns?.[0]?.search?.method?.name).toBe('TOKENS');

    const entity = 'def entity E { attributes: [def attribute A { type: text, search { searchable method: TYPOS(3) } }] }';
    expect(parseString(entity).errors).toHaveLength(0);
    const attr = getDef<EntityDef>(entity).attributes?.[0] as AttributeDef;
    expect(attr.search?.method?.argument).toBe(3);
  });

  it('a repeated method is recorded in duplicateProperties like any search sub-property', () => {
    const src = 'def table T { search { searchable method: EXACT, searchable method: TOKENS } }';
    expect(parseString(src).errors).toHaveLength(0);
    expect(getDef<TableDef>(src).search?.duplicateProperties).toContain('searchable');
  });

  it('the method value carries an accurate source location', () => {
    const src = 'def table T { search { searchable method: TYPOS(2) } }';
    const m = getDef<TableDef>(src).search!.method!;
    expect(m.source.line).toBe(1);
    expect(src.slice(m.source.offsetStart, m.source.offsetEnd)).toBe('TYPOS(2)');
  });

  it('`method` stays usable as an ordinary identifier / object key', () => {
    const src = 'model er\ndef entity method { semantics { kind: dimension } }';
    expect(parseString(src).errors).toHaveLength(0);
    expect(getDef<EntityDef>(src, 0).name).toBe('method');
  });
});

// T2(f) — the standalone alias declaration with an attribute-depth md ref target.
// RULED at T1: the RG arc shipped this as `def term <id> { for: <ref>, forms: [...] }`
// (the RS-9 shape); `is_alias_of` in the RV design docs is informal shorthand for it.
describe('RV-P1.5 (f) — alias def with an attribute-depth md ref target', () => {
  it('`def term … { for: md.<dim>.<attr>, forms: [...] }` parses and keeps the dotted target', () => {
    const src = 'model lexicon locale cs\ndef term stredisko { for: md.account.class.expense, forms: ["středisko", "5xx"] }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<LexiconEntryDef>(src);
    expect(def.kind).toBe('term');
    expect(def.target?.path).toBe('md.account.class.expense');
    expect(def.forms).toEqual(['středisko', '5xx']);
  });
});
