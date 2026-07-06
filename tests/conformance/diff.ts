/**
 * Byte-compares the TS dumps (`out-ts/`) against the Kotlin dumps
 * (`packages/kotlin/ttr-parser/build/conformance/kt/`) per fixture. Exit 0 on
 * full match, 1 on any drift (with a first-divergent-line report).
 * Run: `pnpm --filter @tatrman/conformance diff` (after `dump` + the Kotlin
 * `ConformanceSpec`).
 */
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const tsDir = path.join(here, 'out-ts');
const ktDir = path.resolve(here, '../../packages/kotlin/ttr-parser/build/conformance/kt');

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
    console.error('no dumps found — run the TS `dump` and the Kotlin `ConformanceSpec` first');
    process.exit(1);
  }
  let drift = 0;
  for (const f of [...all].sort()) {
    const ts = await read(path.join(tsDir, f));
    const kt = await read(path.join(ktDir, f));
    if (ts === null) {
      console.error(`✗ ${f}: missing TS dump`);
      drift++;
    } else if (kt === null) {
      console.error(`✗ ${f}: missing Kotlin dump`);
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
    console.error(`\n${drift} fixture(s) drifted between TS and Kotlin.`);
    process.exit(1);
  }
  console.log(`\nAll ${all.size} fixtures match across TS and Kotlin.`);
}

void main();
