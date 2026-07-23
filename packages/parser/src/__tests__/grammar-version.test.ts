// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { TTR_GRAMMAR_VERSION, PROPERTY_MAP } from '@tatrman/grammar';

// Grammar-version guard. Bumping `// @grammar-version:` in TTR.g4 (and running
// the grammar prebuild) is what moves this constant; the assertion is the
// reminder that the CHANGELOG entry + downstream proto/version sync move with it.
describe('grammar version', () => {
  it('is 0.11 (PL-P4.S3 — the H-1 `security { }` block: own/classify/grant/mask)', () => {
    expect(TTR_GRAMMAR_VERSION).toBe('0.11');
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

  it('exposes the inline lexicon property on the er/db carrier kinds (v4.4)', () => {
    const hasLexicon = (kind: keyof typeof PROPERTY_MAP) =>
      PROPERTY_MAP[kind].some((p) => p.name === 'lexicon');
    expect(hasLexicon('table')).toBe(true);
    expect(hasLexicon('column')).toBe(true);
    expect(hasLexicon('entity')).toBe(true);
    expect(hasLexicon('attribute')).toBe(true);
    // NOT on view/relation/query/role.
    expect(hasLexicon('view')).toBe(false);
    expect(hasLexicon('relation')).toBe(false);
  });
});
