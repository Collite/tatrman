// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { TableDef } from '../index.js';

/**
 * EN-P1 (grammar 0.10) — TTR-M entry declarations parse onto the `table` AST in the TS parser, in
 * parity with the Kotlin walker (`EntryDeclarationsParseSpec`): `management`, `changeSemantics: <mode>
 * { <role>: <column> }`, and the Q-8 `writeback { … }` reservation. The parser stays mechanical —
 * vocabulary + role checks are semantic.
 */
function table(src: string, name: string): TableDef {
  const r = parseString(`model db\n${src}`);
  expect(r.errors).toHaveLength(0);
  return r.ast!.definitions.find((d) => d.kind === 'table' && d.name === name) as TableDef;
}

describe('entry declarations (grammar 0.10)', () => {
  it('management: canon lands on the table', () => {
    expect(table('def table ref_region { management: canon }', 'ref_region').management).toBe('canon');
  });

  it('changeSemantics: scd2 with a valid-from/valid-to role map', () => {
    const cs = table(
      'def table dim_customer { changeSemantics: scd2 { validFrom: valid_from, validTo: valid_to }, ' +
        'columns: [ def column valid_from { type: date }, def column valid_to { type: date } ] }',
      'dim_customer'
    ).changeSemantics;
    expect(cs?.mode).toBe('scd2');
    expect(cs?.roles).toEqual({ validFrom: 'valid_from', validTo: 'valid_to' });
  });

  it('changeSemantics: ledger with a reversal-link role', () => {
    const cs = table(
      'def table txn_book { changeSemantics: ledger { reversalLink: reversal_of } }',
      'txn_book'
    ).changeSemantics;
    expect(cs?.mode).toBe('ledger');
    expect(cs?.roles).toEqual({ reversalLink: 'reversal_of' });
  });

  it('changeSemantics: scd1 with no role map', () => {
    const cs = table('def table ref { changeSemantics: scd1 }', 'ref').changeSemantics;
    expect(cs?.mode).toBe('scd1');
    expect(cs?.roles).toEqual({});
  });

  it('a table with no entry declarations carries undefined (default posture)', () => {
    const t = table('def table plain { description: "x" }', 'plain');
    expect(t.management).toBeUndefined();
    expect(t.changeSemantics).toBeUndefined();
    expect(t.writeback).toBeUndefined();
  });

  it('the Q-8 writeback reservation parses as a structured no-op', () => {
    const t = table('def table q8 { writeback { mapping: valid_from } }', 'q8');
    expect(Object.keys(t.writeback!.entries)).toEqual(['mapping']);
  });
});
