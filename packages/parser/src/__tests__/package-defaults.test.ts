import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

/**
 * Stage 1 (pkg-schema-defaults, Item 1) — lock in the already-shipped behavior
 * that `package` is optional and defaults to the empty-string root package.
 * Verification only; no production code backs these beyond `packageDecl?`.
 */
describe('Item 1 — optional package declaration', () => {
  it('a package-less file parses with no packageDecl and no errors', () => {
    const result = parseString(
      'schema er namespace entity\n' +
      'def entity X { attributes: [def attribute id { type: int }] }\n',
      'root.ttr'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.packageDecl).toBeUndefined();
    // The empty-string root package is the default consumers read.
    expect(result.ast?.packageDecl?.name ?? '').toBe('');
  });

  it('a file with `package a.b.c` exposes packageDecl.name === "a.b.c"', () => {
    const result = parseString(
      'package a.b.c\n' +
      'schema er namespace entity\n' +
      'def entity X { attributes: [def attribute id { type: int }] }\n',
      'a/b/c/x.ttr'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.packageDecl?.name).toBe('a.b.c');
    expect(result.ast?.packageDecl?.parts).toEqual(['a', 'b', 'c']);
  });
});
