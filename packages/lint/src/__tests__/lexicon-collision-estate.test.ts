// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DiagnosticCode } from '@tatrman/parser';
import { lintProj, type ProjectFile } from './helpers.js';

// MH T1 — ONE fixture, TWO twins. The Kotlin compiler asserts exactly one `RG-LEXC-004`
// over this estate (`EstateBuildSpec`, "the estate's deliberate homonym is reported once");
// this asserts the lint reports the same single collision, for the same term and the same
// pair of refs. The parity is the point: the two implementations are one rule, and a
// fixture only one of them reads would let them drift.
//
// The fixture is read from the compiler module's test resources rather than copied — a copy
// is exactly how the two sides stop agreeing.

const ESTATE = fileURLToPath(
  new URL('../../../kotlin/ttr-lexicon-compile/src/test/resources/estate/model', import.meta.url)
);

function ttrmFiles(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...ttrmFiles(p));
    else if (name.endsWith('.ttrm')) out.push(p);
  }
  return out;
}

describe('MH T1 — the shared collision fixture, lint side', () => {
  it('reports exactly one collision over the compiler’s own estate fixture', () => {
    if (!existsSync(ESTATE)) {
      // The two modules live in one repo; if that stops being true, say so loudly rather
      // than passing a test that checked nothing.
      throw new Error(`the shared MH fixture estate is missing: ${ESTATE}`);
    }

    const files: ProjectFile[] = ttrmFiles(ESTATE).map((path) => ({
      uri: `file:///${relative(ESTATE, path).split('\\').join('/')}`,
      src: readFileSync(path, 'utf8'),
    }));
    expect(files.length).toBeGreaterThan(0);

    const diags = [...lintProj(files).values()]
      .flat()
      .filter((d) => d.code === DiagnosticCode.LexiconFormCollidesWithName);

    expect(diags).toHaveLength(1);
    expect(diags[0].message).toContain('term "store_channel_cs" form "prodejna" (for: er.entity.store_sales)');
    expect(diags[0].message).toContain('displayLabel.cs anchor "Prodejna" of er.entity.store');
    expect(diags[0].source.file).toBe('file:///lexicon/cs/measures.ttrm');

    // The md dimension's value label "Prodejna" (`Channel.channelCode."1"`) claims the same
    // word and is deliberately NOT reported: a member is an `M:` identity at runtime — the
    // same boundary the compiler draws by skipping MEMBER rows.
    expect(diags[0].message).not.toContain('channelCode');
  });
});
