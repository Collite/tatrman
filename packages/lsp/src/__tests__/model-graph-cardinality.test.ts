// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseCardinality, extractCardinality } from '../model-graph';

describe('parseCardinality', () => {
  it("'1' → 'one'", () => {
    expect(parseCardinality('1')).toBe('one');
  });

  it("'0..1' → 'zero-or-one'", () => {
    expect(parseCardinality('0..1')).toBe('zero-or-one');
  });

  it("'0..*' → 'many'", () => {
    expect(parseCardinality('0..*')).toBe('many');
  });

  it("'n' → 'many'", () => {
    expect(parseCardinality('n')).toBe('many');
  });

  it("'*' → 'many'", () => {
    expect(parseCardinality('*')).toBe('many');
  });

  it("'1..n' → 'one-or-many'", () => {
    expect(parseCardinality('1..n')).toBe('one-or-many');
  });

  it("'1..*' → 'one-or-many'", () => {
    expect(parseCardinality('1..*')).toBe('one-or-many');
  });

  it("'foo' → null", () => {
    expect(parseCardinality('foo')).toBe(null);
  });

  it("'' → null", () => {
    expect(parseCardinality('')).toBe(null);
  });
});

describe('extractCardinality', () => {
  it('returns {from:null,to:null} for undefined', () => {
    expect(extractCardinality(undefined)).toEqual({ from: null, to: null });
  });

  it('returns parsed from/to for an ObjectValue with valid string entries', () => {
    const obj = {
      kind: 'object' as const,
      entries: [
        { key: 'from', value: { kind: 'string' as const, value: '1' }, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 } },
        { key: 'to', value: { kind: 'string' as const, value: '*' }, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 } },
      ],
      source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 },
    };
    expect(extractCardinality(obj)).toEqual({ from: 'one', to: 'many' });
  });

  it('returns null for non-string entries (list values)', () => {
    const obj = {
      kind: 'object' as const,
      entries: [
        { key: 'from', value: { kind: 'string' as const, value: '1' }, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 } },
        { key: 'to', value: { kind: 'list' as const, items: [] }, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 } },
      ],
      source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 },
    };
    expect(extractCardinality(obj)).toEqual({ from: 'one', to: null });
  });

  it('returns null for missing entries', () => {
    const obj = {
      kind: 'object' as const,
      entries: [
        { key: 'from', value: { kind: 'string' as const, value: '0..1' }, source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 } },
      ],
      source: { file: '', line: 1, column: 0, endLine: 1, endColumn: 1, offsetStart: 0, offsetEnd: 1 },
    };
    expect(extractCardinality(obj)).toEqual({ from: 'zero-or-one', to: null });
  });
});