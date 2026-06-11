import { describe, it, expect } from 'vitest';
// Stage 2 (RED): this module is introduced in Stage 3. Until then the import is
// unresolved and this whole file fails — that is the expected red state.
import { defaultSchemaForKind } from '../default-schema.js';

/**
 * Stage 2.5 — unit coverage for the kind → default-schema helper. Covers every
 * `def.kind` produced by the parser (the camelCase kinds in Kinds.kt). The map
 * is normative in docs/features/pkg-schema-defaults/INDEX.md.
 */
describe('defaultSchemaForKind', () => {
  const cases: Array<[string, string]> = [
    // db
    ['model', 'db'],
    ['table', 'db'],
    ['view', 'db'],
    ['column', 'db'],
    ['index', 'db'],
    ['constraint', 'db'],
    ['fk', 'db'],
    ['procedure', 'db'],
    // er
    ['entity', 'er'],
    ['attribute', 'er'],
    ['relation', 'er'],
    // map
    ['er2dbEntity', 'map'],
    ['er2dbAttribute', 'map'],
    ['er2dbRelation', 'map'],
    // cnc
    ['role', 'cnc'],
    ['er2cncRole', 'cnc'],
    // query
    ['query', 'query'],
    ['drillMap', 'query'],
  ];

  for (const [kind, schema] of cases) {
    it(`${kind} ⇒ ${schema}`, () => {
      expect(defaultSchemaForKind(kind)).toBe(schema);
    });
  }

  it('unknown kind falls back to db (matches Kotlin)', () => {
    expect(defaultSchemaForKind('totally-unknown')).toBe('db');
  });
});
