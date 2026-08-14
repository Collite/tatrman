// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { buildSymbolDetail } from '../model-graph.js';
import { buildBindingMap } from '../graph-methods.js';
import { ProjectSymbolTable, Resolver, ReferenceIndex } from '@tatrman/semantics';
import type { ResolvedManifest } from '@tatrman/semantics';

// NLS-P10 review — grammar 0.13 let an author write `description: { en: …, cs: … }`,
// and `buildSymbolDetail` is a READER of that property: its `description` is what the
// Designer's detail drawer prints (`designer/src/shell/TextDrawer.tsx`, which falls back
// to "No description."). Before the fix the map form resolved to null, so a bilingual
// model — hartland's `catalog.ttrm`, the exemplar this very phase created — showed no
// descriptions in the editor used to write it.
//
// The chain under test is Veles' D7 chain minus its "no preference" branch (an LSP caller
// always has a preferred language; `ResolvedManifest.preferredLanguage`, default `en`):
//
//   requested language → plain form → `en` → first entry by language code → null

const documents = new Map<string, string>();

function harness(preferredLanguage: string, content: string, uri = 'file:///loc.ttrm') {
  const table = new ProjectSymbolTable();
  const resolver = new Resolver(table);
  const refIndex = new ReferenceIndex();
  const manifest: ResolvedManifest = { preferredLanguage };

  documents.set(uri, content);
  const result = parseString(content, uri);
  const schema = result.ast.modelDirective?.modelCode ?? 'er';
  const namespace = result.ast.modelDirective?.schema ?? 'ns';
  table.upsertDocument(uri, result.ast, schema, namespace);
  refIndex.upsertDocument(uri, result.ast, schema, namespace, resolver);

  return (qname: string) =>
    buildSymbolDetail(
      qname,
      table,
      resolver,
      refIndex,
      manifest,
      (u) => documents.get(u) ?? null,
      parseString,
    );
}

const ER = `
model er schema ent

def entity Bilingual {
  description: { en: "A bilingual description", cs: "Dvojjazyčný popis" }
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity Plain {
  description: "a plain description"
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity EnglishOnly {
  description: { en: "English only" }
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity Neither {
  description: { fr: "Français seulement", de: "Nur Deutsch" }
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity Undescribed {
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity EmptyMap {
  description: {}
  attributes: [ def attribute id { type: int, isKey: true } ]
}
def entity EmptyEntry {
  description: { cs: "", en: "Fallback" }
  attributes: [ def attribute id { type: int, isKey: true } ]
}
`;

function description(lang: string, name: string): string | null | undefined {
  return harness(lang, ER)(`er.entity.${name}`)?.description;
}

describe('localized description in the symbol detail (NLS-P10)', () => {
  it('step 1 — the requested language wins when the map carries it', () => {
    expect(description('cs', 'Bilingual')).toBe('Dvojjazyčný popis');
    expect(description('en', 'Bilingual')).toBe('A bilingual description');
  });

  it('step 2 — a plain string answers every language (there is nothing to select)', () => {
    expect(description('cs', 'Plain')).toBe('a plain description');
    expect(description('en', 'Plain')).toBe('a plain description');
    expect(description('de', 'Plain')).toBe('a plain description');
  });

  it('step 3 — a map without the requested language falls back to `en`', () => {
    expect(description('cs', 'EnglishOnly')).toBe('English only');
  });

  it('step 4 — neither the requested language nor `en`: first entry by language code', () => {
    // {de, fr} sorted by code → `de`. Deterministic on purpose: the alternative
    // (authoring order) makes the drawer's text depend on how the block was typed.
    expect(description('cs', 'Neither')).toBe('Nur Deutsch');
  });

  it('step 5 — nothing authored, and an empty map, both stay null', () => {
    expect(description('cs', 'Undescribed')).toBeNull();
    expect(description('cs', 'EmptyMap')).toBeNull();
  });

  it('an authored-but-empty entry WINS its language — presence, not truthiness', () => {
    // The map steps test presence and the plain step tests non-emptiness, exactly as
    // Veles' `selectDescription` does (there `description` is a String that is "" when
    // unauthored). Falling through to `en` here instead would make the same model read
    // differently in the editor and on the wire, for the one input that makes the
    // difference visible.
    expect(description('cs', 'EmptyEntry')).toBe('');
    expect(description('en', 'EmptyEntry')).toBe('Fallback');
  });
});

// A table has no `displayLabel`, so `getDisplayLabel` uses its description — which means
// a bilingual table fell through to its bare name before the fix.
const DB = `
model db schema dbo

def table T_BILINGUAL {
  description: { en: "Sales rows", cs: "Řádky prodejů" }
  columns: [ def column id { type: int } ]
}
def table T_PLAIN {
  description: "plain table"
  columns: [ def column id { type: int } ]
}
`;

function label(lang: string, name: string): string | undefined {
  return harness(lang, DB, 'file:///db.ttrm')(`db.dbo.table.${name}`)?.label;
}

describe('table display label from a localized description (NLS-P10)', () => {
  it('uses the requested language rather than falling through to the bare name', () => {
    expect(label('cs', 'T_BILINGUAL')).toBe('Řádky prodejů');
    expect(label('en', 'T_BILINGUAL')).toBe('Sales rows');
  });

  it('REGRESSION PIN — the plain form is unchanged', () => {
    expect(label('cs', 'T_PLAIN')).toBe('plain table');
    expect(label('en', 'T_PLAIN')).toBe('plain table');
  });
});

// C-2 binding map — a query's `predicate` IS its description, same two forms.
const QUERIES = `
model query

def query top_sellers {
  description: { en: "best selling products", cs: "nejprodávanější produkty" }
  language: SQL
}
def query plain_q {
  description: "a plain predicate"
  language: SQL
}
`;

describe('binding-map query predicate honours the localized form (NLS-P10)', () => {
  const docs = () => new Map([['file:///q.ttrm', QUERIES]]);
  const predicate = (lang: string | undefined, name: string) =>
    (lang === undefined ? buildBindingMap(docs()) : buildBindingMap(docs(), lang)).queries.find(
      (q) => q.qname.endsWith(name),
    )?.predicate;

  it('selects the requested language', () => {
    expect(predicate('cs', 'top_sellers')).toBe('nejprodávanější produkty');
  });

  it('defaults to `en` and leaves the plain form alone', () => {
    expect(predicate(undefined, 'top_sellers')).toBe('best selling products');
    expect(predicate(undefined, 'plain_q')).toBe('a plain predicate');
  });
});
