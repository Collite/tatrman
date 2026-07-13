// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { DiagnosticCode } from '@tatrman/parser';
import { desugarLexicon } from '../lexicon/desugar.js';

// RG-P4.S2.T1 — the RS-32 legacy-migration table. Legacy vocabulary forms are a
// SECOND sugar source (beside inline `lexicon{}`) that desugars into the SAME
// canonical entries the S1 pipeline produces (one pipeline, two sources), each
// emitting a named deprecation warning. Consumers never see legacy shapes.

function entriesFor(src: string) {
  return desugarLexicon(parseString(src, 'file:///m.ttrm').ast!);
}

describe('RS-32 migration — entity aliases → term (legacy)', () => {
  it('entity `aliases: [...]` desugars to one legacy term entry targeting the entity', () => {
    const a = entriesFor('model er\ndef entity customer { aliases: ["zákazník", "odběratel"] }');
    const terms = a.entries.filter((e) => e.entryKind === 'term');
    expect(terms).toHaveLength(1);
    expect(terms[0].origin).toBe('legacy');
    expect(terms[0].target).toBe('er.entity.customer');
    expect(terms[0].forms).toEqual(['zákazník', 'odběratel']);
  });

  it('fires a named LexiconLegacyAliases deprecation warning', () => {
    const a = entriesFor('model er\ndef entity customer { aliases: ["x"] }');
    const d = a.diagnostics.filter((x) => x.code === DiagnosticCode.LexiconLegacyAliases);
    expect(d).toHaveLength(1);
    expect(d[0].severity).toBe('warning');
  });

  it('an entity with NO aliases produces no legacy entries or deprecations', () => {
    const a = entriesFor('model er\ndef entity customer { labelPlural: "Customers" }');
    expect(a.entries.filter((e) => e.origin === 'legacy')).toEqual([]);
    expect(a.diagnostics.filter((x) => String(x.code).startsWith('ttr/lexicon-legacy'))).toEqual([]);
  });
});

describe('RS-32 migration — search{} sub-properties', () => {
  it('`search { aliases }` → term entries + LexiconLegacyAliases', () => {
    const a = entriesFor('model er\ndef entity customer { search { aliases: ["odběratel"] } }');
    const t = a.entries.filter((e) => e.entryKind === 'term');
    expect(t).toHaveLength(1);
    expect(t[0].forms).toEqual(['odběratel']);
    expect(t[0].origin).toBe('legacy');
    expect(a.diagnostics.some((x) => x.code === DiagnosticCode.LexiconLegacyAliases)).toBe(true);
  });

  it('`search { keywords: { cs, en } }` → one term entry per locale + LexiconLegacyKeywords', () => {
    const a = entriesFor('model er\ndef entity customer { search { keywords: { cs: ["tržba"], en: ["revenue"] } } }');
    const t = a.entries.filter((e) => e.entryKind === 'term');
    const byLocale = Object.fromEntries(t.map((e) => [e.locale, e.forms]));
    expect(byLocale['cs']).toEqual(['tržba']);
    expect(byLocale['en']).toEqual(['revenue']);
    expect(a.diagnostics.some((x) => x.code === DiagnosticCode.LexiconLegacyKeywords)).toBe(true);
  });

  it('`search { patterns }` → pattern entries + LexiconLegacyPatterns', () => {
    const a = entriesFor('model er\ndef entity customer { search { patterns: ["název .*", "kód .*"] } }');
    const p = a.entries.filter((e) => e.entryKind === 'pattern');
    expect(p.map((e) => e.match)).toEqual(['název .*', 'kód .*']);
    expect(p.every((e) => e.origin === 'legacy')).toBe(true);
    expect(a.diagnostics.some((x) => x.code === DiagnosticCode.LexiconLegacyPatterns)).toBe(true);
  });

  it('`search { examples }` → example entries + LexiconLegacyExamples', () => {
    const a = entriesFor('model er\ndef entity customer { search { examples: ["Kolik zákazníků?"] } }');
    const e = a.entries.filter((x) => x.entryKind === 'example');
    expect(e.map((x) => x.text)).toEqual(['Kolik zákazníků?']);
    expect(a.diagnostics.some((x) => x.code === DiagnosticCode.LexiconLegacyExamples)).toBe(true);
  });

  it('`searchable`/`fuzzy` are retrieval config — NOT migrated, no deprecation', () => {
    const a = entriesFor('model er\ndef entity customer { search { searchable: true, fuzzy: true } }');
    expect(a.entries.filter((e) => e.origin === 'legacy')).toEqual([]);
    expect(a.diagnostics.filter((x) => String(x.code).startsWith('ttr/lexicon-legacy'))).toEqual([]);
  });
});

describe('RS-32 migration — one pipeline, two sources (equivalence)', () => {
  it('legacy aliases and a hand-written canonical term produce the same vocabulary for the target', () => {
    const legacy = entriesFor('model er\ndef entity customer { aliases: ["zákazník", "odběratel"] }');
    const canonical = entriesFor('model lexicon\ndef term t { for: er.entity.customer, forms: ["zákazník", "odběratel"] }');
    const lv = legacy.byTarget.get('er.entity.customer')!.flatMap((e) => e.forms ?? []).sort();
    const cv = canonical.byTarget.get('er.entity.customer')!.flatMap((e) => e.forms ?? []).sort();
    expect(lv).toEqual(cv);
  });
});

describe('RS-32 T3 — search.descriptions folds into description (no vocab entry)', () => {
  it('`search { descriptions }` fires LexiconLegacyDescriptions and produces NO entry', () => {
    const a = entriesFor('model er\ndef entity customer { search { descriptions: { cs: ["Zákazník firmy"] } } }');
    expect(a.diagnostics.some((x) => x.code === DiagnosticCode.LexiconLegacyDescriptions)).toBe(true);
    expect(a.entries.filter((e) => e.origin === 'legacy')).toEqual([]);
  });
});
