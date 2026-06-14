import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { EntityDef, QueryDef } from '../ast.js';

/**
 * Regression: a plain triple-string whose first line is a bare word + newline
 * (e.g. `"""Ne␊1 = Ano"""`) lexes as TAGGED_BLOCK_LITERAL (that token wins over
 * TRIPLE_STRING_LITERAL). Outside `sourceText`/`definitionSql` it must still be
 * read as a plain triple-string — not a parse error. (ai-platform's model-ttr
 * uses these in `description` / `valueLabels`.)
 */
const errs = (src: string) => parseString(src).errors.filter((e) => e.severity === 'error');

describe('tagged-block-like plain triple-strings (regression)', () => {
  it('a bare-word triple-string in a description parses (no error)', () => {
    const src = 'schema er namespace entity\ndef entity e {\n  description: """Ne\n1 = Ano"""\n}';
    const e = errs(src);
    expect(e, e[0]?.message).toHaveLength(0);
    const def = parseString(src).ast!.definitions[0] as EntityDef;
    expect(def.description).toMatchObject({ kind: 'tripleString', value: 'Ne\n1 = Ano' });
  });

  it('a bare-word triple-string in a nested valueLabels parses', () => {
    const src =
      'schema er namespace entity\ndef entity e {\n  attributes: [ def attribute a { type: int, valueLabels: { "0": { cs: """Ne\n1 = Ano""" } } } ]\n}';
    expect(errs(src)).toHaveLength(0);
  });

  it('still tag-peels a real sourceText embedded block', () => {
    const src = 'schema query namespace query\ndef query q { sourceText: """sql\nSELECT 1\n""" }';
    expect(errs(src)).toHaveLength(0);
    const q = parseString(src).ast!.definitions[0] as QueryDef;
    expect(q.sourceText).toMatchObject({ kind: 'taggedBlock', tag: 'sql', language: 'SQL' });
  });
});
