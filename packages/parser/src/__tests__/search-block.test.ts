import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { Definition, TableDef, ViewDef, RelationDef, EntityDef, QueryDef, RoleDef, AttributeDef } from '../index.js';

function getDef<T extends Definition>(src: string, index = 0): T {
  return parseString(src).ast!.definitions[index] as T;
}

describe('search block', () => {
  it('search { searchable: true, fuzzy: true } on table parses with 0 errors', () => {
    const result = parseString('def table MY_TABLE { search { searchable: true, fuzzy: true } }');
    expect(result.errors).toHaveLength(0);
    const def = getDef<TableDef>('def table MY_TABLE { search { searchable: true, fuzzy: true } }');
    expect(def.kind).toBe('table');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on inline column parses with 0 errors', () => {
    const src = 'def table MY_TABLE { columns: [def column NAZEV { type: varchar, search { searchable: true, fuzzy: true } }] }';
    const result = parseString(src);
    expect(result.errors).toHaveLength(0);
    const def = getDef<TableDef>(src);
    expect(def.kind).toBe('table');
    expect(def.columns?.[0]?.search?.searchable).toBe(true);
    expect(def.columns?.[0]?.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on view parses with 0 errors', () => {
    const def = getDef<ViewDef>('def view MY_VIEW { search { searchable: true, fuzzy: true } }');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on relation parses with 0 errors', () => {
    const def = getDef<RelationDef>('def relation MY_REL { search { searchable: true, fuzzy: true } }');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on entity still works', () => {
    const def = getDef<EntityDef>('def entity MY_ENTITY { search { searchable: true, fuzzy: true } }');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on attribute still works', () => {
    const def = getDef<EntityDef>('def entity E { attributes: [def attribute A { type: text, search { searchable: true, fuzzy: true } }] }');
    const attr = def.attributes?.[0] as AttributeDef;
    expect(attr?.search?.searchable).toBe(true);
    expect(attr?.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on query still works', () => {
    const def = getDef<QueryDef>('def query MY_QUERY { language: SQL, sourceText: "SELECT 1", search { searchable: true, fuzzy: true } }');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('search { searchable: true, fuzzy: true } on role still works', () => {
    const def = getDef<RoleDef>('def role MY_ROLE { label: { cs: "Role" }, search { searchable: true, fuzzy: true } }');
    expect(def.search?.searchable).toBe(true);
    expect(def.search?.fuzzy).toBe(true);
  });

  it('top-level def column c { searchable: true } produces a parse error', () => {
    const result = parseString('def column c { type: varchar, searchable: true }');
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('top-level def attribute a { searchable: true } produces a parse error', () => {
    const result = parseString('def attribute a { type: text, searchable: true }');
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('search { keywords {...}, keywords {...} } records "keywords" in duplicateProperties', () => {
    const def = getDef<EntityDef>('def entity E { search { keywords: { cs: ["a"] }, keywords: { en: ["b"] } } }');
    expect(def.search?.duplicateProperties).toContain('keywords');
  });

  it('clean search block yields no duplicateProperties', () => {
    const def = getDef<EntityDef>('def entity E { search { keywords: { cs: ["a"] }, patterns: ["x"] } }');
    expect(def.search?.duplicateProperties ?? []).toHaveLength(0);
  });

  it('block mixing keywords, patterns, searchable, fuzzy parses each field correctly', () => {
    const def = getDef<EntityDef>('def entity E { search { keywords: { cs: ["kw"] }, patterns: ["p1"], searchable: true, fuzzy: true } }');
    expect(def.search!.keywords?.entries?.['cs']?.[0]).toBe('kw');
    expect(def.search!.patterns).toEqual(['p1']);
    expect(def.search!.searchable).toBe(true);
    expect(def.search!.fuzzy).toBe(true);
  });
});