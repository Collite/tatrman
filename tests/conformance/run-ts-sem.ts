/**
 * Parses every shared fixture with @modeler/parser + @modeler/semantics and
 * writes the normalised semantics dump to `out-ts-sem/<fixture>.json`. The
 * Kotlin `SemanticsConformanceSpec` writes the matching dumps; `diff-sem.ts`
 * compares them. Run: `pnpm --filter @modeler/conformance dump-sem`.
 */
import { parseFile } from '@modeler/parser';
import { loadStockVocabularies } from '@modeler/semantics/node-only';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { dumpSem, renderSem } from './dump-sem.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixturesDir = path.join(here, 'fixtures');
const outDir = path.join(here, 'out-ts-sem');

async function main(): Promise<void> {
  await fs.mkdir(outDir, { recursive: true });
  const stock = await loadStockVocabularies(['cnc-roles']);
  const files = (await fs.readdir(fixturesDir)).filter((f) => f.endsWith('.ttr')).sort();
  for (const f of files) {
    const result = await parseFile(path.join(fixturesDir, f));
    if (result.errors.length > 0) {
      console.error(`✗ ${f}: parse errors`, result.errors);
      process.exitCode = 1;
    }
    const sem = dumpSem(result.ast, f, stock);
    await fs.writeFile(path.join(outDir, f.replace(/\.ttr$/, '.json')), renderSem(sem) + '\n');
  }
  console.log(`dumped ${files.length} semantics fixtures to ${path.relative(process.cwd(), outDir)}`);
}

void main();
