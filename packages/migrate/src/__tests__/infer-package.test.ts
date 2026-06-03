import { describe, it, expect } from 'vitest';
import { normaliseSep, inferPackage } from '../index.js';

describe('inferPackage', () => {
  it('infers foo.bar for foo/bar/baz.ttr', () => {
    const result = inferPackage('/project/root/foo/bar/baz.ttr', '/project/root');
    expect(result).toBe('foo.bar');
  });

  it('infers empty string for root-level baz.ttr', () => {
    const result = inferPackage('/project/root/baz.ttr', '/project/root');
    expect(result).toBe('');
  });

  it('infers foo for foo/baz.ttr', () => {
    const result = inferPackage('/project/root/foo/baz.ttr', '/project/root');
    expect(result).toBe('foo');
  });
});

describe('normaliseSep', () => {
  it('converts backslashes on windows', () => {
    expect(normaliseSep('foo\\bar\\baz.ttr')).toBe('foo/bar/baz.ttr');
  });

  it('leaves forward slashes alone', () => {
    expect(normaliseSep('foo/bar/baz.ttr')).toBe('foo/bar/baz.ttr');
  });
});