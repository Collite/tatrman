// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import { DiagnosticCode } from '../diagnostics.js';
import type { LexiconEntryDef } from '../ast.js';

// RG-P4.S1.T1 — the grammar corpus. Grammar 4.4 makes the `lexicon` model, the
// `term`/`pattern`/`example` def kinds, the unit-level `locale` header, and the
// inline `lexicon { … }` sugar block PARSE. AST shape/spans + desugar/validation
// are exercised in the semantics suite (S1.T3–T5); here we assert parse-success
// (no ParseError), that each definition is recognised (none silently dropped),
// and that the walker surfaces the load-bearing fields.

function parseErrors(src: string, uri = 'file:///lex.ttrm') {
  const { errors } = parseString(src, uri);
  return errors.filter((e) => e.code === DiagnosticCode.ParseError);
}

const CANONICAL = `model lexicon

def term trzba { for: md.measure.net, forms: ["tržba", "tržby", "obrat", "utržit"] }
def pattern nazev { for: db.query.by_name, match: "název .*" }
def example q1 { for: md.cubelet.sales, text: "Kolik jsme utržili za Octavie" }
`;

// Targets across all three layers (er / db / md).
const TARGETS = `model lexicon
def term t_er { for: er.entity.customer, forms: ["zákazník"] }
def term t_db { for: db.dbo.customers, forms: ["customers"] }
def term t_md { for: md.measure.net, forms: ["tržba"] }
`;

const LOCALE_HEADER = `model lexicon locale cs
def term t { for: md.measure.net, forms: ["tržba"] }
`;

// Inline `lexicon{}` sugar on md carriers (RS-10) and er/db data-bearing kinds.
const INLINE_MD = `model md
def measure net { domain: md.Money, class: additive, aggregation: sum, lexicon { terms: ["tržba", "obrat", "utržit"] } }
def dimension time { key: day, lexicon { terms: ["čas", "období"] } }
def cubelet sales { grain: [Customer.code], lexicon { terms: ["prodej"] } }
`;

const INLINE_ER = `model er
def entity customer { lexicon { terms: ["zákazník", "odběratel"] } }
def attribute name { type: string, lexicon { terms: ["jméno"] } }
`;

const INLINE_DB = `model db
def table customers { lexicon { terms: ["zákazníci"] } }
def column kod { type: string, lexicon { terms: ["kód"] } }
`;

describe('lexicon grammar 4.4 — parse-success', () => {
  it('a `model lexicon` file with term/pattern/example parses with no errors', () => {
    const { ast, errors } = parseString(CANONICAL, 'file:///lex.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.modelDirective?.modelCode).toBe('lexicon');
    expect(ast?.definitions).toHaveLength(3);
    expect(ast?.definitions.map((d) => d.kind)).toEqual(['term', 'pattern', 'example']);
  });

  it('a `def term` carries its target ref + surface forms', () => {
    const { ast } = parseString(CANONICAL, 'file:///lex.ttrm');
    const term = ast?.definitions[0] as LexiconEntryDef;
    expect(term.kind).toBe('term');
    expect(term.name).toBe('trzba');
    expect(term.target?.path).toBe('md.measure.net');
    expect(term.forms).toEqual(['tržba', 'tržby', 'obrat', 'utržit']);
  });

  it('a `def pattern` carries `match:`; a `def example` carries `text:`', () => {
    const { ast } = parseString(CANONICAL, 'file:///lex.ttrm');
    const pattern = ast?.definitions[1] as LexiconEntryDef;
    const example = ast?.definitions[2] as LexiconEntryDef;
    expect(pattern.target?.path).toBe('db.query.by_name');
    expect(pattern.match).toBe('název .*');
    expect(example.target?.path).toBe('md.cubelet.sales');
    expect(example.text).toBe('Kolik jsme utržili za Octavie');
  });

  it('lexicon targets resolve across er / db / md layers (parse level)', () => {
    const { ast, errors } = parseString(TARGETS, 'file:///lex.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    const paths = (ast?.definitions as LexiconEntryDef[]).map((d) => d.target?.path);
    expect(paths).toEqual(['er.entity.customer', 'db.dbo.customers', 'md.measure.net']);
  });

  it('the unit-level `locale` header parses and is captured', () => {
    const { ast, errors } = parseString(LOCALE_HEADER, 'file:///lex.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.modelDirective?.modelCode).toBe('lexicon');
    expect(ast?.modelDirective?.locale).toBe('cs');
  });

  it('inline `lexicon{}` sugar parses on md carriers (measure/dimension/cubelet)', () => {
    const { ast, errors } = parseString(INLINE_MD, 'file:///md.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.definitions).toHaveLength(3);
    const measure = ast?.definitions[0] as { lexicon?: { terms?: string[] } };
    expect(measure.lexicon?.terms).toEqual(['tržba', 'obrat', 'utržit']);
  });

  it('inline `lexicon{}` sugar parses on er carriers (entity/attribute)', () => {
    const { ast, errors } = parseString(INLINE_ER, 'file:///er.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    const entity = ast?.definitions[0] as { lexicon?: { terms?: string[] } };
    expect(entity.lexicon?.terms).toEqual(['zákazník', 'odběratel']);
  });

  it('inline `lexicon{}` sugar parses on db carriers (table/column)', () => {
    const { errors } = parseString(INLINE_DB, 'file:///db.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
  });

  it('the new keywords stay usable as cross-ref fragments / bare ids (idPart)', () => {
    // `term`, `pattern`, `example`, `for`, `forms`, `match`, `locale` as ids.
    const src = `model db
def table term { columns: [ def column pattern { type: string } ] }
def column example { type: string }
def column for { type: string }
def column locale { type: string }
`;
    const { ast, errors } = parseString(src, 'file:///db.ttrm');
    expect(errors.filter((e) => e.code === DiagnosticCode.ParseError)).toEqual([]);
    expect(ast?.definitions).toHaveLength(4);
  });
});

describe('lexicon grammar 4.4 — parse-level rejection', () => {
  it('`forms:` with a non-list value is a parse error', () => {
    expect(parseErrors('model lexicon\ndef term x { forms: notAList }').length).toBeGreaterThan(0);
  });

  it('an unknown def kind in a lexicon model is a parse error', () => {
    expect(parseErrors('model lexicon\ndef frobnicate x { }').length).toBeGreaterThan(0);
  });
});
