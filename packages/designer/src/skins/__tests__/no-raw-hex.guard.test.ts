// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 P1 (contracts §6, §8) — the `no-raw-hex` guard. The canvas + skins render
// through `@tatrman/tokens` (the `canvas` family), never raw hex: a raw literal here
// is un-audited colour that escapes the palette-stability contract. This walks the
// live source at test time and fails with a `file:line: <hex>` listing of every
// offender, so the fix is always a token, never an allowlist (task 1.6).

import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

// `vitest run` executes from the package root (packages/designer), so anchor there
// rather than import.meta.url (not a file:// scheme under the jsdom environment).
const SRC_ROOT = join(process.cwd(), 'src');
const SKINS_DIR = join(SRC_ROOT, 'skins');
const CANVAS_DIR = join(SRC_ROOT, 'canvas');

const HEX = /#[0-9a-fA-F]{3,8}\b/g;
const CODE_EXT = /\.(ts|tsx|css)$/;

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    if (entry === '__tests__' || entry === 'node_modules') continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else if (CODE_EXT.test(entry)) out.push(full);
  }
  return out;
}

function offenders(dir: string): string[] {
  const hits: string[] = [];
  for (const file of walk(dir)) {
    const lines = readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, i) => {
      const matches = line.match(HEX);
      if (matches) {
        for (const m of matches) hits.push(`${relative(SRC_ROOT, file)}:${i + 1}: ${m}`);
      }
    });
  }
  return hits;
}

describe('no-raw-hex guard — skins + canvas consume tokens only (contracts §6)', () => {
  it('has zero raw hex literals under designer/src/skins', () => {
    const hits = offenders(SKINS_DIR);
    expect(hits, `raw hex in skins (migrate to @tatrman/tokens canvas family):\n${hits.join('\n')}`).toEqual([]);
  });

  it('has zero raw hex literals under designer/src/canvas', () => {
    const hits = offenders(CANVAS_DIR);
    expect(hits, `raw hex in canvas (migrate to @tatrman/tokens canvas family):\n${hits.join('\n')}`).toEqual([]);
  });
});
