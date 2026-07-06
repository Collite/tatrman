/**
 * Parses every shared fixture with @tatrman/parser and writes the normalised
 * JSON dump to `out-ts/<fixture>.json`. The Kotlin `ConformanceSpec` writes the
 * matching dumps; `diff.ts` compares them. Run: `pnpm --filter @tatrman/conformance dump`.
 */
import { parseFile } from '@tatrman/parser';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { dump } from './dump.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixturesDir = path.join(here, 'fixtures');
const outDir = path.join(here, 'out-ts');

async function main(): Promise<void> {
  await fs.mkdir(outDir, { recursive: true });
  const files = (await fs.readdir(fixturesDir)).filter((f) => f.endsWith('.ttrm')).sort();
  for (const f of files) {
    const result = await parseFile(path.join(fixturesDir, f));
    // Only error-severity diagnostics are fatal here. The TS parser folds
    // warnings (e.g. the unknown-language-tag on 48) into `errors` with a
    // `severity`, whereas the Kotlin `ConformanceSpec` keeps them in a separate
    // `warnings` list; filtering by severity keeps the two harnesses symmetric.
    const fatal = result.errors.filter((e) => e.severity === 'error');
    if (fatal.length > 0) {
      console.error(`✗ ${f}: parse errors`, fatal);
      process.exitCode = 1;
    }
    const json = dump(result);
    await fs.writeFile(path.join(outDir, f.replace(/\.ttrm$/, '.json')), json + '\n');
  }
  console.log(`dumped ${files.length} fixtures to ${path.relative(process.cwd(), outDir)}`);
}

void main();
