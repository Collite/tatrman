// migrate-qnames driver (qname-redesign Phase 7).
//
// Rewrites legacy canonical keys to the v4.0 uniform shape in a project's model
// files (`.ttrm` graph `objects:` lists + embedded refs, and `.ttrg` layout node
// keys). Builds the symbol table over the WHOLE project so package-scoped keys
// resolve, computes the old→new key map, then applies it token-wise to every
// file. Pure planner (`planQnameMigration`) + fs wrapper (`runQnameMigration`).
//
// Run BEFORE flipping the symbol table to the uniform shape: the map is derived
// from the legacy keys the old symbol table still emits. After the flip the map
// is empty (nothing to do), which the re-parse/idempotency check confirms.

import { parseString } from '@modeler/parser';
import {
  ProjectSymbolTable,
  effectivePackage,
  parseManifest,
  resolveManifest,
  type PackagesConfig,
} from '@modeler/semantics';
import { computeKeyMap, rewriteCanonicalKeys } from './qname-migrate.js';
import type { ModelFile } from './resolve-packages.js';

export interface QnameMigrationWrite {
  path: string;
  before: string;
  after: string;
}

export interface QnameMigrationPlan {
  /** old canonical key → new canonical key (only entries that change). */
  keyMap: Map<string, string>;
  /** Files whose text changed. */
  writes: QnameMigrationWrite[];
  /** Files that failed to re-parse after rewriting (a rewrite bug — never write). */
  reparseFailures: string[];
}

const isModelExt = (p: string) => p.endsWith('.ttrm') || p.endsWith('.ttrg');

/**
 * Plan the migration over an in-memory file set (pure — no fs). Deterministic:
 * the key map is content-derived and the rewrite is token-exact.
 */
export function planQnameMigration(
  files: ModelFile[],
  projectRoot: string,
  cfg: PackagesConfig,
): QnameMigrationPlan {
  const symbols = new ProjectSymbolTable();
  for (const file of files) {
    if (!isModelExt(file.path)) continue;
    const uri = file.path.startsWith('file://') ? file.path : `file://${file.path}`;
    const ast = parseString(file.text, uri).ast;
    if (!ast) continue;
    const canonical = effectivePackage(ast, file.path, projectRoot, cfg);
    const schemaCode = ast.modelDirective?.modelCode ?? '';
    const namespace = ast.modelDirective?.schema ?? '';
    symbols.upsertDocument(uri, ast, schemaCode, namespace, canonical);
  }

  const keyMap = computeKeyMap(symbols);

  const writes: QnameMigrationWrite[] = [];
  const reparseFailures: string[] = [];
  for (const file of files) {
    if (!isModelExt(file.path)) continue;
    const after = rewriteCanonicalKeys(file.text, keyMap);
    if (after === file.text) continue;
    // Re-parse verification: a rewrite must never break parseability of a .ttrm.
    if (file.path.endsWith('.ttrm')) {
      const reparsed = parseString(after, `file://${file.path}`);
      if (!reparsed.ast || reparsed.errors.length > 0) {
        reparseFailures.push(file.path);
        continue;
      }
    }
    writes.push({ path: file.path, before: file.text, after });
  }

  return { keyMap, writes, reparseFailures };
}

/** Walk a project root, plan, and (unless dryRun) apply the migration to disk. */
export async function runQnameMigration(
  projectRoot: string,
  opts: { dryRun?: boolean } = {},
): Promise<QnameMigrationPlan> {
  const { readFile, writeFile } = await import('node:fs/promises');
  const { join } = await import('node:path');

  let cfg: PackagesConfig;
  try {
    cfg = resolveManifest(parseManifest(await readFile(join(projectRoot, 'modeler.toml'), 'utf-8')), projectRoot).packages;
  } catch {
    cfg = resolveManifest(undefined, projectRoot).packages;
  }

  const files: ModelFile[] = [];
  await walk(projectRoot, files);
  const plan = planQnameMigration(files, projectRoot, cfg);

  if (!opts.dryRun) {
    for (const w of plan.writes) await writeFile(w.path, w.after, 'utf-8');
  }
  return plan;
}

async function walk(dir: string, out: ModelFile[]): Promise<void> {
  const { readdir, readFile } = await import('node:fs/promises');
  const { join } = await import('node:path');
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
      await walk(full, out);
    } else if (entry.isFile() && isModelExt(entry.name)) {
      out.push({ path: full, text: await readFile(full, 'utf-8') });
    }
  }
}
