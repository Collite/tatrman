// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import type { Document } from '@tatrman/parser';
import {
  derivedPackage,
  effectivePackage,
  classifyPackageMismatch,
} from '../derivation.js';
import type { PackagesConfig } from '../manifest.js';

const ROOT = '/proj';
const flexible: PackagesConfig = { root: '', layout: 'flexible' };
const withRoot: PackagesConfig = { root: 'com.tatrman', layout: 'flexible' };
const ENTITY = 'def entity X { attributes: [def attribute id { type: int }] }';

function doc(src: string): Document {
  const ast = parseString(src, 'file:///proj/x.ttrm').ast;
  if (!ast) throw new Error('parse failed');
  return ast;
}

describe('PD1.2 — derivedPackage', () => {
  it('root "" + file a/b/er.ttrm → "a.b"', () => {
    expect(derivedPackage('/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
  });

  it('root "com.tatrman" + file a/b/er.ttrm → "com.tatrman.a.b"', () => {
    expect(derivedPackage('/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('com.tatrman.a.b');
  });

  it('root "" + root-level file → ""', () => {
    expect(derivedPackage('/proj/main.ttrm', ROOT, flexible)).toBe('');
  });

  it('root "com.tatrman" + root-level file → "com.tatrman"', () => {
    expect(derivedPackage('/proj/main.ttrm', ROOT, withRoot)).toBe('com.tatrman');
  });

  it('handles file:// URIs', () => {
    expect(derivedPackage('file:///proj/a/b/er.ttrm', ROOT, withRoot)).toBe('com.tatrman.a.b');
  });
});

describe('PD1.2 — effectivePackage', () => {
  it('no declaration, root "" → derived a.b', () => {
    const d = doc(`model er schema entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
  });

  it('no declaration, root "com.tatrman" → derived com.tatrman.a.b', () => {
    const d = doc(`model er schema entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('com.tatrman.a.b');
  });

  it('declaration a.b wins over derivation, any root (verbatim, root elided)', () => {
    const d = doc(`package a.b\nmodel er schema entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('a.b');
  });

  it('declaration x.y wins even when it diverges from the path', () => {
    const d = doc(`package x.y\nmodel er schema entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('x.y');
  });

  it('no-cascade: a child never inherits an ancestor declaration', () => {
    // a/er.ttrm declares `package renamed`; a/b/er.ttrm (no declaration) must
    // derive a.b, NOT renamed.b.
    const child = doc(`model er schema entity\n${ENTITY}`);
    expect(effectivePackage(child, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
    expect(effectivePackage(child, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('com.tatrman.a.b');
  });
});

describe('PD1.2 — classifyPackageMismatch', () => {
  const ns = (pkg: string) => doc(`package ${pkg}\nmodel er schema entity\n${ENTITY}`);

  it('matching declaration → none', () => {
    expect(classifyPackageMismatch(ns('a.b'), '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('none');
  });

  it('declaration eliding the root still matches → none', () => {
    expect(classifyPackageMismatch(ns('a.b'), '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('none');
  });

  it('leaf-only override → leaf', () => {
    expect(classifyPackageMismatch(ns('a.renamed'), '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('leaf');
  });

  it('prefix divergence (different non-leaf segment) → prefix', () => {
    expect(classifyPackageMismatch(ns('x.y'), '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('prefix');
  });

  it('prefix divergence (different segment count) → prefix', () => {
    expect(
      classifyPackageMismatch(ns('totally.different.thing'), '/proj/a/b/er.ttrm', ROOT, flexible)
    ).toBe('prefix');
  });

  it('no declaration → none', () => {
    expect(
      classifyPackageMismatch(doc(`model er schema entity\n${ENTITY}`), '/proj/a/b/er.ttrm', ROOT, flexible)
    ).toBe('none');
  });

  it('root-level file with a declaration → none (nothing to compare)', () => {
    expect(classifyPackageMismatch(ns('anything'), '/proj/main.ttrm', ROOT, flexible)).toBe('none');
  });
});
