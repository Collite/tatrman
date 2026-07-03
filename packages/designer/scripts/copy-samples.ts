import fs from 'node:fs';
import path from 'node:path';

/** Walk `srcDir` and return the manifest of relative file paths, applying the
 *  same filtering rules used when copying (skip dotfiles and `.modeler`). */
export function listSampleFiles(srcDir: string): string[] {
  const manifest: string[] = [];

  function walk(src: string, prefix: string): void {
    for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
      if (entry.name.startsWith('.')) continue;
      if (entry.name === '.modeler') continue;
      const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
      if (entry.isDirectory()) {
        walk(path.join(src, entry.name), rel);
      } else {
        manifest.push(rel);
      }
    }
  }

  walk(srcDir, '');
  return manifest;
}

export function copySamples(srcDir: string, dest: string): string[] {
  fs.mkdirSync(dest, { recursive: true });
  const manifest = listSampleFiles(srcDir);

  for (const rel of manifest) {
    const srcPath = path.join(srcDir, rel);
    const dstPath = path.join(dest, rel);
    fs.mkdirSync(path.dirname(dstPath), { recursive: true });
    fs.copyFileSync(srcPath, dstPath);
  }

  fs.writeFileSync(path.join(dest, 'index.json'), JSON.stringify(manifest, null, 2));
  return manifest;
}