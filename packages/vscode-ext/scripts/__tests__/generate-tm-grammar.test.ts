#!/usr/bin/env node
import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseGrammar, tokenToScope } from '../generate-tm-grammar.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const GRAMMAR_PATH = path.resolve(__dirname, '../../../grammar/src/TTR.g4');

const EXPECTED_SCOPES = [
  'keyword.control.def.ttrm',
  'keyword.control.package.ttrm',    // NEW (v1.1)
  'keyword.control.import.ttrm',      // NEW (v1.1)
  'keyword.declaration.graph.ttrm',   // NEW (v1.1)
  'keyword.other.packages.ttrm',      // area body (v3.0; was v2.3 .ttrd)
  'keyword.other.entities.ttrm',      // area body (v3.0; was v2.3 .ttrd)
  'keyword.other.schema.ttrm',
  'keyword.other.kind.ttrm',
  'keyword.other.property.ttrm',
  'support.type.primitive.ttrm',
  'constant.language.ttrm',
  'constant.language.indextype.ttrm',
  'constant.language.constrainttype.ttrm',
  'constant.language.querylang.ttrm',
  'punctuation.separator.ttrm',
  'punctuation.section.braces.ttrm',
  'punctuation.section.brackets.ttrm',
  'punctuation.section.parens.ttrm',
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
    expect(queryScope).toBe('keyword.other.schema.ttrm');
  });

  it('BOOLEAN_LITERAL maps to constant.language.ttrm (covers true|false)', () => {
    const scope = tokenToScope('BOOLEAN_LITERAL', 'true');
    expect(scope).toBe('constant.language.ttrm');
  });

  it('schema codes (DB, ER, BINDING, CNC, QUERY) all map to schema scope', () => {
    for (const [name, literal] of [['DB', 'db'], ['ER', 'er'], ['BINDING', 'binding'], ['CNC', 'cnc'], ['QUERY', 'query']]) {
      expect(tokenToScope(name, literal)).toBe('keyword.other.schema.ttrm');
    }
  });

  it('v1.1 keywords map to dedicated scopes', () => {
    expect(tokenToScope('PACKAGE', 'package')).toBe('keyword.control.package.ttrm');
    expect(tokenToScope('IMPORT',  'import')).toBe('keyword.control.import.ttrm');
    expect(tokenToScope('GRAPH',   'graph')).toBe('keyword.declaration.graph.ttrm');
    expect(tokenToScope('OBJECTS', 'objects')).toBe('keyword.other.property.ttrm');
    expect(tokenToScope('LAYOUT',  'layout')).toBe('keyword.other.property.ttrm');
  });

  it('v3.0 area keywords map to dedicated scopes', () => {
    expect(tokenToScope('AREA',     'area')).toBe('keyword.other.kind.ttrm');
    expect(tokenToScope('PACKAGES', 'packages')).toBe('keyword.other.packages.ttrm');
    expect(tokenToScope('ENTITIES', 'entities')).toBe('keyword.other.entities.ttrm');
  });
});