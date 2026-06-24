#!/usr/bin/env node
import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseGrammar, tokenToScope } from '../generate-tm-grammar.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const GRAMMAR_PATH = path.resolve(__dirname, '../../../grammar/src/TTR.g4');

const EXPECTED_SCOPES = [
  'keyword.control.def.ttr',
  'keyword.control.package.ttr',    // NEW (v1.1)
  'keyword.control.import.ttr',      // NEW (v1.1)
  'keyword.declaration.graph.ttr',   // NEW (v1.1)
  'keyword.declaration.domain.ttr',  // NEW (v2.3 .ttrd)
  'keyword.other.packages.ttr',      // NEW (v2.3 .ttrd)
  'keyword.other.entities.ttr',      // NEW (v2.3 .ttrd)
  'keyword.other.schema.ttr',
  'keyword.other.kind.ttr',
  'keyword.other.property.ttr',
  'support.type.primitive.ttr',
  'constant.language.ttr',
  'constant.language.indextype.ttr',
  'constant.language.constrainttype.ttr',
  'constant.language.querylang.ttr',
  'punctuation.separator.ttr',
  'punctuation.section.braces.ttr',
  'punctuation.section.brackets.ttr',
  'punctuation.section.parens.ttr',
];

describe('TextMate grammar generator', () => {
  const g4Content = fs.readFileSync(GRAMMAR_PATH, 'utf-8');
  const tokens = parseGrammar(g4Content);

  it('parses at least 100 tokens from TTR.g4', () => {
    expect(tokens.length).toBeGreaterThanOrEqual(100);
  });

  it('every expected scope has at least one token', () => {
    const byScope = new Map<string, number>();
    for (const token of tokens) {
      const scope = tokenToScope(token.name, token.literal);
      if (!scope) continue;
      byScope.set(scope, (byScope.get(scope) ?? 0) + 1);
    }
    for (const scope of EXPECTED_SCOPES) {
      expect(byScope.get(scope) ?? 0).toBeGreaterThan(0);
    }
  });

  it('no duplicate patterns within any scope', () => {
    const byScope = new Map<string, string[]>();
    for (const token of tokens) {
      const scope = tokenToScope(token.name, token.literal);
      if (!scope) continue;
      if (!byScope.has(scope)) byScope.set(scope, []);
      byScope.get(scope)!.push(token.literal);
    }
    for (const [, lits] of byScope) {
      const unique = [...new Set(lits)];
      expect(lits.length).toBe(unique.length);
    }
  });

  it('QUERY is classified as schema keyword, not kind', () => {
    const queryScope = tokenToScope('QUERY', 'query');
    expect(queryScope).toBe('keyword.other.schema.ttr');
  });

  it('BOOLEAN_LITERAL maps to constant.language.ttr (covers true|false)', () => {
    const scope = tokenToScope('BOOLEAN_LITERAL', 'true');
    expect(scope).toBe('constant.language.ttr');
  });

  it('schema codes (DB, ER, BINDING, CNC, QUERY) all map to schema scope', () => {
    for (const [name, literal] of [['DB', 'db'], ['ER', 'er'], ['BINDING', 'binding'], ['CNC', 'cnc'], ['QUERY', 'query']]) {
      expect(tokenToScope(name, literal)).toBe('keyword.other.schema.ttr');
    }
  });

  it('v1.1 keywords map to dedicated scopes', () => {
    expect(tokenToScope('PACKAGE', 'package')).toBe('keyword.control.package.ttr');
    expect(tokenToScope('IMPORT',  'import')).toBe('keyword.control.import.ttr');
    expect(tokenToScope('GRAPH',   'graph')).toBe('keyword.declaration.graph.ttr');
    expect(tokenToScope('OBJECTS', 'objects')).toBe('keyword.other.property.ttr');
    expect(tokenToScope('LAYOUT',  'layout')).toBe('keyword.other.property.ttr');
  });

  it('v2.3 .ttrd keywords map to dedicated scopes', () => {
    expect(tokenToScope('DOMAIN',   'domain')).toBe('keyword.declaration.domain.ttr');
    expect(tokenToScope('PACKAGES', 'packages')).toBe('keyword.other.packages.ttr');
    expect(tokenToScope('ENTITIES', 'entities')).toBe('keyword.other.entities.ttr');
  });
});