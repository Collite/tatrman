import { describe, it, expect } from 'vitest';
import { format } from '../index.js';

const fmt = (src: string) => format(src, 'file:///t.ttrm');

const table = (pk: string, cols = 'def column ID { type: int }') =>
  `model db schema dbo\ndef table T { primaryKey: ${pk}, columns: [ ${cols} ] }\n`;

describe('formatter — primaryKey renders as bare ids', () => {
  it('rewrites a legacy quoted list to the bare form', () => {
    expect(fmt(table('["ID"]'))).toContain('primaryKey: [ID]');
  });

  it('normalises a single bare id to the bare list form', () => {
    expect(fmt(table('ID'))).toContain('primaryKey: [ID]');
  });

  it('keeps a multi-column bare list bare', () => {
    const out = fmt(table('[ID, KOD]', 'def column ID { type: int }, def column KOD { type: text }'));
    expect(out).toContain('primaryKey: [ID, KOD]');
  });

  it('preserves an accented identifier bare', () => {
    const out = fmt('model db schema dbo\ndef table T { primaryKey: ["id_dokladů"], columns: [ def column id_dokladů { type: int } ] }\n');
    expect(out).toContain('primaryKey: [id_dokladů]');
  });

  it('falls back to quotes when a key is not a valid identifier', () => {
    // A quoted key with a space can't be a bare id → the whole list stays quoted.
    const out = fmt('model db schema dbo\ndef table T { primaryKey: ["has space"], columns: [ def column id { type: int } ] }\n');
    expect(out).toContain('primaryKey: ["has space"]');
  });
});
