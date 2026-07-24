// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  SEMANTICS_VOCABULARY_VERSION,
  ENTITY_KINDS,
  ATTRIBUTE_ROLES,
  KIND_COMPLETENESS,
  ALL_ROLES,
} from '../index.js';

// Drift guard — these rosters are the cross-repo contract with ai-platform's
// closed proto enums (feature-grounding-contracts.md §4). A failing assertion is
// the reminder that the proto enums + SEMANTICS_VOCABULARY_VERSION move together.
describe('semantics vocabulary (grammar 4.2 / ttr-semantics v2)', () => {
  it('is version 2', () => {
    expect(SEMANTICS_VOCABULARY_VERSION).toBe(2);
  });

  it('has exactly the four entity/table kinds', () => {
    expect([...ENTITY_KINDS].sort()).toEqual(['calendar', 'fx_rate', 'period_table', 'poi']);
  });

  it('has exactly the twenty-three attribute/column roles (v2 adds the journal-role family)', () => {
    expect([...ALL_ROLES].sort()).toEqual(
      [
        'amount',
        'amount_domestic',
        'authored_by',
        'calendar_date',
        'currency_code',
        'document_date',
        'due_date',
        'event_date',
        'fx_from_currency',
        'fx_rate',
        'fx_to_currency',
        'geo_lat',
        'geo_lon',
        'geo_point',
        'period_code',
        'period_end',
        'period_start',
        'posting_date',
        'valid_flag',
        'valid_from',
        'valid_to',
        'version',
        'written_at',
      ].sort(),
    );
  });

  it('pins each role type-constraint and extra-key set', () => {
    expect(ATTRIBUTE_ROLES.amount.typeConstraint).toBe('numeric');
    expect(ATTRIBUTE_ROLES.amount.extraKeys.map((k) => k.key)).toEqual(['currency']);
    expect(ATTRIBUTE_ROLES.period_code.typeConstraint).toBe('text');
    expect(ATTRIBUTE_ROLES.period_code.extraKeys.map((k) => k.key)).toEqual(['code_format']);
    expect(ATTRIBUTE_ROLES.event_date.typeConstraint).toBe('date');
    expect(ATTRIBUTE_ROLES.event_date.extraKeys.map((k) => k.key)).toEqual(['period']);
    expect(ATTRIBUTE_ROLES.geo_lat.typeConstraint).toBe('numeric');
    expect(ATTRIBUTE_ROLES.geo_point.typeConstraint).toBe('text');
  });

  it('pins the journal-role family type-constraints (S5C-B.4, R30)', () => {
    expect(ATTRIBUTE_ROLES.version.typeConstraint).toBe('numeric');
    expect(ATTRIBUTE_ROLES.authored_by.typeConstraint).toBe('text');
    expect(ATTRIBUTE_ROLES.written_at.typeConstraint).toBe('date');
    // valid_flag is boolean — no numeric/text/date family, left unconstrained.
    expect(ATTRIBUTE_ROLES.valid_flag.typeConstraint).toBeUndefined();
  });

  it('pins the kind completeness clauses', () => {
    expect(KIND_COMPLETENESS.period_table.map((c) => c.role)).toEqual(['period_start', 'period_end', 'period_code']);
    expect(KIND_COMPLETENESS.calendar.map((c) => c.role)).toEqual(['calendar_date']);
    expect(KIND_COMPLETENESS.fx_rate.map((c) => c.role)).toEqual(['fx_from_currency', 'fx_to_currency', 'fx_rate']);
    // poi is the geo_point XOR lat/lon special case — no flat clauses.
    expect(KIND_COMPLETENESS.poi).toEqual([]);
  });
});
