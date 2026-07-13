// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { desugarLexicon } from '../lexicon/desugar.js';
import { serializeVocabularySnapshot } from '../lexicon/snapshot.js';

// RG-P4.S2.T4 — RS-9 T3 canonical-only propagation. A model mixing inline sugar
// and legacy `search{}`/`aliases` forms serialises into the vocabulary snapshot
// as CANONICAL entries only (one shape) — the snapshot aligns to RG-P2's
// `SnapshotVocabularySource` seam (category == targetRef, values {id, value}).

function snapshotOf(src: string) {
  return serializeVocabularySnapshot(desugarLexicon(parseString(src, 'file:///m.ttrm').ast!));
}

describe('lexicon snapshot — canonical-only propagation (RS-9 T3)', () => {
  it('the hero: inline `lexicon { terms }` on a measure rides the snapshot for its target', () => {
    const snap = snapshotOf('model md\ndef measure net { domain: md.Money, lexicon { terms: ["tržba", "obrat", "utržit"] } }');
    const entry = snap.entries.find((e) => e.targetRef === 'md.measure.net')!;
    expect(entry.category).toBe('md.measure.net'); // RG-P2 stub: category == targetRef
    expect(entry.values.map((v) => v.value)).toEqual(['tržba', 'obrat', 'utržit']);
    // id = ascii-fold of the value (diacritic-insensitive), per the stub scheme.
    expect(entry.values.map((v) => v.id)).toEqual(['trzba', 'obrat', 'utrzit']);
  });

  it('inline + legacy vocabulary on one carrier merge into ONE canonical shape (no legacy leak)', () => {
    const snap = snapshotOf('model er\ndef entity customer { aliases: ["zákazník"], search { aliases: ["odběratel"] }, lexicon { terms: ["klient"] } }');
    const entry = snap.entries.find((e) => e.targetRef === 'er.entity.customer')!;
    const values = entry.values.map((v) => v.value).sort();
    expect(values).toEqual(['klient', 'odběratel', 'zákazník']);
    // No shape other than {id, value} rides — the consumer never sees search/aliases.
    for (const v of entry.values) expect(Object.keys(v).sort()).toEqual(['id', 'value']);
  });

  it('per-locale units are preserved as distinct snapshot entries', () => {
    const snap = snapshotOf('model er\ndef entity customer { search { keywords: { cs: ["tržba"], en: ["revenue"] } } }');
    const cs = snap.entries.find((e) => e.targetRef === 'er.entity.customer' && e.locale === 'cs')!;
    const en = snap.entries.find((e) => e.targetRef === 'er.entity.customer' && e.locale === 'en')!;
    expect(cs.values.map((v) => v.value)).toEqual(['tržba']);
    expect(en.values.map((v) => v.value)).toEqual(['revenue']);
  });

  it('patterns ride the separate `patterns` list, not `values`', () => {
    const snap = snapshotOf('model lexicon\ndef pattern nazev { for: db.query.by_name, match: "název .*" }');
    const entry = snap.entries.find((e) => e.targetRef === 'db.query.by_name')!;
    expect(entry.patterns).toEqual(['název .*']);
    expect(entry.values).toEqual([]);
  });
});

describe('lexicon snapshot — valueLabels A4-β member vocabulary rides beside terms', () => {
  it('a coded attribute valueLabels label + aliases become declared vocabulary for the attribute', () => {
    const snap = snapshotOf(
      'model er schema entity\ndef entity account { attributes: [def attribute status { type: int, valueLabels { "1": { label: { cs: "Aktivní" }, aliases: ["živý"] } } }] }',
    );
    const cs = snap.entries.find((e) => e.targetRef === 'er.entity.account.status' && e.locale === 'cs')!;
    const base = snap.entries.find((e) => e.targetRef === 'er.entity.account.status' && e.locale === null)!;
    expect(cs.values.map((v) => v.value)).toEqual(['Aktivní']);
    expect(base.values.map((v) => v.value)).toEqual(['živý']);
  });
});
