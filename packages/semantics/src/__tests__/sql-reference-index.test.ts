import { describe, it, expect } from 'vitest';
import { SqlReferenceIndex } from '../sql/reference-index.js';

const loc = (uri: string, line = 0) => ({
  uri,
  range: { start: { line, character: 0 }, end: { line, character: 4 } },
});

describe('SqlReferenceIndex (4.3)', () => {
  it('indexes and queries usages by qname', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttrm', [
      { qname: 'db.dbo.users', loc: loc('a.ttrm', 1) },
      { qname: 'db.dbo.users.email', loc: loc('a.ttrm', 2) },
    ]);
    idx.upsertDocument('b.ttrm', [{ qname: 'db.dbo.users', loc: loc('b.ttrm', 3) }]);
    expect(idx.findByQname('db.dbo.users').map((l) => l.uri).sort()).toEqual(['a.ttrm', 'b.ttrm']);
    expect(idx.findByQname('db.dbo.users.email')).toHaveLength(1);
    expect(idx.findByQname('db.dbo.nope')).toEqual([]);
  });

  it('re-upserting a document replaces only its own entries', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttrm', [{ qname: 'db.dbo.users', loc: loc('a.ttrm') }]);
    idx.upsertDocument('b.ttrm', [{ qname: 'db.dbo.users', loc: loc('b.ttrm') }]);
    idx.upsertDocument('a.ttrm', []); // a.ttrm no longer references it
    expect(idx.findByQname('db.dbo.users').map((l) => l.uri)).toEqual(['b.ttrm']);
  });

  it('removeDocument drops a document’s usages', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttrm', [{ qname: 'db.dbo.users', loc: loc('a.ttrm') }]);
    idx.removeDocument('a.ttrm');
    expect(idx.findByQname('db.dbo.users')).toEqual([]);
  });
});
