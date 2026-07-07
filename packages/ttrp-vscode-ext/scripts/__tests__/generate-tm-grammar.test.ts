import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseGrammar, tokenToScope, buildGrammar } from '../generate-tm-grammar.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const GRAMMAR_PATH = path.resolve(here, '../../../grammar/src/TTRP.g4');
const OUTPUT_PATH = path.resolve(here, '../../syntaxes/ttrp.tmLanguage.json');

describe('TTR-P TextMate grammar generator', () => {
  const g4 = fs.readFileSync(GRAMMAR_PATH, 'utf8');

  it('extracts literal keyword tokens from TTRP.g4', () => {
    const tokens = parseGrammar(g4);
    const literals = tokens.map((t) => t.literal);
    expect(literals).toContain('container');
    expect(literals).toContain('->');
    // Ops like `aggregate`/`filter` are IDENT, not lexer tokens — they must NOT appear.
    expect(literals).not.toContain('aggregate');
  });

  it('maps declaration and control keywords to scopes', () => {
    expect(tokenToScope('CONTAINER')).toBe('keyword.control.declaration.ttrp');
    expect(tokenToScope('AFTER')).toBe('keyword.control.flow.ttrp');
    expect(tokenToScope('ARROW')).toBe('keyword.operator.arrow.ttrp');
  });

  it('emits the four fence-delegation rules (sql, pandas, ttrb) and reserved ports', () => {
    const grammar = buildGrammar(g4) as { patterns: Array<Record<string, unknown>>; repository: Record<string, unknown> };
    const serialized = JSON.stringify(grammar);
    expect(serialized).toContain('meta.embedded.block.sql');
    expect(serialized).toContain('source.sql');
    expect(serialized).toContain('meta.embedded.block.pandas');
    expect(serialized).toContain('source.python');
    expect(serialized).toContain('string.quoted.fenced.ttrb.ttrp');
    expect(serialized).toContain('variable.language.port.ttrp');
  });

  it('the committed ttrp.tmLanguage.json is up to date with TTRP.g4', () => {
    const current = fs.readFileSync(OUTPUT_PATH, 'utf8');
    const regenerated = JSON.stringify(buildGrammar(g4), null, 2) + '\n';
    expect(current).toBe(regenerated);
  });
});
