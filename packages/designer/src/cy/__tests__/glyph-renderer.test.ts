// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { glyphFor } from '../glyph-renderer';

function parseSvg(s: string): Element | null {
  const parser = new DOMParser();
  const doc = parser.parseFromString(`<svg xmlns="http://www.w3.org/2000/svg">${s}</svg>`, 'image/svg+xml');
  const err = doc.querySelector('parsererror');
  if (err) return null;
  return doc.documentElement.firstElementChild;
}

describe('glyphFor', () => {
  it("'one' → <g class='glyph-one'> with exactly one <line>", () => {
    const el = parseSvg(glyphFor('one'));
    expect(el).not.toBeNull();
    expect(el!.getAttribute('class')).toBe('glyph-one');
    expect(el!.querySelectorAll('line').length).toBe(1);
    expect(el!.querySelectorAll('circle').length).toBe(0);
  });

  it("'zero-or-one' → <g class='glyph-zero-or-one'> with one <circle> and one <line>", () => {
    const el = parseSvg(glyphFor('zero-or-one'));
    expect(el).not.toBeNull();
    expect(el!.getAttribute('class')).toBe('glyph-zero-or-one');
    expect(el!.querySelectorAll('line').length).toBe(1);
    expect(el!.querySelectorAll('circle').length).toBe(1);
  });

  it("'many' → <g class='glyph-many'> containing three <line> elements", () => {
    const el = parseSvg(glyphFor('many'));
    expect(el).not.toBeNull();
    expect(el!.getAttribute('class')).toBe('glyph-many');
    expect(el!.querySelectorAll('line').length).toBe(3);
    expect(el!.querySelectorAll('circle').length).toBe(0);
  });

  it("'one-or-many' → <g class='glyph-one-or-many'> with one perpendicular <line> plus three crow's-foot <line>s", () => {
    const el = parseSvg(glyphFor('one-or-many'));
    expect(el).not.toBeNull();
    expect(el!.getAttribute('class')).toBe('glyph-one-or-many');
    expect(el!.querySelectorAll('line').length).toBe(4);
    expect(el!.querySelectorAll('circle').length).toBe(0);
  });

  it('glyphFor(null) returns empty string', () => {
    expect(glyphFor(null)).toBe('');
  });

  it('matches snapshot for one', () => {
    expect(glyphFor('one')).toMatchSnapshot();
  });

  it('matches snapshot for zero-or-one', () => {
    expect(glyphFor('zero-or-one')).toMatchSnapshot();
  });

  it('matches snapshot for many', () => {
    expect(glyphFor('many')).toMatchSnapshot();
  });

  it('matches snapshot for one-or-many', () => {
    expect(glyphFor('one-or-many')).toMatchSnapshot();
  });
});