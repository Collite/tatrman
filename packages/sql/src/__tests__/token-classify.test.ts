// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { lexSql } from '../lexer-service.js';
import { classifyToken, type SqlSemanticType } from '../token-classify.js';
import type { SqlDialect } from '@tatrman/parser';

/** Classify every token of a line and return [text-ish key, semantic] pairs. */
function classified(sql: string, dialect: SqlDialect): Map<string, SqlSemanticType | null> {
  const { tokens } = lexSql(sql, dialect);
  const m = new Map<string, SqlSemanticType | null>();
  for (const t of tokens) {
    const key = t.literalName ? t.literalName.replace(/^'|'$/g, '') : (t.typeName ?? '?');
    m.set(key, classifyToken(t.typeName, t.literalName));
  }
  return m;
}

describe('classifyToken (LSP semantic-token legend, §7)', () => {
  it('classifies a representative T-SQL line', () => {
    const c = classified("SELECT a, 1, 'x' FROM t WHERE @v = 1 -- c", 'tsql');
    expect(c.get('SELECT')).toBe('keyword');
    expect(c.get('FROM')).toBe('keyword');
    expect(c.get('WHERE')).toBe('keyword');
    expect(c.get(',')).toBe('operator');
    expect(c.get('=')).toBe('operator');
    expect(c.get('DECIMAL')).toBe('number');
    expect(c.get('STRING')).toBe('string');
    expect(c.get('LOCAL_ID')).toBe('variable');
    expect(c.get('LINE_COMMENT')).toBe('comment');
    expect(c.get('ID')).toBeNull(); // plain identifier → no token (Phase 3 classifies)
  });

  it('classifies a representative PostgreSQL line', () => {
    const c = classified("SELECT a, 1, 'x' FROM t WHERE b = 1 -- c", 'postgres');
    expect(c.get('SELECT')).toBe('keyword');
    expect(c.get(',')).toBe('operator');
    expect(c.get('=')).toBe('operator');
    expect(c.get('Integral')).toBe('number');
    expect(c.get('StringConstant')).toBe('string');
    expect(c.get('LineComment')).toBe('comment');
    expect(c.get('Identifier')).toBeNull();
  });

  it('returns null for unknown / whitespace token names', () => {
    expect(classifyToken('Whitespace', null)).toBeNull();
    expect(classifyToken(null, null)).toBeNull();
  });
});
