/**
 * Parses every shared fixture with @tatrman/parser + @tatrman/semantics and
 * writes the normalised semantics dump to `out-ts-sem/<fixture>.json`. The
 * Kotlin `SemanticsConformanceSpec` writes the matching dumps; `diff-sem.ts`
 * compares them. Run: `pnpm --filter @tatrman/conformance dump-sem`.
 */
import { parseFile } from '@tatrman/parser';
import { loadStockVocabularies } from '@tatrman/semantics/node-only';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { dumpSem, dumpSemDocs, renderSem, type SemDocInput } from './dump-sem.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixturesDir = path.join(here, 'fixtures');
const outDir = path.join(here, 'out-ts-sem');

async function main(): Promise<void> {
  await fs.mkdir(outDir, { recursive: true });
  const stock = await loadStockVocabularies(['cnc-roles']);
  const entries = await fs.readdir(fixturesDir, { withFileTypes: true });

  // Single-file fixtures: one `.ttrm` → one single-document dump.
  const files = entries.filter((e) => e.isFile() && e.name.endsWith('.ttrm')).map((e) => e.name).sort();
  for (const f of files) {
    const result = await parseFile(path.join(fixturesDir, f));
    if (result.errors.length > 0) {
      console.error(`✗ ${f}: parse errors`, result.errors);
      process.exitCode = 1;
    }
    const sem = dumpSem(result.ast, f, stock);
    await fs.writeFile(path.join(outDir, f.replace(/\.ttrm$/, '.json')), renderSem(sem) + '\n');
  }

  // Multi-document scenarios: each subdirectory bundles several `.ttrm` files
  // loaded into one project symbol table → one `<dir>.json` dump. This is how
  // cross-file resolution (same-package, named/wildcard imports) is exercised.
  const dirs = entries.filter((e) => e.isDirectory()).map((e) => e.name).sort();
  for (const dir of dirs) {
    const dirPath = path.join(fixturesDir, dir);
    const subFiles = (await fs.readdir(dirPath)).filter((f) => f.endsWith('.ttrm')).sort();
    const docs: SemDocInput[] = [];
    for (const sf of subFiles) {
      const result = await parseFile(path.join(dirPath, sf));
      if (result.errors.length > 0) {
        console.error(`✗ ${dir}/${sf}: parse errors`, result.errors);
        process.exitCode = 1;
      }
      if (result.ast) docs.push({ ast: result.ast, uri: `${dir}/${sf}` });
    }
    const sem = dumpSemDocs(docs, stock);
    await fs.writeFile(path.join(outDir, `${dir}.json`), renderSem(sem) + '\n');
  }

  console.log(`dumped ${files.length} single-file + ${dirs.length} multi-doc semantics fixtures to ${path.relative(process.cwd(), outDir)}`);
}

void main();
