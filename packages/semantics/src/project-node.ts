import { readFile } from 'fs/promises';
import { join, dirname } from 'path';
import { parseManifest, resolveManifest, type ProjectManifest, type ResolvedManifest } from './manifest.js';
import type { Project } from './index.js';

export async function findProjectRoot(documentPath: string, workspaceFolder: string): Promise<string> {
  let dir = dirname(documentPath);
  const maxIterations = 100;
  let iterations = 0;

  while (iterations < maxIterations) {
    try {
      const manifestPath = join(dir, 'modeler.toml');
      await readFile(manifestPath, 'utf-8');
      return dir;
    } catch {
      const parent = dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
    iterations++;
  }

  return workspaceFolder;
}

export async function loadProject(root: string): Promise<Project> {
  let manifest: ResolvedManifest | undefined;
  let rawManifest: ProjectManifest | undefined;

  try {
    const content = await readFile(join(root, 'modeler.toml'), 'utf-8');
    rawManifest = parseManifest(content);
    manifest = resolveManifest(rawManifest, root);
  } catch {
    manifest = resolveManifest(undefined, root);
  }

  const ttrFiles: string[] = [];
  await walkDir(root, ttrFiles);

  return { root, manifest, ttrFiles };
}

async function walkDir(dir: string, results: string[]): Promise<void> {
  const { readdir } = await import('fs/promises');
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }

  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === '.modeler' || entry.name === 'node_modules' || entry.name === '.git') continue;
      await walkDir(fullPath, results);
    } else if (entry.isFile() && entry.name.endsWith('.ttrm')) {
      results.push(fullPath);
    }
  }
}