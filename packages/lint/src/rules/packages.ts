import { DiagnosticCode } from '@modeler/parser';
import { findCyclesOn, inferPackageFromUri } from '@modeler/semantics';
import type { Rule } from '../rule.js';

// Ported from Validator.validateCircularDependencies (project) and
// validatePackageDeclarations (document).

const circularPackageDependency: Rule = {
  id: 'circular-package-dependency',
  code: DiagnosticCode.CircularPackageDependency,
  category: 'packages',
  scope: 'project',
  defaultSeverity: 'warning',
  docs: 'A package participates in an import cycle.',
  check(ctx) {
    if (ctx.scope !== 'project') return;
    const cycles = findCyclesOn(ctx.packageGraph);
    const packageToUris = new Map<string, string[]>();
    for (const entry of ctx.symbols.all()) {
      const arr = packageToUris.get(entry.packageName) ?? [];
      arr.push(entry.documentUri);
      packageToUris.set(entry.packageName, arr);
    }
    for (const cycle of cycles) {
      const uri = packageToUris.get(cycle[0])?.[0] ?? '';
      ctx.report({
        source: { file: uri, line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
        message: `Package '${cycle[0]}' is part of a cycle: ${cycle.join(' → ')} → ${cycle[0]}. Cycles parse cleanly but make dependency reasoning harder.`,
      });
    }
  },
};

const packageDeclarationMismatch: Rule = {
  id: 'package-declaration-mismatch',
  code: DiagnosticCode.PackageDeclarationMismatch,
  category: 'packages',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'The declared package does not match the package inferred from the file path.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    const declaredPackage = ctx.ast.packageDecl?.name ?? '';
    const { inferred } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    if (declaredPackage && inferred && declaredPackage !== inferred) {
      ctx.report({
        source: ctx.ast.packageDecl!.source,
        message: `Declared package '${declaredPackage}' does not match inferred package '${inferred}'`,
      });
    }
  },
};

const missingPackageDeclaration: Rule = {
  id: 'missing-package-declaration',
  code: DiagnosticCode.MissingPackageDeclaration,
  category: 'packages',
  scope: 'document',
  defaultSeverity: 'info',
  docs: 'A non-root file has no package declaration.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    const declaredPackage = ctx.ast.packageDecl?.name ?? '';
    const { inferred, isRootFile } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    if (!declaredPackage && !isRootFile) {
      ctx.report({
        source: ctx.ast.source,
        message: `File is in package '${inferred}' but has no package declaration`,
      });
    }
  },
};

export const PACKAGE_RULES: Rule[] = [
  circularPackageDependency,
  packageDeclarationMismatch,
  missingPackageDeclaration,
];
