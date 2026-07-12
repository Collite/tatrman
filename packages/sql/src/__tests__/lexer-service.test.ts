// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { lexSql, resolveDialect } from '../lexer-service.js';

describe('lexSql (dialect lexer service)', () => {
  it('lexes a {param} query with zero errors (the mask did its job)', () => {
    const sql = 'SELECT id, name FROM dbo.Produkt WHERE id = {id_produktu}';
    const { tokens, masked, errors } = lexSql(sql, 'tsql');
    expect(errors).toEqual([]);
    expect(masked.masked).not.toMatch(/[{}]/);
    expect(masked.placeholders).toHaveLength(1);
    // Keyword + identifier tokens are present.
    expect(tokens.some((t) => t.literalName === "'SELECT'")).toBe(true);
    expect(tokens.some((t) => t.typeName === 'ID')).toBe(true);
  });

  it('postgres lexes a {param} query with zero errors', () => {
    const { errors } = lexSql('SELECT a FROM t WHERE a = {p}', 'postgres');
    expect(errors).toEqual([]);
  });

  it('token spans index into the (masked) value', () => {
    const { tokens } = lexSql('SELECT 1', 'tsql');
    const select = tokens[0]!;
    expect(select.span).toEqual({ offset: 0, length: 6, line: 1, column: 0 });
  });

  it('a not-yet-vendored dialect lexes to no tokens (graceful, no throw)', () => {
    const { tokens, errors } = lexSql('SELECT 1', 'mysql');
    expect(tokens).toEqual([]);
    expect(errors).toEqual([]);
  });
});

describe('resolveDialect', () => {
  it("uses the block's resolved dialect when present", () => {
    expect(resolveDialect({ dialect: 'postgres' })).toBe('postgres');
  });

  it('falls back to the config default for a bare `sql` tag (dialect null)', () => {
    expect(resolveDialect({ dialect: null }, { defaultDialect: 'postgres' })).toBe('postgres');
  });

  it('falls back to the hard tsql default when neither is set', () => {
    expect(resolveDialect({ dialect: null })).toBe('tsql');
  });
});
