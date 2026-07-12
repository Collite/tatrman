// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { normaliseSep, inferPackage } from '../index.js';

describe('inferPackage', () => {
  it('infers foo.bar for foo/bar/baz.ttrm', () => {
    const result = inferPackage('/project/root/foo/bar/baz.ttrm', '/project/root');
    expect(result).toBe('foo.bar');
  });

  it('infers empty string for root-level baz.ttrm', () => {
    const result = inferPackage('/project/root/baz.ttrm', '/project/root');
    expect(result).toBe('');
  });

  it('infers foo for foo/baz.ttrm', () => {
    const result = inferPackage('/project/root/foo/baz.ttrm', '/project/root');
    expect(result).toBe('foo');
  });
});

describe('normaliseSep', () => {
  it('converts backslashes on windows', () => {
    expect(normaliseSep('foo\\bar\\baz.ttrm')).toBe('foo/bar/baz.ttrm');
  });

  it('leaves forward slashes alone', () => {
    expect(normaliseSep('foo/bar/baz.ttrm')).toBe('foo/bar/baz.ttrm');
  });
});