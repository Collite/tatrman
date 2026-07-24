// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';
import { parseString } from '../index.js';
import type {
  GrantStatement,
  OwnStatement,
  ClassifyStatement,
  MaskStatement,
} from '../index.js';

// PL-P4.S3 (grammar 0.11) — the document-level `security { … }` block. Structured
// (own / classify / grant / mask), NOT a free-form object bag — so an unknown verb
// and a row-level predicate are hard parse errors (contracts §11). Cross-references
// (`sales`, `order_line.customer_email`, roles) are kept as opaque strings here;
// resolution is ttr-semantics' job (advisory only — S3.T6).
describe('security block (grammar 0.11)', () => {
  const src = [
    'model db schema dbo',
    'def table sales { columns: [ def column region { type: text } ] }',
    'def table order_line { columns: [ def column customer_email { type: text } ] }',
    'security {',
    '  own      sales: team_sales',
    '  classify order_line.customer_email: pii',
    '  grant    read on sales to accounting',
    '  mask     order_line.customer_email',
    '}',
  ].join('\n');

  it('parses cleanly and lands one block with four statements', () => {
    const r = parseString(src);
    expect(r.errors).toHaveLength(0);
    expect(r.ast!.securityBlocks).toHaveLength(1);
    expect(r.ast!.securityBlocks[0]!.statements).toHaveLength(4);
  });

  it('captures each verb with opaque object/role references', () => {
    const stmts = parseString(src).ast!.securityBlocks[0]!.statements;
    const own = stmts[0] as OwnStatement;
    expect(own.verb).toBe('own');
    expect(own.objectRef).toBe('sales');
    expect(own.owner).toBe('team_sales');

    const classify = stmts[1] as ClassifyStatement;
    expect(classify.verb).toBe('classify');
    expect(classify.objectRef).toBe('order_line.customer_email'); // dotted id kept opaque
    expect(classify.classification).toBe('pii');

    const grant = stmts[2] as GrantStatement;
    expect(grant.verb).toBe('grant');
    expect(grant.privilege).toBe('read');
    expect(grant.objectRef).toBe('sales');
    expect(grant.grantee).toBe('accounting');

    const mask = stmts[3] as MaskStatement;
    expect(mask.verb).toBe('mask');
    expect(mask.objectRef).toBe('order_line.customer_email');
  });

  it('carries an accurate source location on the block', () => {
    const block = parseString(src).ast!.securityBlocks[0]!;
    expect(block.source.line).toBe(4); // block opens on line 4 (1-indexed)
    expect(block.source.endColumn).toBeGreaterThan(block.source.column);
    expect(block.source.offsetEnd).toBeGreaterThan(block.source.offsetStart);
  });

  it('supports several blocks in one document', () => {
    const two = [
      'model db schema dbo',
      'def table sales { columns: [ def column region { type: text } ] }',
      'security { own sales: team_sales }',
      'security { mask sales }',
    ].join('\n');
    expect(parseString(two).ast!.securityBlocks).toHaveLength(2);
  });

  // Parser-reject roster (mirrors the Kotlin SecurityNegativeSpec).
  describe('negative roster (security-negative/)', () => {
    const here = dirname(fileURLToPath(import.meta.url));
    const dir = resolve(here, '../../../../tests/conformance/fixtures/security-negative');
    for (const name of readdirSync(dir).filter((f) => f.endsWith('.ttrm'))) {
      it(`${name} is rejected`, () => {
        const r = parseString(readFileSync(resolve(dir, name), 'utf-8'), name);
        expect(r.errors.filter((e) => e.severity === 'error').length).toBeGreaterThan(0);
      });
    }
  });
});
