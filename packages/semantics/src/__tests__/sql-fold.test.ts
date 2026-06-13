import { describe, it, expect } from 'vitest';
import { foldEq, foldIdent } from '../sql/fold.js';

describe('SQL identifier folding (3.4.2, contracts §6.2)', () => {
  it('tsql is case-insensitive: Users == users', () => {
    expect(foldEq('Users', 'users', 'tsql')).toBe(true);
    expect(foldEq('ORDERS', 'orders', 'tsql')).toBe(true);
  });

  it('postgres folds unquoted to lower-case: Users == users', () => {
    expect(foldEq('Users', 'users', 'postgres')).toBe(true);
    expect(foldIdent('MixedCase', 'postgres')).toBe('mixedcase');
  });

  it('duckdb is case-insensitive for comparison', () => {
    expect(foldEq('Customer', 'CUSTOMER', 'duckdb')).toBe(true);
  });

  it('distinguishes genuinely different names', () => {
    expect(foldEq('Orders', 'Customers', 'tsql')).toBe(false);
    expect(foldEq('id', 'name', 'postgres')).toBe(false);
  });
});
