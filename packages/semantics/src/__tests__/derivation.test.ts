import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import type { Document } from '@modeler/parser';
import {
  derivedPackage,
  effectivePackage,
  classifyPackageMismatch,
} from '../derivation.js';
import type { PackagesConfig } from '../manifest.js';

const ROOT = '/proj';
const flexible: PackagesConfig = { root: '', layout: 'flexible' };
const withRoot: PackagesConfig = { root: 'cz.dfpartner', layout: 'flexible' };
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

  it('root "cz.dfpartner" + file a/b/er.ttrm → "cz.dfpartner.a.b"', () => {
    expect(derivedPackage('/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('cz.dfpartner.a.b');
  });

  it('root "" + root-level file → ""', () => {
    expect(derivedPackage('/proj/main.ttrm', ROOT, flexible)).toBe('');
  });

  it('root "cz.dfpartner" + root-level file → "cz.dfpartner"', () => {
    expect(derivedPackage('/proj/main.ttrm', ROOT, withRoot)).toBe('cz.dfpartner');
  });

  it('handles file:// URIs', () => {
    expect(derivedPackage('file:///proj/a/b/er.ttrm', ROOT, withRoot)).toBe('cz.dfpartner.a.b');
  });
});

describe('PD1.2 — effectivePackage', () => {
  it('no declaration, root "" → derived a.b', () => {
    const d = doc(`schema er namespace entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
  });

  it('no declaration, root "cz.dfpartner" → derived cz.dfpartner.a.b', () => {
    const d = doc(`schema er namespace entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('cz.dfpartner.a.b');
  });

  it('declaration a.b wins over derivation, any root (verbatim, root elided)', () => {
    const d = doc(`package a.b\nschema er namespace entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('a.b');
  });

  it('declaration x.y wins even when it diverges from the path', () => {
    const d = doc(`package x.y\nschema er namespace entity\n${ENTITY}`);
    expect(effectivePackage(d, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('x.y');
  });

  it('no-cascade: a child never inherits an ancestor declaration', () => {
    // a/er.ttrm declares `package renamed`; a/b/er.ttrm (no declaration) must
    // derive a.b, NOT renamed.b.
    const child = doc(`schema er namespace entity\n${ENTITY}`);
    expect(effectivePackage(child, '/proj/a/b/er.ttrm', ROOT, flexible)).toBe('a.b');
    expect(effectivePackage(child, '/proj/a/b/er.ttrm', ROOT, withRoot)).toBe('cz.dfpartner.a.b');
  });
});

describe('PD1.2 — classifyPackageMismatch', () => {
  const ns = (pkg: string) => doc(`package ${pkg}\nschema er namespace entity\n${ENTITY}`);

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
      classifyPackageMismatch(doc(`schema er namespace entity\n${ENTITY}`), '/proj/a/b/er.ttrm', ROOT, flexible)
    ).toBe('none');
  });

  it('root-level file with a declaration → none (nothing to compare)', () => {
    expect(classifyPackageMismatch(ns('anything'), '/proj/main.ttrm', ROOT, flexible)).toBe('none');
  });
});
