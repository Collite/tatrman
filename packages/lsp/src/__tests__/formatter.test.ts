import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { formatDocument, DEFAULT_FORMAT_CONFIG, type FormatConfig } from '../formatter/format.js';

function fmt(src: string, cfg: Partial<FormatConfig> = {}): string {
  const r = parseString(src, 'test.ttr');
  if (!r.ast) throw new Error('parse failed: ' + JSON.stringify(r.errors.slice(0, 2)));
  return formatDocument(r.ast, src, { ...DEFAULT_FORMAT_CONFIG, ...cfg });
}

describe('formatter', () => {
  it('idempotent: formatting messy input twice is stable', () => {
    const messy = `package a.b
schema er namespace entity
def entity x {   description: "d",
      attributes: [ def attribute id { type: int, isKey: true } ] }`;
    const once = fmt(messy);
    const twice = fmt(once);
    expect(twice).toBe(once);
  });

  it('indents nested attribute defs (list items at one level, nested def inline)', () => {
    const out = fmt(`package a
schema er namespace entity
def entity x { description: "d", attributes: [ def attribute id { type: int } ] }`, { separator: 'newline' });
    // top-level property at 4 spaces, list item (nested def) at 8 spaces.
    expect(out).toContain('\n    description:');
    expect(out).toContain('\n        def attribute id { type: int }');
  });

  it("separator 'newline' puts each property on its own line", () => {
    const out = fmt(`package a
schema er namespace entity
def entity x { description: "d", labelPlural: "xs" }`, { separator: 'newline' });
    expect(out).toMatch(/description: "d",\n {4}labelPlural: "xs"/);
  });

  it("separator 'comma' forces a single line even when it exceeds the width", () => {
    const out = fmt(`package a
schema er namespace entity
def entity x { description: "d", labelPlural: "xs", nameAttribute: foo }`, { separator: 'comma', width: 20 });
    expect(out).toContain('def entity x { description: "d", labelPlural: "xs", nameAttribute: foo }');
  });

  it('alignKeys aligns property values to a common column', () => {
    const out = fmt(`package a
schema er namespace entity
def entity x { description: "d", nameAttribute: foo }`, { separator: 'newline', alignKeys: true });
    const lines = out.split('\n');
    const descLine = lines.find((l) => l.includes('description:'))!;
    const nameLine = lines.find((l) => l.includes('nameAttribute:'))!;
    // The value (after the padded key) must start at the same column on both lines.
    expect(descLine.indexOf('"d"')).toBe(nameLine.indexOf('foo'));
    // "description" (shorter key) must be padded with extra spaces after its colon.
    expect(descLine).toContain('description:  ');
  });

  it('preserves a triple-string literal verbatim (including its internal newline)', () => {
    const src = `package a
schema er namespace entity
def entity x { description: """line one
line two""" }`;
    const out = fmt(src);
    expect(out).toContain('"""line one\nline two"""');
  });

  it('emits package, imports, schema as leading blocks', () => {
    const out = fmt(`package a.b
import c.d.*
schema er namespace entity
def entity x { description: "d" }`);
    expect(out.startsWith('package a.b\n\nimport c.d.*\n\nschema er namespace entity\n\n')).toBe(true);
  });
});
