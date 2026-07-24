// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { validateSecurityDocument } from '../security-validate.js';

// PL-P4.S3.T6 — advisory `security { }` validation. Mirrors the Kotlin
// SecuritySemanticsSpec case-for-case: object refs resolve (or lint as a WARNING);
// roles are verbatim (never resolved); a violation is NEVER a compile block (H-3).
const MODEL = [
  'model db schema dbo',
  'def table sales { columns: [ def column region { type: text }, def column amount { type: decimal } ] }',
  'def table order_line { columns: [ def column customer_email { type: text } ] }',
].join('\n');

function validate(security: string, known?: Set<string>) {
  const r = parseString(`${MODEL}\n${security}`);
  expect(r.errors.filter((e) => e.severity === 'error')).toHaveLength(0);
  return validateSecurityDocument(r.ast!, known);
}

describe('security block advisory validation (grammar 0.11)', () => {
  it('all object refs resolve → zero diagnostics', () => {
    expect(
      validate(
        'security { own sales: team_sales, classify order_line.customer_email: pii, ' +
          'grant read on sales to accounting, mask order_line.customer_email }',
      ),
    ).toEqual([]);
  });

  it('an unknown object → one security/unresolved-object WARNING (never an error)', () => {
    const d = validate('security { grant read on ghost to accounting }');
    expect(d).toHaveLength(1);
    expect(d[0]!.code).toBe('security/unresolved-object');
    expect(d[0]!.severity).toBe('warning'); // H-3: advisory only
    expect(d[0]!.message).toContain('ghost');
  });

  it('a column-level ref resolves via its owning object head segment', () => {
    expect(validate('security { mask order_line.unknown_col }')).toEqual([]);
  });

  it('unknown role/classification tokens are NOT flagged (verbatim org-policy data)', () => {
    expect(validate('security { grant read on sales to nobody, classify sales.region: ultra_secret }')).toEqual([]);
  });

  it('an injected project-wide object set is honoured for cross-file refs', () => {
    const r = parseString('model db schema dbo\nsecurity { grant read on shop.sales.order to buyers }');
    expect(validateSecurityDocument(r.ast!, new Set(['shop.sales.order']))).toEqual([]);
    expect(validateSecurityDocument(r.ast!, new Set(['other.thing']))).toHaveLength(1);
  });

  it('no security block → no diagnostics', () => {
    expect(validate('')).toEqual([]);
  });
});
