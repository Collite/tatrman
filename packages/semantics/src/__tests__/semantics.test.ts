// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseManifest, resolveManifest } from '../manifest.js';

describe('manifest', () => {
  it('parseManifest parses valid TOML', () => {
    const result = parseManifest('[project]\nname = "test-project"\n');
    expect(result.project?.name).toBe('test-project');
  });

  it('resolveManifest applies defaults', () => {
    const resolved = resolveManifest(undefined, '/some/path');
    expect(resolved.name).toBe('path');
    expect(resolved.preferredLanguage).toBe('en');
    expect(resolved.declaredSchemas).toEqual(['db', 'er', 'binding', 'query', 'cnc']);
    expect(resolved.stockVocabularies).toEqual(['cnc-roles']);
    expect(resolved.lint.strict).toBe(false);
    expect(resolved.lint.requireDescriptions).toBe(false);
  });

  it('resolveManifest uses manifest values', () => {
    const resolved = resolveManifest(
      { project: { name: 'my-project' }, language: { preferred: 'cs' }, schemas: { declared: ['db', 'er'] }, lint: { strict: true } },
      '/some/path'
    );
    expect(resolved.name).toBe('my-project');
    expect(resolved.preferredLanguage).toBe('cs');
    expect(resolved.declaredSchemas).toEqual(['db', 'er']);
    expect(resolved.lint.strict).toBe(true);
  });
});