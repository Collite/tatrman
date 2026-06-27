import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { dump } from './dump.js';

/**
 * Snapshot-guards the TS dump so accidental format changes are caught locally
 * (independent of the cross-language diff). If a snapshot changes intentionally,
 * the Kotlin `ConformanceDump` must change in lock-step.
 */
describe('conformance dump (TS)', () => {
  it('minimal model matches the §5 schema', () => {
    const r = parseString('def project M { version: "1.2.3" }');
    expect(dump(r)).toMatchInlineSnapshot(`
      "{
          "definitions": [
              {
                  "description": null,
                  "kind": "model",
                  "name": "M",
                  "properties": {
                      "version": "1.2.3"
                  },
                  "tags": []
              }
          ],
          "imports": [],
          "package": null,
          "schemaDirective": null
      }"
    `);
  });

  it('entity with nested attributes, search and displayLabel', () => {
    const src = [
      'model er',
      'def entity Customer {',
      '  labelPlural: "Customers"',
      '  roles: [fact, dimension]',
      '  displayLabel: { cs: "Zákazník", en: "Customer" }',
      '  attributes: [',
      '    def attribute id { type: int, isKey: true },',
      '    def attribute name { type: text, search { searchable: true } }',
      '  ]',
      '}',
    ].join('\n');
    const r = parseString(src);
    expect(r.errors).toEqual([]);
    const out = dump(r);
    // Spot-check the load-bearing normalisations rather than a full snapshot.
    expect(out).toContain('"kind": "entity"');
    expect(out).toContain('"roles": [');
    expect(out).toContain('"displayLabel": {');
    expect(out).toContain('"searchable": true');
    // keys are sorted: description precedes kind precedes name precedes properties
    expect(out.indexOf('"description"')).toBeLessThan(out.indexOf('"kind"'));
  });
});
