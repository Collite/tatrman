import { describe, it, expect } from 'vitest';
import { parseString, DiagnosticCode } from '@tatrman/parser';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';
import { analyzeSemantics } from '../index.js';

function codesFor(src: string): DiagnosticCode[] {
  const r = parseString(src, 'x.ttrm');
  return analyzeSemantics(r.ast!).diagnostics.map((d) => d.code);
}
function diagsFor(src: string) {
  const r = parseString(src, 'x.ttrm');
  return analyzeSemantics(r.ast!).diagnostics;
}

const ent = (body: string) => `model er\ndef entity E {\n${body}\n}`;

describe('semantics-block validation (TTR-SEM-2xx)', () => {
  it('200 — unknown key (with nearest-match suggestion)', () => {
    const d = diagsFor(ent('attributes: [ def attribute a { type: { type: varchar, length: 6 }, semantics { role: period_code, code_forma: "x" } } ]'));
    const hit = d.find((x) => x.code === DiagnosticCode.SemUnknownKey);
    expect(hit).toBeDefined();
    expect(hit?.suggestion).toBe('code_format');
  });

  it('201 — unknown role (suggests event_date for event_dat)', () => {
    const d = diagsFor(ent('attributes: [ def attribute a { type: date, semantics { role: event_dat } } ]'));
    const hit = d.find((x) => x.code === DiagnosticCode.SemUnknownRole);
    expect(hit).toBeDefined();
    expect(hit?.suggestion).toBe('event_date');
    expect(hit?.message).toContain('event_date');
  });

  it('202 — unknown kind (suggests period_table)', () => {
    const d = diagsFor(ent('semantics { kind: periodtable }'));
    const hit = d.find((x) => x.code === DiagnosticCode.SemUnknownKind);
    expect(hit).toBeDefined();
    expect(hit?.suggestion).toBe('period_table');
  });

  it('203 — duplicate key', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: date, semantics { role: event_date, role: due_date } } ]')))
      .toContain(DiagnosticCode.SemDuplicateKey);
  });

  it('204 — kind on an attribute, and role on an entity', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: date, semantics { kind: poi } } ]')))
      .toContain(DiagnosticCode.SemMisplacedKeyword);
    expect(codesFor(ent('semantics { role: event_date }'))).toContain(DiagnosticCode.SemMisplacedKeyword);
  });

  it('205 — type-constraint violation (amount on a text column)', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: { type: varchar, length: 3 }, semantics { role: amount, currency: a } } ]')))
      .toContain(DiagnosticCode.SemTypeConstraint);
  });

  it('206 — completeness (period_table missing period_end)', () => {
    const src = ent([
      'semantics { kind: period_table },',
      'attributes: [',
      '  def attribute s { type: date, semantics { role: period_start } },',
      '  def attribute c { type: { type: varchar, length: 6 }, semantics { role: period_code } }',
      ']',
    ].join('\n'));
    expect(codesFor(src)).toContain(DiagnosticCode.SemCompleteness);
  });

  it('207 — more than one event_date on an entity', () => {
    const src = ent([
      'attributes: [',
      '  def attribute a { type: date, semantics { role: event_date } },',
      '  def attribute b { type: date, semantics { role: event_date } }',
      ']',
    ].join('\n'));
    expect(codesFor(src)).toContain(DiagnosticCode.SemMultipleEventDate);
  });

  it('208 — period: to a nonexistent entity, and to a non-period_table entity', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: date, semantics { role: event_date, period: Nope } } ]')))
      .toContain(DiagnosticCode.SemBadPeriodRef);
    const miskinded = [
      'model er',
      'def entity P { semantics { kind: poi }, attributes: [ def attribute x { type: decimal, semantics { role: geo_lat } }, def attribute y { type: decimal, semantics { role: geo_lon } } ] }',
      'def entity E { attributes: [ def attribute a { type: date, semantics { role: event_date, period: P } } ] }',
    ].join('\n');
    expect(codesFor(miskinded)).toContain(DiagnosticCode.SemBadPeriodRef);
  });

  it('209 — currency: to a missing sibling, and to a non-currency_code sibling', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: decimal, semantics { role: amount, currency: nope } } ]')))
      .toContain(DiagnosticCode.SemBadCurrencyRef);
    const roleless = ent([
      'attributes: [',
      '  def attribute a { type: decimal, semantics { role: amount, currency: c } },',
      '  def attribute c { type: date, semantics { role: event_date } }',
      ']',
    ].join('\n'));
    expect(codesFor(roleless)).toContain(DiagnosticCode.SemBadCurrencyRef);
  });

  it('210 — geo_lat without geo_lon, and geo_point + pair', () => {
    expect(codesFor(ent('semantics { kind: poi }, attributes: [ def attribute a { type: decimal, semantics { role: geo_lat } } ]')))
      .toContain(DiagnosticCode.SemGeoPair);
    const both = ent([
      'semantics { kind: poi },',
      'attributes: [',
      '  def attribute p { type: text, semantics { role: geo_point } },',
      '  def attribute a { type: decimal, semantics { role: geo_lat } },',
      '  def attribute o { type: decimal, semantics { role: geo_lon } }',
      ']',
    ].join('\n'));
    expect(codesFor(both)).toContain(DiagnosticCode.SemGeoPair);
  });

  it('211 — valid_from without valid_to', () => {
    expect(codesFor(ent('attributes: [ def attribute a { type: date, semantics { role: valid_from } } ]')))
      .toContain(DiagnosticCode.SemValidPair);
  });

  it('green path — the golden 59-semantics.ttrm fixture yields zero diagnostics', () => {
    const here = dirname(fileURLToPath(import.meta.url));
    const src = readFileSync(resolve(here, '../../../../tests/conformance/fixtures/59-semantics.ttrm'), 'utf-8');
    const diags = diagsFor(src);
    expect(diags).toEqual([]);
  });

  it('green path — the golden 60-semantics-db.ttrm fixture yields zero diagnostics', () => {
    const here = dirname(fileURLToPath(import.meta.url));
    const src = readFileSync(resolve(here, '../../../../tests/conformance/fixtures/60-semantics-db.ttrm'), 'utf-8');
    expect(diagsFor(src)).toEqual([]);
  });
});
