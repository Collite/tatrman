// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { migrateLexicon } from '../lexicon-migrate.js';
import { parseString } from '@tatrman/parser';

// RG-P4.S2.T2 — the `migrate-lexicon` codemod. Rewrites legacy `search {}` vocab
// into inline `lexicon {}`, trivia-safe (AST-span based) and idempotent.

function parses(src: string): boolean {
  return parseString(src, 'file:///m.ttrm').errors.filter((e) => e.code === 'ttr/parse-error').length === 0;
}

describe('migrateLexicon codemod', () => {
  it('rewrites `search { aliases }` → inline `lexicon { terms }`', () => {
    const before = 'model er schema entity\ndef entity customer { search { aliases: ["zákazník", "odběratel"] } }\n';
    const after = migrateLexicon(before);
    expect(after).toContain('lexicon { terms: ["zákazník", "odběratel"] }');
    expect(after).not.toContain('aliases');
    expect(parses(after)).toBe(true);
  });

  it('folds patterns + examples into the lexicon block', () => {
    const before = 'model er schema entity\ndef entity q { search { patterns: ["název .*"], examples: ["Kolik?"] } }\n';
    const after = migrateLexicon(before);
    expect(after).toContain('lexicon {');
    expect(after).toContain('patterns: ["název .*"]');
    expect(after).toContain('examples: ["Kolik?"]');
    expect(parses(after)).toBe(true);
  });

  it('keeps searchable/fuzzy in a slimmed `search {}` beside the lexicon block', () => {
    const before = 'model er schema entity\ndef entity customer { search { searchable: true, fuzzy: true, aliases: ["x"] } }\n';
    const after = migrateLexicon(before);
    expect(after).toContain('search { searchable: true, fuzzy: true }');
    expect(after).toContain('lexicon { terms: ["x"] }');
    expect(parses(after)).toBe(true);
  });

  it('leaves locale-keyed keywords in the slimmed search block (guided-manual)', () => {
    const before = 'model er schema entity\ndef entity customer { search { aliases: ["x"], keywords: { cs: ["tržba"] } } }\n';
    const after = migrateLexicon(before);
    expect(after).toContain('keywords: { cs: ["tržba"] }');
    expect(after).toContain('lexicon { terms: ["x"] }');
    expect(parses(after)).toBe(true);
  });

  it('is idempotent — a second pass is a no-op', () => {
    const before = 'model er schema entity\ndef entity customer { search { aliases: ["x"] } }\n';
    const once = migrateLexicon(before);
    expect(migrateLexicon(once)).toBe(once);
  });

  it('leaves a file with no legacy vocab untouched (incl. searchable-only)', () => {
    const src = 'model er schema entity\ndef entity customer { search { searchable: true } }\n';
    expect(migrateLexicon(src)).toBe(src);
  });

  it('preserves surrounding trivia (comments) outside the block', () => {
    const before = 'model er schema entity\n// keep me\ndef entity customer { search { aliases: ["x"] } } // trailing\n';
    const after = migrateLexicon(before);
    expect(after).toContain('// keep me');
    expect(after).toContain('// trailing');
  });
});
