/**
 * Byte-compares the TS semantics dumps (`out-ts-sem/`) against the Kotlin
 * semantics dumps (`packages/kotlin/ttr-semantics/build/conformance/kt-sem/`)
 * per fixture. Exit 0 on full match, 1 on any drift (with a first-divergent-line
 * report). Run: `pnpm --filter @modeler/conformance diff-sem` (after `dump-sem`
 * and the Kotlin `SemanticsConformanceSpec`).
 */
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const tsDir = path.join(here, 'out-ts-sem');
const ktDir = path.resolve(here, '../../packages/kotlin/ttr-semantics/build/conformance/kt-sem');

async function read(p: string): Promise<string | null> {
  try {
    return await fs.readFile(p, 'utf8');
  } catch {
    return null;
  }
}

async function list(dir: string): Promise<string[]> {
  try {
    return (await fs.readdir(dir)).filter((f) => f.endsWith('.json')).sort();
  } catch {
    return [];
  }
}

function reportDiff(ts: string, kt: string): void {
  const a = ts.split('\n');
  const b = kt.split('\n');
  const n = Math.max(a.length, b.length);
  for (let i = 0; i < n; i++) {
    if (a[i] !== b[i]) {
      console.error(`    first diff at line ${i + 1}:`);
      console.error(`      ts: ${a[i] ?? '<eof>'}`);
      console.error(`      kt: ${b[i] ?? '<eof>'}`);
      return;
    }
  }
}

async function main(): Promise<void> {
  const all = new Set([...(await list(tsDir)), ...(await list(ktDir))]);
  if (all.size === 0) {
    console.error('no semantics dumps found — run the TS `dump-sem` and the Kotlin `SemanticsConformanceSpec` first');
    process.exit(1);
  }
  let drift = 0;
  for (const f of [...all].sort()) {
    const ts = await read(path.join(tsDir, f));
    const kt = await read(path.join(ktDir, f));
    if (ts === null) {
      console.error(`✗ ${f}: missing TS semantics dump`);
      drift++;
    } else if (kt === null) {
      console.error(`✗ ${f}: missing Kotlin semantics dump`);
      drift++;
    } else if (ts !== kt) {
      console.error(`✗ ${f}: DRIFT`);
      reportDiff(ts, kt);
      drift++;
    } else {
      console.log(`✓ ${f}`);
    }
  }
  if (drift > 0) {
    console.error(`\n${drift} fixture(s) drifted between TS and Kotlin semantics.`);
    process.exit(1);
  }
  console.log(`\nAll ${all.size} fixtures match across TS and Kotlin semantics.`);
}

void main();
