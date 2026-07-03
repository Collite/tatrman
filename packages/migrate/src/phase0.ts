/**
 * Phase 0 (grammar 3.0) migration helper. Given the files of a pre-3.0 project,
 * produces the writes + deletes that upgrade it to the 3.0 conventions:
 *
 *   1. `*.ttr`  → `*.ttrm`               (model file extension)
 *   2. `*.ttrd` → `*.ttrm`, `domain <id> { … }` → `def area <id> { … }`
 *   3. `schema map`     → `schema binding`   (cross-model mapping schema code)
 *   4. inline `mapping:` / `mapping {` → `binding:` / `binding {`
 *
 * `.ttrg` graph files keep their extension; their content is still rewritten for
 * the `schema map → binding` rename (a graph may render the binding schema).
 *
 * Pure and deterministic — no filesystem access (see `runPhase0` for the CLI
 * driver). Re-running over already-migrated `.ttrm` files is a no-op.
 */
import { rewriteV4Keywords } from './keyword-rewrite.js';

export interface Phase0File {
  path: string;
  text: string;
}

export interface Phase0Result {
  /** Files to write (renamed and/or content-rewritten). */
  writes: Phase0File[];
  /** Old paths to delete (the `.ttr` / `.ttrd` originals that were renamed). */
  deletes: string[];
}

/** Schema-code + inline-property keyword rewrites (text-level, order-independent). */
function rewriteContent(text: string): string {
  const mapped = text
    // `schema map` directive and `schema: map` graph property → binding code.
    .replace(/schema([ \t]*:?[ \t]+)map\b/g, 'schema$1binding')
    // inline `mapping:` / `mapping {` property keyword → binding.
    .replace(/\bmapping(\s*[:{])/g, 'binding$1');
  // v4.0 keyword rename (shared with `migrate-qnames` — the single source).
  return rewriteV4Keywords(mapped);
}

/** Convert the single top-level `domain <id> { … }` block opener to `def area <id> {`. */
function domainToArea(text: string): string {
  return text.replace(/(^|\n)([ \t]*)domain([ \t]+[^\s{]+[ \t]*\{)/, '$1$2def area$3');
}

export function migratePhase0(files: Phase0File[]): Phase0Result {
  const writes: Phase0File[] = [];
  const deletes: string[] = [];

  for (const f of files) {
    if (f.path.endsWith('.ttrd')) {
      writes.push({ path: f.path.replace(/\.ttrd$/, '.ttrm'), text: domainToArea(rewriteContent(f.text)) });
      deletes.push(f.path);
    } else if (f.path.endsWith('.ttr')) {
      writes.push({ path: f.path.replace(/\.ttr$/, '.ttrm'), text: rewriteContent(f.text) });
      deletes.push(f.path);
    } else if (f.path.endsWith('.ttrg')) {
      const text = rewriteContent(f.text);
      if (text !== f.text) writes.push({ path: f.path, text });
    }
  }

  return { writes, deletes };
}

/** Filesystem driver for the `modeler phase0` CLI command. */
export async function runPhase0(
  projectRoot: string,
  opts: { dryRun?: boolean } = {}
): Promise<Phase0Result> {
  const { readFile, writeFile, rm, mkdir } = await import('node:fs/promises');
  const { join, dirname } = await import('node:path');
  const { readdir } = await import('node:fs/promises');

  const files: Phase0File[] = [];
  async function walk(dir: string): Promise<void> {
    let entries;
    try {
      entries = await readdir(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const entry of entries) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === '.modeler' || entry.name === 'node_modules' || entry.name === '.git') continue;
        await walk(full);
      } else if (entry.isFile() && (entry.name.endsWith('.ttr') || entry.name.endsWith('.ttrd') || entry.name.endsWith('.ttrg'))) {
        files.push({ path: full, text: await readFile(full, 'utf-8') });
      }
    }
  }
  await walk(projectRoot);

  const result = migratePhase0(files);

  if (!opts.dryRun) {
    for (const w of result.writes) {
      await mkdir(dirname(w.path), { recursive: true });
      await writeFile(w.path, w.text, 'utf-8');
    }
    for (const d of result.deletes) {
      // A `.ttr`/`.ttrd` whose new `.ttrm` is a different path must be removed.
      if (!result.writes.some((w) => w.path === d)) await rm(d, { force: true });
    }
  }

  return result;
}
