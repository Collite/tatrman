import { describe, it, expect } from 'vitest';
// Stage 2 (RED): this module is introduced in Stage 3. Until then the import is
// unresolved and this whole file fails — that is the expected red state.
import { defaultSchemaForKind, modelForKind } from '../default-schema.js';

/**
 * Unit coverage for the single kind → model map (D4/D14/D15). `defaultSchemaForKind`
 * is a deprecated alias of `modelForKind`; both must agree for every kind. Covers
 * every `def.kind` produced by the parser (the camelCase kinds in Kinds.kt).
 */
describe('modelForKind / defaultSchemaForKind', () => {
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
    // binding
    ['er2dbEntity', 'binding'],
    ['er2dbAttribute', 'binding'],
    ['er2dbRelation', 'binding'],
    // cnc
    ['role', 'cnc'],
    ['er2cncRole', 'cnc'],
    // D14 — query + drillMap are db-layer objects; there is no `query` model.
    ['query', 'db'],
    ['drillMap', 'db'],
  ];

  for (const [kind, model] of cases) {
    it(`${kind} ⇒ ${model}`, () => {
      expect(modelForKind(kind)).toBe(model);
      // The deprecated alias must return the same value (single source of truth).
      expect(defaultSchemaForKind(kind)).toBe(model);
    });
  }

  it('unknown kind falls back to db (matches Kotlin)', () => {
    expect(modelForKind('totally-unknown')).toBe('db');
    expect(defaultSchemaForKind('totally-unknown')).toBe('db');
  });
});
