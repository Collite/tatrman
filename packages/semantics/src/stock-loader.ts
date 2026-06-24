import { parseString, type Document } from '@modeler/parser';
import { readFile } from 'fs/promises';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

// import.meta.url is in dist/; PKG_ROOT/.. is the package directory.
// Stock .ttrm files live in `src/stock/` and are copied to `dist/stock/` if
// the build pipeline ships them; falls back to `src/stock/` for local dev.
const HERE = dirname(fileURLToPath(import.meta.url));
const PKG_ROOT = join(HERE, '..');
const STOCK_DIRS = [
  join(HERE, 'stock'),       // dist/stock (if shipped)
  join(PKG_ROOT, 'src', 'stock'), // src/stock (workspace dev)
  join(PKG_ROOT, 'stock'),   // legacy location
];

async function readFirstExisting(name: string): Promise<string | undefined> {
  for (const dir of STOCK_DIRS) {
    try {
      return await readFile(join(dir, `${name}.ttrm`), 'utf-8');
    } catch {
      // try the next candidate
    }
  }
  return undefined;
}

/**
 * Load the named stock vocabularies (e.g. `['cnc-roles']`). Returns a map
 * from vocab name to parsed `Document`. Vocabularies that can't be located
 * or fail to parse are silently skipped.
 */
export async function loadStockVocabularies(names: string[]): Promise<Map<string, Document>> {
  const results = new Map<string, Document>();

  for (const name of names) {
    const content = await readFirstExisting(name);
    if (!content) continue;
    const parsed = parseString(content, `stock://${name}.ttrm`);
    if (parsed.ast && parsed.errors.length === 0) {
      results.set(name, parsed.ast);
    }
  }

  return results;
}
