// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  MD_CALC_CATALOG,
  MD_CATALOG_VERSION,
  type CatalogEntry,
  type IntShape,
} from '../index.js';

// The v1 pinned floor (map-catalog.md §2.5): exactly these 11 entries.
const FLOOR = [
  'truncToDay',
  'truncToMonth',
  'truncToQuarter',
  'truncToYear',
  'truncToWeek',
  'monthOfDate',
  'quarterOfDate',
  'yearOfDate',
  'dayOfMonth',
  'weekOfYear',
  'quarterOfMonth',
] as const;

const CATEGORIES = new Set(['truncation', 'extraction', 'rollup', 'fiscal']);

function isIntShape(s: CatalogEntry['input']): s is IntShape {
  return typeof s === 'object' && s.kind === 'int';
}
function isShapeValid(s: CatalogEntry['input']): boolean {
  return s === 'instant' || s === 'date' || s === 'instant|date' || isIntShape(s);
}

describe('@tatrman/md-catalog — shape & invariants', () => {
  it('seeds exactly the pinned v1 floor (count + names)', () => {
    expect(MD_CALC_CATALOG.size).toBe(FLOOR.length);
    for (const name of FLOOR) {
      expect(MD_CALC_CATALOG.has(name), `missing entry ${name}`).toBe(true);
    }
  });

  it('does NOT seed the deferred fiscal / sub-day tail', () => {
    for (const deferred of [
      'fiscalYearOfDate',
      'fiscalQuarterOfDate',
      'truncToSecond',
      'hourOfDay',
      'dayOfWeek',
      'halfOfQuarter',
      'monthOfWeek',
    ]) {
      expect(MD_CALC_CATALOG.has(deferred), `unexpected entry ${deferred}`).toBe(false);
    }
  });

  it('every entry has a unique name equal to its map key', () => {
    const names = new Set<string>();
    for (const [key, entry] of MD_CALC_CATALOG) {
      expect(entry.name).toBe(key);
      expect(names.has(entry.name)).toBe(false);
      names.add(entry.name);
    }
    expect(names.size).toBe(MD_CALC_CATALOG.size);
  });

  it('every entry has a valid category, input/output shape, and N:1 cardinality', () => {
    for (const entry of MD_CALC_CATALOG.values()) {
      expect(CATEGORIES.has(entry.category), `${entry.name} category`).toBe(true);
      expect(isShapeValid(entry.input), `${entry.name} input`).toBe(true);
      expect(isShapeValid(entry.output), `${entry.name} output`).toBe(true);
      expect(entry.cardinality).toBe('N:1');
      expect(typeof entry.semantics).toBe('string');
      expect(entry.semantics.length).toBeGreaterThan(0);
    }
  });

  it('every declared param has a name, a type, and (enum) values', () => {
    for (const entry of MD_CALC_CATALOG.values()) {
      for (const p of entry.params) {
        expect(p.name.length).toBeGreaterThan(0);
        const okType = p.type === 'enum' || (typeof p.type === 'object' && p.type.kind === 'int');
        expect(okType, `${entry.name}.${p.name} type`).toBe(true);
        if (p.type === 'enum') {
          expect(Array.isArray(p.values) && p.values.length > 0).toBe(true);
          // A defaulted enum param must default to one of its values.
          if (p.default !== undefined) expect(p.values).toContain(p.default);
        }
      }
    }
  });

  it('the two week-family params default to the ISO/EU convention', () => {
    const weekStart = MD_CALC_CATALOG.get('truncToWeek')!.params.find((p) => p.name === 'weekStart');
    expect(weekStart?.default).toBe('mon');
    const scheme = MD_CALC_CATALOG.get('weekOfYear')!.params.find((p) => p.name === 'scheme');
    expect(scheme?.default).toBe('iso');
  });

  it('MD_CATALOG_VERSION is a semver string', () => {
    expect(MD_CATALOG_VERSION).toMatch(/^\d+\.\d+\.\d+$/);
  });

  it('spot-check truncToDay / monthOfDate / quarterOfMonth shapes (map-catalog §2)', () => {
    const day = MD_CALC_CATALOG.get('truncToDay')!;
    expect(day.category).toBe('truncation');
    expect(day.input).toBe('instant|date');
    expect(day.output).toBe('date');
    expect(day.params).toEqual([]);

    const month = MD_CALC_CATALOG.get('monthOfDate')!;
    expect(month.category).toBe('extraction');
    expect(month.input).toBe('instant|date');
    expect(month.output).toEqual({ kind: 'int', lo: 1, hi: 12 });

    const q = MD_CALC_CATALOG.get('quarterOfMonth')!;
    expect(q.category).toBe('rollup');
    expect(q.input).toEqual({ kind: 'int', lo: 1, hi: 12 });
    expect(q.output).toEqual({ kind: 'int', lo: 1, hi: 4 });
  });
});
