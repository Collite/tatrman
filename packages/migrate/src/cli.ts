#!/usr/bin/env node
import { Command } from 'commander';
import { runMigration, resolvePackages, serializeArtifact, runPhase0, type MigrateReport } from './index.js';

const program = new Command();

program
  .name('modeler')
  .description('Tatrman Modeler CLI');

program
  .command('migrate-to-packages')
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

program
  .command('resolve-packages')
  .description('Emit the deterministic resolved-packages.json artifact (packages, entities, domains)')
  .argument('<project-root>', 'Root of the model project (directory containing modeler.toml)')
  .option('--out <file>', 'Output path (default <project-root>/.modeler/resolved-packages.json)')
  .option('--check', 'Compare the on-disk artifact to a freshly-generated one; exit non-zero on drift', false)
  .option('--verbose', 'Print a summary to stderr', false)
  .action(async (projectRoot, opts) => {
    try {
      const { join } = await import('node:path');
      const outPath: string = opts.out ?? join(projectRoot, '.modeler', 'resolved-packages.json');

      const artifact = await resolvePackages(projectRoot);
      const serialized = serializeArtifact(artifact);

      if (opts.verbose) {
        console.error(
          `resolve-packages: ${artifact.packages.length} package(s), ${artifact.entities.length} entit(y/ies), ${artifact.domains.length} domain(s)`
        );
      }

      if (opts.check) {
        const { readFile } = await import('node:fs/promises');
        let onDisk: string;
        try {
          onDisk = await readFile(outPath, 'utf-8');
        } catch {
          console.error(`resolve-packages --check: no artifact at ${outPath}; run 'modeler resolve-packages' to generate it.`);
          process.exit(3);
          return;
        }
        if (onDisk !== serialized) {
          console.error(`resolve-packages --check: ${outPath} is stale. Re-run 'modeler resolve-packages --out ${outPath}'.`);
          process.exit(3);
          return;
        }
        if (opts.verbose) console.error(`resolve-packages --check: ${outPath} is up to date.`);
        process.exit(0);
        return;
      }

      const { mkdir, writeFile } = await import('node:fs/promises');
      const { dirname } = await import('node:path');
      await mkdir(dirname(outPath), { recursive: true });
      await writeFile(outPath, serialized, 'utf-8');
      if (opts.verbose) console.error(`resolve-packages: wrote ${outPath}`);
      process.exit(0);
    } catch (err) {
      console.error('resolve-packages failed:', err);
      process.exit(2);
    }
  });

program
  .command('phase0')
  .description('Migrate a pre-3.0 project to grammar 3.0: *.ttr→*.ttrm, schema map→binding, inline mapping:→binding:, .ttrd domain block→def area .ttrm')
  .argument('<project-root>', 'Root of the project to migrate')
  .option('--dry-run', 'Show what would change without writing or deleting files', false)
  .action(async (projectRoot, opts) => {
    try {
      const result = await runPhase0(projectRoot, { dryRun: opts.dryRun ?? false });
      if (opts.dryRun) console.log('=== DRY RUN — no files written ===\n');
      console.log(`Files written:  ${result.writes.length}`);
      for (const w of result.writes) console.log(`  + ${w.path}`);
      console.log(`Files removed:  ${result.deletes.length}`);
      for (const d of result.deletes) console.log(`  - ${d}`);
      process.exit(0);
    } catch (err) {
      console.error('phase0 migration failed:', err);
      process.exit(2);
    }
  });

program.parse();