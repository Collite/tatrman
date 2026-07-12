// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

/**
 * Phase 1 (embedded-sql) — RED. Tag-resolution diagnostics (DESIGN §5/§6),
 * emitted by the walker when it resolves the tag against TAG_REGISTRY (1.3):
 *   - unknown tag (C8)        → diagnostic anchored on the tag
 *   - `language:` ↔ tag clash → diagnostic
 *   - `language:` on a tagged query → soft-deprecation warning
 * These reference diagnostic codes that do not exist yet, so the specs are red
 * until 1.3 wires the registry and emits them.
 */
const codes = (src: string): Array<string | undefined> =>
  parseString(src).errors.map((e) => e.code);

describe('tagged-block tag-resolution diagnostics (embedded-sql Phase 1)', () => {
  it('C8 — an unknown tag is flagged', () => {
    const c = codes('def query c8 {\n  sourceText: """nope\nx\n"""\n}');
    expect(c).toContain('ttr/unknown-language-tag');
  });

  it('`language:` disagreeing with the block tag is flagged', () => {
    // tag says postgres (SQL/postgres), language says TRANSFORMATION_DSL.
    const c = codes(
      'def query m {\n  language: TRANSFORMATION_DSL\n  sourceText: """postgres\nSELECT 1\n"""\n}',
    );
    expect(c).toContain('ttr/language-tag-mismatch');
  });

  it('`language:` on a tagged query is soft-deprecated', () => {
    const c = codes('def query d {\n  language: SQL\n  sourceText: """sql\nSELECT 1\n"""\n}');
    expect(c).toContain('ttr/deprecated-language-property');
  });
});
