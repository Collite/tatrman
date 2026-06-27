import { describe, it, expect } from 'vitest';
import { parseQname, qnameToString, buildQname } from '../qname.js';

describe('parseQname', () => {
  it('parses er.entity.artikl', () => {
    expect(parseQname('er.entity.artikl')).toEqual({
      schemaCode: 'er',
      namespace: 'entity',
      parts: ['artikl'],
    });
  });

  it('parses er.entity.artikl.id_artiklu (sub-part)', () => {
    expect(parseQname('er.entity.artikl.id_artiklu')).toEqual({
      schemaCode: 'er',
      namespace: 'entity',
      parts: ['artikl', 'id_artiklu'],
    });
  });

  it('parses db.dbo.QZBOZI_DF', () => {
    expect(parseQname('db.dbo.QZBOZI_DF')).toEqual({
      schemaCode: 'db',
      namespace: 'dbo',
      parts: ['QZBOZI_DF'],
    });
  });

  it('parses cnc.role.fact', () => {
    expect(parseQname('cnc.role.fact')).toEqual({
      schemaCode: 'cnc',
      namespace: 'role',
      parts: ['fact'],
    });
  });

  it('returns null for non-schema-code first segment', () => {
    expect(parseQname('not-a-schema.x.y')).toBeNull();
  });

  it('returns null for single-segment input', () => {
    expect(parseQname('foo')).toBeNull();
  });

  it('two-segment input has empty schema and one part', () => {
    expect(parseQname('er.artikl')).toEqual({
      schemaCode: 'er',
      namespace: '',
      parts: ['artikl'],
    });
  });
});

describe('qnameToString', () => {
  it('round-trips er.entity.artikl', () => {
    const q = parseQname('er.entity.artikl')!;
    expect(qnameToString(q)).toBe('er.entity.artikl');
  });

  it('round-trips db.dbo.QZBOZI_DF', () => {
    const q = parseQname('db.dbo.QZBOZI_DF')!;
    expect(qnameToString(q)).toBe('db.dbo.QZBOZI_DF');
  });

  it('round-trips cnc.role.fact', () => {
    const q = parseQname('cnc.role.fact')!;
    expect(qnameToString(q)).toBe('cnc.role.fact');
  });

  it('omits empty schema segment', () => {
    expect(qnameToString(buildQname('db', '', ['t']))).toBe('db.t');
  });
});
