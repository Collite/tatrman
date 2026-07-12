// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { TableDef } from '../ast.js';

/**
 * `primaryKey` accepts three surface forms (all collapse to the column-name
 * `string[]`): a quoted-string list (legacy), a bare-id list, and a single bare
 * id. Column names are always valid identifiers, so the bare forms are the
 * cleaner authoring style.
 */
function pkOf(src: string): { pk: string[] | undefined; errors: number } {
  const r = parseString(src);
  const table = r.ast?.definitions[0] as TableDef | undefined;
  return { pk: table?.primaryKey, errors: r.errors.filter((e) => e.severity === 'error').length };
}

const wrap = (pk: string, cols = 'def column ID { type: int }') =>
  `model db schema dbo\ndef table T { primaryKey: ${pk}, columns: [ ${cols} ] }`;

describe('primaryKey grammar (string + bare-id variants)', () => {
  it('accepts the legacy quoted-string list', () => {
    expect(pkOf(wrap('["ID"]'))).toEqual({ pk: ['ID'], errors: 0 });
  });

  it('accepts a single bare id (string-less)', () => {
    expect(pkOf(wrap('ID'))).toEqual({ pk: ['ID'], errors: 0 });
  });

  it('accepts a bare-id list', () => {
    expect(pkOf(wrap('[ID]'))).toEqual({ pk: ['ID'], errors: 0 });
  });

  it('accepts a multi-column bare-id list', () => {
    const cols = 'def column ID { type: int }, def column KOD { type: text }';
    expect(pkOf(wrap('[ID, KOD]', cols))).toEqual({ pk: ['ID', 'KOD'], errors: 0 });
  });

  it('accepts an accented bare id (Latin-Extended)', () => {
    const r = parseString('model db schema dbo\ndef table T { primaryKey: id_dokladů, columns: [ def column id_dokladů { type: int } ] }');
    expect((r.ast?.definitions[0] as TableDef).primaryKey).toEqual(['id_dokladů']);
    expect(r.errors.filter((e) => e.severity === 'error')).toHaveLength(0);
  });

  it('a bare-id form is indistinguishable from the quoted form in the AST', () => {
    expect(pkOf(wrap('[ID]')).pk).toEqual(pkOf(wrap('["ID"]')).pk);
  });
});
