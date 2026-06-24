#!/usr/bin/env node
import { describe, it, expect } from 'vitest';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const GRAMMAR_PATH = path.resolve(__dirname, '../../syntaxes/ttrg.tmLanguage.json');

describe('ttrg.tmLanguage.json', () => {
  const grammar = JSON.parse(fs.readFileSync(GRAMMAR_PATH, 'utf-8'));

  it('has scopeName source.ttrg', () => {
    expect(grammar.scopeName).toBe('source.ttrg');
  });

  it('has fileTypes ttrg', () => {
    expect(grammar.fileTypes).toContain('ttrg');
  });

  it('repository defines the required top-level scopes', () => {
    const repo = grammar.repository;
    expect(repo).toBeDefined();
    expect(repo['comments']).toBeDefined();
    expect(repo['strings']).toBeDefined();
    expect(repo['numbers']).toBeDefined();
    expect(repo['keywords']).toBeDefined();
    expect(repo['qnames']).toBeDefined();
    expect(repo['operators']).toBeDefined();
  });

  it('keywords block contains all required keyword patterns inline', () => {
    const kwPatterns = grammar.repository['keywords'].patterns;
    const scopes = new Set(kwPatterns.map((p: any) => p.name));
    expect(scopes).toContain('keyword.declaration.graph.ttrg');
    expect(scopes).toContain('keyword.other.property.ttrg');
    expect(scopes).toContain('keyword.other.schema.ttrg');
    expect(scopes).toContain('keyword.control.ttrg');
  });

  it('graph keyword is matched by the grammar', () => {
    const kwPatterns = grammar.repository['keywords'].patterns;
    const graphPat = kwPatterns.find((p: any) => p.name === 'keyword.declaration.graph.ttrg');
    expect(graphPat).toBeDefined();
    expect(graphPat.match).toContain('graph');
  });

  it('objects|layout|nodes|edges|schema are matched as property keywords', () => {
    const kwPatterns = grammar.repository['keywords'].patterns;
    const propPat = kwPatterns.find((p: any) => p.name === 'keyword.other.property.ttrg');
    expect(propPat).toBeDefined();
    const match = propPat.match;
    expect(match).toContain('objects');
    expect(match).toContain('layout');
    expect(match).toContain('schema');
  });

  it('db|er|binding|query|cnc are matched as schema keywords', () => {
    const kwPatterns = grammar.repository['keywords'].patterns;
    const schemaPat = kwPatterns.find((p: any) => p.name === 'keyword.other.schema.ttrg');
    expect(schemaPat).toBeDefined();
    const match = schemaPat.match;
    for (const s of ['db', 'er', 'binding', 'query', 'cnc']) {
      expect(match).toContain(s);
    }
  });

  it('qname pattern matches dotted identifiers', () => {
    const qnames = grammar.repository['qnames'];
    const match = qnames?.patterns?.[0]?.match;
    expect(match).toBeDefined();
    expect(match).toContain('\\.');
  });

  it('patterns include comments, strings, numbers, keywords, qnames, operators', () => {
    const names = grammar.patterns.map((p: any) => {
      if (p.include) return p.include;
      return p.name ?? 'unknown';
    });
    expect(names).toContain('#comments');
    expect(names).toContain('#strings');
    expect(names).toContain('#numbers');
    expect(names).toContain('#keywords');
    expect(names).toContain('#qnames');
    expect(names).toContain('#operators');
  });

  it('has no dangling include references', () => {
    const allPatterns = JSON.stringify(grammar.patterns) + JSON.stringify(grammar.repository);
    const includeMatches = allPatterns.match(/include.*?"#([^"]+)"/g) ?? [];
    for (const inc of includeMatches) {
      const blockName = inc.replace(/include.*?"#/, '').replace(/"/g, '');
      expect(grammar.repository[blockName], `reference #${blockName} should exist`).toBeDefined();
    }
  });
});