import { describe, it, expect } from 'vitest';
import { sqlCompletionContext, sqlScopeFromTokens } from '../sql-features.js';

// offset just past a marker `|` in the string (the marker is removed first).
function at(withCursor: string): { value: string; offset: number } {
  const offset = withCursor.indexOf('|');
  return { value: withCursor.replace('|', ''), offset };
}

describe('sqlCompletionContext (4.4.2) — lexer-first', () => {
  it('FROM → table position', () => {
    const { value, offset } = at('SELECT id FROM |');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'table' });
  });

  it('JOIN → table position', () => {
    const { value, offset } = at('SELECT * FROM a JOIN |');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'table' });
  });

  it('SELECT → column position', () => {
    const { value, offset } = at('SELECT |');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'column' });
  });

  it('WHERE → column position', () => {
    const { value, offset } = at('SELECT * FROM t WHERE |');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'column' });
  });

  it('alias dot → column position qualified by the alias', () => {
    const { value, offset } = at('SELECT o.| FROM Orders o');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'column', qualifier: 'o' });
  });

  it('comma after SELECT stays a column position', () => {
    const { value, offset } = at('SELECT a, |');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'column' });
  });

  it('partial word after FROM is still a table position', () => {
    const { value, offset } = at('SELECT * FROM Ord|');
    expect(sqlCompletionContext(value, 'tsql', offset)).toEqual({ kind: 'table' });
  });
});

describe('sqlScopeFromTokens (4.4.4) — FROM/JOIN scrape', () => {
  it('reads a single table with an alias', () => {
    expect(sqlScopeFromTokens('SELECT  FROM Orders o', 'tsql')).toEqual([{ name: ['Orders'], alias: 'o' }]);
  });

  it('reads comma-separated FROM tables and a JOIN', () => {
    const scope = sqlScopeFromTokens('SELECT * FROM a x, b JOIN dbo.c z ON 1=1', 'tsql');
    expect(scope).toEqual([
      { name: ['a'], alias: 'x' },
      { name: ['b'], alias: undefined },
      { name: ['dbo', 'c'], alias: 'z' },
    ]);
  });

  it('does not treat a clause keyword as an alias', () => {
    expect(sqlScopeFromTokens('SELECT * FROM Orders WHERE x = 1', 'tsql')).toEqual([
      { name: ['Orders'], alias: undefined },
    ]);
  });
});
