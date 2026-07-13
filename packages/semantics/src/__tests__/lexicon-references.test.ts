// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import type { LexiconEntryDef } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { collectReferences } from '../references.js';

// RG-P4.S1.T5 — referential integrity. A lexicon `for:` target resolves through
// the SAME reference resolver every other cross-ref uses (goto-def + unresolved-
// reference fall out for free). A target naming a non-existent def is a resolver
// miss with tried-qname locations; a valid target resolves to the md symbol.

function projectWith(files: Array<{ uri: string; src: string }>) {
  const table = new ProjectSymbolTable();
  const asts = new Map<string, ReturnType<typeof parseString>['ast']>();
  for (const f of files) {
    const ast = parseString(f.src, f.uri).ast!;
    table.upsertDocument(f.uri, ast, ast.modelDirective?.modelCode ?? 'db', ast.modelDirective?.schema ?? '');
    asts.set(f.uri, ast);
  }
  return { table, asts };
}

const MD_FILE = {
  uri: 'md.ttrm',
  src: `model md
def measure net { domain: md.Money, class: additive, aggregation: sum }`,
};

const LEXICON_FILE = {
  uri: 'lex.ttrm',
  src: `model lexicon
def term trzba { for: md.measure.net, forms: ["tržba", "obrat"] }
def term ghost { for: md.measure.nonexistent, forms: ["x"] }`,
};

describe('lexicon references — referential integrity (T5)', () => {
  it('collectReferences surfaces the `for:` target as a navigable Reference', () => {
    const ast = parseString(LEXICON_FILE.src, LEXICON_FILE.uri).ast!;
    const term = ast.definitions[0] as LexiconEntryDef;
    const refs = collectReferences(term);
    expect(refs).toHaveLength(1);
    expect(refs[0].path).toBe('md.measure.net');
    // Span-carrying, so goto-def lands on the target token.
    expect(refs[0].source).toBeDefined();
  });

  it('a term targeting an existing md.measure resolves', () => {
    const { table } = projectWith([MD_FILE, LEXICON_FILE]);
    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'md.measure.net', parts: ['md', 'measure', 'net'] },
      { schemaCode: 'lexicon', namespace: '' },
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('md.measure.net');
  });

  it('a term targeting a non-existent md.measure is a resolver miss with tried qnames (→ unresolved-reference)', () => {
    const { table } = projectWith([MD_FILE, LEXICON_FILE]);
    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'md.measure.nonexistent', parts: ['md', 'measure', 'nonexistent'] },
      { schemaCode: 'lexicon', namespace: '' },
    );
    expect(res.resolved).toBe(false);
    if (!res.resolved) {
      expect(res.tried.length).toBeGreaterThan(0);
      expect(res.tried.some((t) => t.candidate.includes('nonexistent'))).toBe(true);
    }
  });
});
