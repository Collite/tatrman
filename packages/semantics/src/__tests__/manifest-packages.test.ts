// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  parseManifest,
  resolveManifest,
  resolvePackagesConfig,
  defaultPackagesConfig,
} from '../manifest.js';

describe('PD1.1 — [packages] config loader', () => {
  it('defaults to root="" and layout="flexible" when no [packages] block', () => {
    const m = resolveManifest(parseManifest('[project]\nname = "x"\n'), '/proj');
    expect(m.packages).toEqual(defaultPackagesConfig);
  });

  it('parses root and layout', () => {
    const m = resolveManifest(
      parseManifest('[packages]\nroot = "com.tatrman"\nlayout = "strict"\n'),
      '/proj'
    );
    expect(m.packages).toEqual({ root: 'com.tatrman', layout: 'strict' });
  });

  it('accepts all three layout values', () => {
    for (const layout of ['flexible', 'strict', 'off'] as const) {
      const { config, diagnostics } = resolvePackagesConfig({ layout });
      expect(config.layout).toBe(layout);
      expect(diagnostics).toHaveLength(0);
    }
  });

  it('rejects an unknown layout value with a config diagnostic and falls back to flexible', () => {
    const { config, diagnostics } = resolvePackagesConfig({ root: 'a', layout: 'lenient' });
    expect(config).toEqual({ root: 'a', layout: 'flexible' });
    expect(diagnostics).toHaveLength(1);
    expect(diagnostics[0].field).toBe('layout');
    expect(diagnostics[0].message).toContain('lenient');
  });

  it('root defaults to "" when omitted but layout is set', () => {
    const { config, diagnostics } = resolvePackagesConfig({ layout: 'off' });
    expect(config).toEqual({ root: '', layout: 'off' });
    expect(diagnostics).toHaveLength(0);
  });
});
