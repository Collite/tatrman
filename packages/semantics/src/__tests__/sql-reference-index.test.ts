import { describe, it, expect } from 'vitest';
import { SqlReferenceIndex } from '../sql/reference-index.js';

const loc = (uri: string, line = 0) => ({
  uri,
  range: { start: { line, character: 0 }, end: { line, character: 4 } },
});

describe('SqlReferenceIndex (4.3)', () => {
  it('indexes and queries usages by qname', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttr', [
      { qname: 'db.dbo.users', loc: loc('a.ttr', 1) },
      { qname: 'db.dbo.users.email', loc: loc('a.ttr', 2) },
    ]);
    idx.upsertDocument('b.ttr', [{ qname: 'db.dbo.users', loc: loc('b.ttr', 3) }]);
    expect(idx.findByQname('db.dbo.users').map((l) => l.uri).sort()).toEqual(['a.ttr', 'b.ttr']);
    expect(idx.findByQname('db.dbo.users.email')).toHaveLength(1);
    expect(idx.findByQname('db.dbo.nope')).toEqual([]);
  });

  it('re-upserting a document replaces only its own entries', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttr', [{ qname: 'db.dbo.users', loc: loc('a.ttr') }]);
    idx.upsertDocument('b.ttr', [{ qname: 'db.dbo.users', loc: loc('b.ttr') }]);
    idx.upsertDocument('a.ttr', []); // a.ttr no longer references it
    expect(idx.findByQname('db.dbo.users').map((l) => l.uri)).toEqual(['b.ttr']);
  });

  it('removeDocument drops a document’s usages', () => {
    const idx = new SqlReferenceIndex();
    idx.upsertDocument('a.ttr', [{ qname: 'db.dbo.users', loc: loc('a.ttr') }]);
    idx.removeDocument('a.ttr');
    expect(idx.findByQname('db.dbo.users')).toEqual([]);
  });
});
