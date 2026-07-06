import { describe, it, expect } from 'vitest';
import { TTR_GRAMMAR_VERSION, PROPERTY_MAP } from '@tatrman/grammar';

// Grammar-version guard. Bumping `// @grammar-version:` in TTR.g4 (and running
// the grammar prebuild) is what moves this constant; the assertion is the
// reminder that the CHANGELOG entry + downstream proto/version sync move with it.
describe('grammar version', () => {
  it('is 4.2 (semantics block — grounding Phase 1)', () => {
    expect(TTR_GRAMMAR_VERSION).toBe('4.2');
  });

  it('exposes the semantics property on exactly the four attachment kinds', () => {
    const hasSemantics = (kind: keyof typeof PROPERTY_MAP) =>
      PROPERTY_MAP[kind].some((p) => p.name === 'semantics');
    expect(hasSemantics('table')).toBe(true);
    expect(hasSemantics('column')).toBe(true);
    expect(hasSemantics('entity')).toBe(true);
    expect(hasSemantics('attribute')).toBe(true);
    // NOT on view/relation/query/role.
    expect(hasSemantics('view')).toBe(false);
    expect(hasSemantics('relation')).toBe(false);
    expect(hasSemantics('query')).toBe(false);
    expect(hasSemantics('role')).toBe(false);
  });
});
