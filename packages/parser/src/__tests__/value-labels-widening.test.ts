// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import { DiagnosticCode } from '../diagnostics.js';
import type { AttributeDef } from '../ast.js';

// RG-P4.S2 (A4-β, RS-12) — valueLabels per-value alias widening. The legacy
// `{ cs: "…", en: "…" }` form and the widened `{ label: {…}, aliases: [ … ] }`
// form both parse; the walker captures `aliases` and preserves the localized
// label unchanged for the legacy form.

function attrOf(src: string): AttributeDef {
  const { ast, errors } = parseString(src, 'file:///m.ttrm');
  expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
  return ast!.definitions[0] as AttributeDef;
}

describe('valueLabels A4-β widening', () => {
  it('the legacy `{ cs, en }` form still parses to a localized label, no aliases', () => {
    const a = attrOf('model er schema entity\ndef attribute status { type: int, valueLabels { "1": { cs: "Aktivní", en: "Active" } } }');
    const e = a.valueLabels!.entries[0];
    expect(e.key).toBe('1');
    expect(e.label.entries).toEqual({ cs: 'Aktivní', en: 'Active' });
    expect(e.aliases).toBeUndefined();
  });

  it('the widened `{ label: {…}, aliases: [ … ] }` form captures label + aliases', () => {
    const a = attrOf('model er schema entity\ndef attribute status { type: int, valueLabels { "1": { label: { cs: "Aktivní", en: "Active" }, aliases: ["živý", "aktivni"] } } }');
    const e = a.valueLabels!.entries[0];
    expect(e.label.entries).toEqual({ cs: 'Aktivní', en: 'Active' });
    expect(e.aliases).toEqual(['živý', 'aktivni']);
  });

  it('mixed entries — one legacy, one widened — both parse in one block', () => {
    const a = attrOf('model er schema entity\ndef attribute status { type: int, valueLabels { "1": { label: { cs: "Aktivní" }, aliases: ["živý"] }, "2": { cs: "Neaktivní" } } }');
    expect(a.valueLabels!.entries).toHaveLength(2);
    expect(a.valueLabels!.entries[0].aliases).toEqual(['živý']);
    expect(a.valueLabels!.entries[1].aliases).toBeUndefined();
    expect(a.valueLabels!.entries[1].label.entries).toEqual({ cs: 'Neaktivní' });
  });
});
