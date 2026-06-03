#!/usr/bin/env node
import { Command } from 'commander';
import { runMigration, type MigrateReport } from './index.js';

const program = new Command();

program
  .name('modeler-migrate')
  .description('Migrate a v1 project (no packages) to v1.1 (packages, imports, per-graph .ttrg files)')
  .argument('<project-root>', 'Root of the project to migrate (directory containing modeler.toml)')
  .option('--dry-run', 'Show what would be done without writing any files', false)
  .option('--commit-ttrl-removal', 'Delete .modeler/layout.ttrl after successful migration', false)
  .option('--wildcard-threshold <N>', 'Minimum symbols from a package to use wildcard import (default 3)', '3')
  .option('--verbose', 'Print detailed progress', false)
  .action(async (projectRoot, opts) => {
    const args = {
      projectRoot,
      dryRun: opts.dryRun ?? false,
      commitTtrlRemoval: opts.commitTtrlRemoval ?? false,
      verbose: opts.verbose ?? false,
      wildcardThreshold: parseInt(opts.wildcardThreshold ?? '3', 10),
    };

    try {
      const { report, writes } = await runMigration(args);

      if (args.dryRun) {
        console.log('=== DRY RUN — no files written ===\n');
      }

      console.log(`Files touched: ${report.filesTouched.length}`);
      if (args.verbose) for (const f of report.filesTouched) console.log(`  ${f}`);

      console.log(`\nPackages created: ${report.packagesCreated.length}`);
      if (args.verbose) for (const p of report.packagesCreated) console.log(`  ${p}`);

      console.log(`\nImports inserted: ${report.importsInserted.length}`);
      if (args.verbose) for (const imp of report.importsInserted) {
        const form = imp.isWildcard ? '.*' : `.${imp.schema}.${imp.namespace}.${imp.defName}`;
        console.log(`  ${imp.uri}: import ${imp.package}${form}`);
      }

      console.log(`\n.ttrg files created: ${report.ttrgFilesCreated.length}`);
      if (args.verbose) for (const g of report.ttrgFilesCreated) console.log(`  ${g}`);

      if (report.ambiguousReferences.length > 0) {
        console.log(`\nAmbiguous references (manual resolution required):`);
        for (const amb of report.ambiguousReferences) {
          console.log(`  ${amb.uri}:${amb.line}: "${amb.ref}" — candidates: ${amb.candidates.join(', ')}`);
        }
      }

      if (args.dryRun) {
        console.log(`\nWould write ${writes.length} file(s)`);
        process.exit(0);
      }

      console.log(`\nWrote ${writes.length} file(s)`);
      await writeReport(projectRoot, report);

      if (report.ambiguousReferences.length > 0) {
        console.log('\nAmbiguous references require manual disambiguation. Fix and re-run.');
        process.exit(1);
      }

      process.exit(0);
    } catch (err) {
      console.error('Migration failed:', err);
      process.exit(2);
    }
  });

async function writeReport(projectRoot: string, report: MigrateReport): Promise<void> {
  try {
    const { mkdir, writeFile } = await import('node:fs/promises');
    const { join } = await import('node:path');
    const dir = join(projectRoot, '.modeler');
    await mkdir(dir, { recursive: true });
    await writeFile(join(dir, 'migrate-report.json'), JSON.stringify(report, null, 2), 'utf-8');
  } catch {
    // non-fatal
  }
}

program.parse();