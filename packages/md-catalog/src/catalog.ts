// SPDX-License-Identifier: Apache-2.0
import type { CatalogEntry } from './types.js';

/**
 * The v1 Time catalog — the **pinned floor** decided 2026-06-25 against real
 * `ai-models` usage (map-catalog.md §2.5). Exactly 11 entries:
 *
 *   truncation: truncToDay, truncToMonth, truncToQuarter, truncToYear, truncToWeek
 *   extraction: monthOfDate, quarterOfDate, yearOfDate, dayOfMonth, weekOfYear
 *   rollup:     quarterOfMonth
 *
 * Deliberately **not** seeded: the fiscal family (real fiscal/accounting periods
 * are `kind: bound` dimensions + `md2db_domain`, not calc — §2.5) and the
 * deferred sub-day / `dayOfWeek` / `halfOfQuarter` / `monthOfWeek` / `quarterOfWeek`
 * tail. Adding one later is an additive minor bump.
 *
 * Week-family params default to the ISO-8601 / EU convention (Monday-start, ISO
 * week): `truncToWeek(weekStart = mon)`, `weekOfYear(scheme = iso)`.
 */
const ENTRIES: CatalogEntry[] = [
  // ---- Truncation: instant|date → date (start of the containing period) ----
  {
    name: 'truncToDay',
    category: 'truncation',
    params: [],
    input: 'instant|date',
    output: 'date',
    cardinality: 'N:1',
    semantics: 'midnight of the same calendar day',
  },
  {
    name: 'truncToWeek',
    category: 'truncation',
    params: [{ name: 'weekStart', type: 'enum', values: ['mon', 'sun'], default: 'mon' }],
    input: 'instant|date',
    output: 'date',
    cardinality: 'N:1',
    semantics: 'first day of the containing week',
  },
  {
    name: 'truncToMonth',
    category: 'truncation',
    params: [],
    input: 'instant|date',
    output: 'date',
    cardinality: 'N:1',
    semantics: 'first day of the containing month',
  },
  {
    name: 'truncToQuarter',
    category: 'truncation',
    params: [],
    input: 'instant|date',
    output: 'date',
    cardinality: 'N:1',
    semantics: 'first day of the containing quarter',
  },
  {
    name: 'truncToYear',
    category: 'truncation',
    params: [],
    input: 'instant|date',
    output: 'date',
    cardinality: 'N:1',
    semantics: 'first day of the containing year',
  },

  // ---- Extraction: instant|date → int{lo..hi} (calendar component) ----
  {
    name: 'dayOfMonth',
    category: 'extraction',
    params: [],
    input: 'instant|date',
    output: { kind: 'int', lo: 1, hi: 31 },
    cardinality: 'N:1',
    semantics: 'day-of-month',
  },
  {
    name: 'weekOfYear',
    category: 'extraction',
    params: [{ name: 'scheme', type: 'enum', values: ['iso', 'us'], default: 'iso' }],
    input: 'instant|date',
    output: { kind: 'int', lo: 1, hi: 53 },
    cardinality: 'N:1',
    semantics: 'week number',
  },
  {
    name: 'monthOfDate',
    category: 'extraction',
    params: [],
    input: 'instant|date',
    output: { kind: 'int', lo: 1, hi: 12 },
    cardinality: 'N:1',
    semantics: 'month number',
  },
  {
    name: 'quarterOfDate',
    category: 'extraction',
    params: [],
    input: 'instant|date',
    output: { kind: 'int', lo: 1, hi: 4 },
    cardinality: 'N:1',
    semantics: 'quarter number',
  },
  {
    name: 'yearOfDate',
    category: 'extraction',
    params: [],
    input: 'instant|date',
    output: { kind: 'int' },
    cardinality: 'N:1',
    semantics: 'calendar year',
  },

  // ---- Roll-up: int{lo..hi} → int{lo'..hi'} (one calendar level up) ----
  {
    name: 'quarterOfMonth',
    category: 'rollup',
    params: [],
    input: { kind: 'int', lo: 1, hi: 12 },
    output: { kind: 'int', lo: 1, hi: 4 },
    cardinality: 'N:1',
    semantics: '⌈m/3⌉',
  },
];

/** The built-in calc-map catalog, keyed by `entry.name`. Read-only. */
export const MD_CALC_CATALOG: ReadonlyMap<string, CatalogEntry> = new Map(
  ENTRIES.map((e) => [e.name, e])
);
