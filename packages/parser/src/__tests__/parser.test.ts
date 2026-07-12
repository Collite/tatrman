// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString, parseFile } from '../index.js';
import { DiagnosticCode } from '../diagnostics.js';
import { RECOVERY_FIXTURES } from './recovery-fixtures.js';
import path from 'path';
import fs from 'fs/promises';

const samplesDir = path.resolve(__dirname, '../../../../samples');

async function getAllTtrFiles(dir: string, excludeDirs: string[] = []): Promise<string[]> {
  const results: string[] = [];
  const entries = await fs.readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (excludeDirs.includes(entry.name)) continue;
      results.push(...await getAllTtrFiles(fullPath, excludeDirs));
    } else if (entry.isFile() && entry.name.endsWith('.ttrm')) {
      results.push(fullPath);
    }
  }
  return results;
}

describe('parser', () => {
  it('parseString("") returns empty Document with no errors', () => {
    const result = parseString('');
    expect(result.errors).toHaveLength(0);
    expect(result.ast?.definitions).toHaveLength(0);
  });

  it('parseString("model db schema dbo") returns schemaDirective with schemaCode === "db" and schema === "dbo"', () => {
    const result = parseString('model db schema dbo');
    expect(result.errors).toHaveLength(0);
    expect(result.ast?.modelDirective?.modelCode).toBe('db');
    expect(result.ast?.modelDirective?.schema).toBe('dbo');
  });

  it('parseString("model er") (no schema) returns schemaDirective with schemaCode === "er" and no schema', () => {
    const result = parseString('model er');
    expect(result.errors).toHaveLength(0);
    expect(result.ast?.modelDirective?.modelCode).toBe('er');
    expect(result.ast?.modelDirective?.schema).toBeUndefined();
  });

  it('parseString("def project erp_v1 { version: \\"1.0.0\\" }") parses the project header', () => {
    const result = parseString('def project erp_v1 { version: "1.0.0" }');
    expect(result.errors).toHaveLength(0);
    expect(result.ast?.definitions).toHaveLength(1);
    expect(result.ast?.definitions[0].name).toBe('erp_v1');
  });

  // v4.0 — the old keywords are removed (hard cut, D13), not aliased.
  it('rejects the old "schema db namespace dbo" directive form', () => {
    const result = parseString('schema db namespace dbo');
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('rejects the old "def model" definition form', () => {
    const result = parseString('def model erp_v1 { version: "1.0.0" }');
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('parseString("def entity foo {}") returns one Definition with kind === "entity" and name === "foo"', () => {
    const result = parseString('def entity foo {}');
    expect(result.errors).toHaveLength(0);
    expect(result.ast?.definitions).toHaveLength(1);
    expect(result.ast?.definitions[0].kind).toBe('entity');
    expect(result.ast?.definitions[0].name).toBe('foo');
  });

  it('syntax error case returns at least one ParseError with non-zero line/column', () => {
    const result = parseString('def entity {');
    expect(result.errors.length).toBeGreaterThan(0);
    const error = result.errors[0];
    expect(error.severity).toBe('error');
    expect(error.source.line).toBeGreaterThan(0);
    expect(error.source.column).toBeGreaterThan(0);
  });

  it('parseString("def entity foo {}") has correct offsets: offsetStart=0, offsetEnd=17', () => {
    const result = parseString('def entity foo {}');
    expect(result.errors).toHaveLength(0);
    const def = result.ast?.definitions[0];
    expect(def).toBeDefined();
    expect(def!.source.offsetStart).toBe(0);
    expect(def!.source.offsetEnd).toBe(17);
  });

  it('parseString("def entity foobar {}") endColumn is the column after the closing brace', () => {
    const result = parseString('def entity foobar {}');
    expect(result.errors).toHaveLength(0);
    const def = result.ast?.definitions[0];
    expect(def).toBeDefined();
    expect(def!.source.offsetEnd).toBe(20);
    expect(def!.source.endLine).toBe(1);
    expect(def!.source.endColumn).toBe(20);
  });

  it('multi-line def: endLine and endColumn reflect the last token of the span', () => {
    const result = parseString('def entity foo {\n}\n');
    expect(result.errors).toHaveLength(0);
    const def = result.ast?.definitions[0];
    expect(def).toBeDefined();
    expect(def!.source.endLine).toBe(2);
    expect(def!.source.endColumn).toBe(1);
  });
});

describe('parseFile', () => {
  it('parses samples/v1-metadata/er.ttrm with no errors and returns >0 entity definitions', async () => {
    const result = await parseFile(path.join(samplesDir, 'v1-metadata/er.ttrm'));
    expect(result.errors).toHaveLength(0);
    const entities = result.ast?.definitions.filter((d) => d.kind === 'entity') ?? [];
    expect(entities.length).toBeGreaterThan(0);
  });

  it('parses all sample files without errors', async () => {
    const ttrFiles = await getAllTtrFiles(samplesDir, ['broken']);

    for (const file of ttrFiles) {
      const result = await parseFile(file);
      expect(result.errors, `Errors in ${file}: ${result.errors.map((e) => e.message).join(', ')}`).toHaveLength(0);
    }
  });
});

describe('parser error recovery', () => {
  for (const fixture of RECOVERY_FIXTURES) {
    it(`"${fixture.name}": ${fixture.description} — ${
      fixture.expectErrors ? 'produces ttr/parse-error' : 'parses without ttr/parse-error (grammar is permissive)'
    }`, () => {
      const result = parseString(fixture.input);
      if (fixture.expectErrors) {
        expect(result.errors.length, `"${fixture.name}" should have errors`).toBeGreaterThanOrEqual(1);
        const hasParseError = result.errors.some((e) => e.code === DiagnosticCode.ParseError);
        expect(hasParseError, `"${fixture.name}" should have at least one ttr/parse-error`).toBe(true);
      } else {
        const hasParseError = result.errors.some((e) => e.code === DiagnosticCode.ParseError);
        expect(hasParseError).toBe(false);
      }
    });

    it(`"${fixture.name}": ${fixture.description} — produces ${fixture.expectedRecoveredDefs} recovered defs`, () => {
      const result = parseString(fixture.input);
      const defs = result.ast?.definitions ?? [];
      expect(defs.length).toBe(fixture.expectedRecoveredDefs);
    });

    if (fixture.expectErrors) {
      it(`"${fixture.name}": ${fixture.description} — produces at least one ttr/parse-recovery-info`, () => {
        const result = parseString(fixture.input);
        const hasRecoveryInfo = result.errors.some((e) => e.code === DiagnosticCode.ParseRecoveryInfo);
        expect(hasRecoveryInfo, `"${fixture.name}" should have at least one ttr/parse-recovery-info`).toBe(true);
      });
    }
  }
});