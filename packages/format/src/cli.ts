#!/usr/bin/env node
import { Command } from 'commander';
import { readFileSync, writeFileSync, statSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { format } from './index.js';

const program = new Command();

program
  .name('modeler-fmt')
  .description('Format TTR source files (canonical layout, comment-preserving)')
  .argument('<path>', 'A .ttr/.ttrg file, or a directory to format recursively')
  .option('--check', 'Exit 1 if any file is not already formatted; write nothing', false)
  .option('--write', 'Rewrite files in place', false)
  .action((path: string, opts: { check?: boolean; write?: boolean }) => {
    let files: string[];
    try {
      files = collectFiles(path);
    } catch (err) {
      console.error(`modeler-fmt: ${err instanceof Error ? err.message : String(err)}`);
      process.exit(2);
    }

    if (files.length === 0) {
      console.error(`modeler-fmt: no .ttr/.ttrg files found at ${path}`);
      process.exit(2);
    }

    const unformatted: string[] = [];
    let written = 0;

    for (const file of files) {
      const src = readFileSync(file, 'utf-8');
      let formatted: string;
      try {
        formatted = format(src, `file://${file}`);
      } catch (err) {
        // Never partially write on a parse error.
        console.error(`modeler-fmt: cannot format ${file}: ${err instanceof Error ? err.message : String(err)}`);
        process.exit(2);
      }

      if (opts.check) {
        if (formatted !== src) unformatted.push(file);
        continue;
      }
      if (opts.write) {
        if (formatted !== src) {
          writeFileSync(file, formatted, 'utf-8');
          written++;
        }
        continue;
      }
      // Default: print to stdout.
      process.stdout.write(formatted);
    }

    if (opts.check) {
      if (unformatted.length > 0) {
        console.error(`modeler-fmt: ${unformatted.length} file(s) not formatted:`);
        for (const f of unformatted) console.error(`  ${f}`);
        process.exit(1);
      }
      process.exit(0);
    }

    if (opts.write) {
      console.log(`modeler-fmt: formatted ${written} file(s)`);
      process.exit(0);
    }

    process.exit(0);
  });

/** Resolve a file or directory to the list of .ttr/.ttrg files to format. */
function collectFiles(path: string): string[] {
  const st = statSync(path); // throws if missing → caught as operational failure
  if (st.isFile()) {
    if (!path.endsWith('.ttr') && !path.endsWith('.ttrg')) {
      throw new Error(`not a .ttr/.ttrg file: ${path}`);
    }
    return [path];
  }
  const out: string[] = [];
  const walk = (dir: string): void => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.') || entry.name === 'node_modules') continue;
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.ttr') || entry.name.endsWith('.ttrg')) out.push(full);
    }
  };
  walk(path);
  return out.sort();
}

program.parse();
