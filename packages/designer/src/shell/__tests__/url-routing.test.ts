// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { formatPath, parsePath } from '../url.js';
import type { UrlState } from '../types.js';

describe('url routing — round-trip parse(format(state)) = state', () => {
  const cases: Array<[string, UrlState]> = [
    ['none (no workspace)', { kind: 'none' }],
    ['none (workspace only)', { kind: 'none', workspace: 'ws' }],
    ['subject (no skin)', { kind: 'subject', workspace: 'ws', subjectRef: 'er.sales' }],
    [
      'subject (with skin)',
      { kind: 'subject', workspace: 'ws', subjectRef: 'er.sales', skin: 'md.er-dialect' },
    ],
    [
      'perspective lineage',
      {
        kind: 'perspective',
        workspace: 'ws',
        perspective: 'lineage',
        params: { root: 'md.Sales.net_amount', scope: 'neighborhood', dir: 'upstream' },
      },
    ],
    [
      'perspective binding',
      {
        kind: 'perspective',
        workspace: 'ws',
        perspective: 'binding',
        params: { left: 'er.sales', sel: 'Customer' },
      },
    ],
  ];

  for (const [name, state] of cases) {
    it(`round-trips ${name}`, () => {
      expect(parsePath(formatPath(state))).toEqual(state);
    });
  }
});

describe('url routing — encoding', () => {
  it('preserves dots/slashes/colons in a graph-uri subjectRef', () => {
    const state: UrlState = {
      kind: 'subject',
      workspace: 'ws',
      subjectRef: 'file:///proj/graphs/all_er.ttrg',
    };
    const path = formatPath(state);
    // encoded slashes stay a single path segment
    expect(path).not.toContain('file:///proj');
    expect(parsePath(path)).toEqual(state);
  });

  it('preserves special chars in perspective param values', () => {
    const state: UrlState = {
      kind: 'perspective',
      workspace: 'ws',
      perspective: 'lineage',
      params: { root: 'file:///proj/graphs/all_er.ttrg', dir: 'upstream' },
    };
    expect(parsePath(formatPath(state))).toEqual(state);
  });
});

describe('url routing — skin truth surface', () => {
  it('omits skin= when no skin is chosen', () => {
    const path = formatPath({ kind: 'subject', workspace: 'ws', subjectRef: 'er.sales' });
    expect(path).not.toContain('skin=');
  });

  it('includes skin= when a non-default skin is chosen', () => {
    const path = formatPath({
      kind: 'subject',
      workspace: 'ws',
      subjectRef: 'er.sales',
      skin: 'md.er-dialect',
    });
    expect(path).toContain('skin=');
  });

  it('parses a no-skin URL with the skin key absent', () => {
    const state = parsePath('/w/ws/s/er.sales');
    expect(state).toEqual({ kind: 'subject', workspace: 'ws', subjectRef: 'er.sales' });
    expect('skin' in state).toBe(false);
  });
});

describe('url routing — DS-SHELL-001 unknown subject classification', () => {
  it('classifies an unknown ref as unknownSubject', () => {
    expect(parsePath('/w/ws/s/er.bogus', new Set(['er.sales']))).toEqual({
      kind: 'unknownSubject',
      workspace: 'ws',
      subjectRef: 'er.bogus',
    });
  });

  it('leaves a known ref as a normal subject', () => {
    expect(parsePath('/w/ws/s/er.sales', new Set(['er.sales']))).toEqual({
      kind: 'subject',
      workspace: 'ws',
      subjectRef: 'er.sales',
    });
  });

  it('never classifies when no knownSubjectRefs is passed', () => {
    expect(parsePath('/w/ws/s/er.bogus')).toEqual({
      kind: 'subject',
      workspace: 'ws',
      subjectRef: 'er.bogus',
    });
  });

  it('formats unknownSubject exactly like subject (same path)', () => {
    const unknown: UrlState = { kind: 'unknownSubject', workspace: 'ws', subjectRef: 'er.bogus' };
    const subject: UrlState = { kind: 'subject', workspace: 'ws', subjectRef: 'er.bogus' };
    expect(formatPath(unknown)).toBe(formatPath(subject));
  });
});

describe('url routing — robustness', () => {
  it('parses "/" as none', () => {
    expect(parsePath('/')).toEqual({ kind: 'none' });
  });

  it('parses garbage as none without throwing', () => {
    expect(parsePath('/garbage')).toEqual({ kind: 'none' });
    expect(() => parsePath('/w/ws/x/y/z/nonsense')).not.toThrow();
  });
});
