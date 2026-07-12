// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { buildSymbolDetail } from '../model-graph.js';
import { ProjectSymbolTable, Resolver, ReferenceIndex } from '@tatrman/semantics';
import type { ResolvedManifest } from '@tatrman/semantics';

function makeManifest(preferredLanguage = 'en'): ResolvedManifest {
  return { preferredLanguage };
}

function makeResolver(table: ProjectSymbolTable): Resolver {
  return new Resolver(table);
}

const documents = new Map<string, string>();

function parseAndUpsert(
  table: ProjectSymbolTable,
  refIndex: ReferenceIndex,
  resolver: Resolver,
  content: string,
  uri = 'file:///test.ttrm'
): void {
  documents.set(uri, content);
  const result = parseString(content, uri);
  if (!result.ast) return;
  const schema = result.ast.modelDirective?.modelCode ?? 'er';
  const namespace = result.ast.modelDirective?.schema ?? 'ns';
  table.upsertDocument(uri, result.ast, schema, namespace);
  refIndex.upsertDocument(uri, result.ast, schema, namespace, resolver);
}

describe('buildSymbolDetail', () => {
  it('entity with two attributes → perKindData.kind === "entity", attributes.length === 2', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    parseAndUpsert(table, refIndex, resolver, `
model er schema ent
def entity foo {
  attributes: [
    def attribute id { type: int, isKey: true },
    def attribute name { type: text }
  ]
}
    `);

    const result = buildSymbolDetail(
      'er.entity.foo',
      table,
      resolver,
      refIndex,
      manifest,
      (uri) => documents.get(uri) ?? null,
      parseString
    );

    expect(result).not.toBeNull();
    expect(result!.perKindData.kind).toBe('entity');
    expect((result!.perKindData as { attributes: unknown[] }).attributes).toHaveLength(2);
  });

  it('table with primaryKey and three columns → perKindData.kind === "table", primaryKey === ["id"], columns.length === 3', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    parseAndUpsert(table, refIndex, resolver, `
model db schema dbo
def table products {
  primaryKey: ["id"],
  columns: [
    def column id { type: int, isKey: true },
    def column name { type: text },
    def column price { type: numeric }
  ]
}
    `);

    const result = buildSymbolDetail(
      'db.dbo.table.products',
      table,
      resolver,
      refIndex,
      manifest,
      (uri) => documents.get(uri) ?? null,
      parseString
    );

    expect(result).not.toBeNull();
    expect(result!.perKindData.kind).toBe('table');
    const pk = (result!.perKindData as { primaryKey: string[] }).primaryKey;
    expect(pk).toEqual(['id']);
    const cols = (result!.perKindData as { columns: unknown[] }).columns;
    expect(cols).toHaveLength(3);
  });

  it('preferredLanguage = cs with displayLabel cs+en → label === "Artikl"', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest('cs');

    parseAndUpsert(table, refIndex, resolver, `
model er schema ent
def entity artikl {
  displayLabel: { cs: "Artikl", en: "Item" },
  attributes: [def attribute id { type: int }]
}
    `);

    const result = buildSymbolDetail(
      'er.entity.artikl',
      table,
      resolver,
      refIndex,
      manifest,
      (uri) => documents.get(uri) ?? null,
      parseString
    );

    expect(result).not.toBeNull();
    expect(result!.label).toBe('Artikl');
  });

  it('preferredLanguage = de with displayLabel cs+en → fallback to def.name (contract v5)', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest('de');

    parseAndUpsert(table, refIndex, resolver, `
model er schema ent
def entity artikl {
  displayLabel: { cs: "Artikl", en: "Item" },
  attributes: [def attribute id { type: int }]
}
    `);

    const result = buildSymbolDetail(
      'er.entity.artikl',
      table,
      resolver,
      refIndex,
      manifest,
      (uri) => documents.get(uri) ?? null,
      parseString
    );

    expect(result).not.toBeNull();
    expect(result!.label).toBe('artikl');
  });

  it('entity without description → description === null', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    parseAndUpsert(table, refIndex, resolver, `
model er schema ent
def entity foo { attributes: [def attribute id { type: int }] }
    `);

    const result = buildSymbolDetail(
      'er.entity.foo', table, resolver, refIndex, manifest,
      (uri) => documents.get(uri) ?? null, parseString
    );

    expect(result).not.toBeNull();
    expect(result!.description).toBeNull();
  });

  it('unknown qname → returns null', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    const result = buildSymbolDetail(
      'er.entity.does_not_exist',
      table,
      resolver,
      refIndex,
      manifest,
      () => null,
      parseString
    );

    expect(result).toBeNull();
  });

  it('two relations targeting the same entity → referencedBy.length === 2', () => {
    const table = new ProjectSymbolTable();
    const resolver = makeResolver(table);
    const refIndex = new ReferenceIndex();
    const manifest = makeManifest();

    parseAndUpsert(table, refIndex, resolver, `
model er schema ent
def entity parent { attributes: [def attribute id { type: int, isKey: true }] }
def entity ref_a { attributes: [def attribute id { type: int, isKey: true }] }
def entity ref_b { attributes: [def attribute id { type: int, isKey: true }] }
def relation r1 { from: er.entity.ref_a, to: er.entity.parent, cardinality: { from: "1", to: "*" } }
def relation r2 { from: er.entity.ref_b, to: er.entity.parent, cardinality: { from: "1", to: "*" } }
    `);

    const result = buildSymbolDetail(
      'er.entity.parent',
      table,
      resolver,
      refIndex,
      manifest,
      (uri) => documents.get(uri) ?? null,
      parseString
    );

    expect(result).not.toBeNull();
    // Each relation contributes one ref to `parent` via its `to:` field.
    expect(result!.referencedBy).toHaveLength(2);
  });
});