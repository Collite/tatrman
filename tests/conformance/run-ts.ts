/**
 * Parses every shared fixture with @modeler/parser and writes the normalised
 * JSON dump to `out-ts/<fixture>.json`. The Kotlin `ConformanceSpec` writes the
 * matching dumps; `diff.ts` compares them. Run: `pnpm --filter @modeler/conformance dump`.
 */
import { parseFile } from '@modeler/parser';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { dump } from './dump.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixturesDir = path.join(here, 'fixtures');
const outDir = path.join(here, 'out-ts');

async function main(): Promise<void> {
  await fs.mkdir(outDir, { recursive: true });
  const files = (await fs.readdir(fixturesDir)).filter((f) => f.endsWith('.ttr')).sort();
  for (const f of files) {
    const result = await parseFile(path.join(fixturesDir, f));
    if (result.errors.length > 0) {
      console.error(`✗ ${f}: parse errors`, result.errors);
      process.exitCode = 1;
    }
    const json = dump(result);
    await fs.writeFile(path.join(outDir, f.replace(/\.ttr$/, '.json')), json + '\n');
  }
  console.log(`dumped ${files.length} fixtures to ${path.relative(process.cwd(), outDir)}`);
}

void main();
