// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

// Phase 0 Stage A — `schema map` → `model binding`.
// `binding` is the cross-model mapping schema code; `map` is removed from the
// schemaCode alternation (reserved for the future MD `def map` value-set).

describe('Phase 0 — model binding', () => {
  it('parses `model binding` + an er2db_* def with zero diagnostics', () => {
    const { ast, errors } = parseString(
      'model binding\ndef er2db_entity e { entity: er.entity.x, target: { table: db.dbo.t } }',
      'file:///b.ttrm'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.modelDirective?.modelCode).toBe('binding');
  });

  it('accepts an explicit schema on the binding schema', () => {
    const { ast, errors } = parseString(
      'model binding schema er2db\ndef er2db_entity e { entity: er.entity.x, target: db.table }',
      'file:///b.ttrm'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.modelDirective?.modelCode).toBe('binding');
    expect(ast?.modelDirective?.schema).toBe('er2db');
  });

  it('rejects `schema map` — `map` is no longer a valid schema code', () => {
    const { errors } = parseString('schema map\ndef er2db_entity e { }', 'file:///m.ttrm');
    expect(errors.length).toBeGreaterThan(0);
  });

  it('keeps `map` usable as an identifier fragment in cross-references', () => {
    const { ast, errors } = parseString(
      'model er schema entity\ndef entity map { attributes: [def attribute id { type: int }] }',
      'file:///x.ttrm'
    );
    expect(errors).toHaveLength(0);
    expect(ast?.definitions[0].name).toBe('map');
  });
});
