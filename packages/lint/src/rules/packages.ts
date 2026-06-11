import { DiagnosticCode } from '@modeler/parser';
import { findCyclesOn, inferPackageFromUri } from '@modeler/semantics';
import { insertAtTopEdit, replaceRangeEdit } from '@modeler/edit';
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
  // Rewriting the declared package is a judgment call (the file may be misplaced
  // instead) → suggestion, never batch-applied.
  fix: {
    kind: 'suggestion',
    title: 'Rewrite declaration to the inferred package',
    build(ctx, d) {
      const data = d.data as { inferred?: string; name?: string } | undefined;
      const text = ctx.scope === 'document' ? ctx.text : undefined;
      if (!data?.inferred || !data.name || text === undefined) return { documentChanges: [] };
      const lineIdx = d.source.line - 1;
      const lineText = text.split('\n')[lineIdx] ?? '';
      const col = lineText.indexOf(data.name);
      const range = {
        start: { line: lineIdx, character: col < 0 ? 0 : col },
        end: { line: lineIdx, character: col < 0 ? 0 : col + data.name.length },
      };
      return replaceRangeEdit(d.source.file, range, data.inferred);
    },
  },
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    const declaredPackage = ctx.ast.packageDecl?.name ?? '';
    const { inferred } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    if (declaredPackage && inferred && declaredPackage !== inferred) {
      ctx.report({
        source: ctx.ast.packageDecl!.source,
        message: `Declared package '${declaredPackage}' does not match inferred package '${inferred}'`,
        data: { inferred, name: declaredPackage },
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
  fix: {
    kind: 'safe',
    title: 'Insert the inferred package declaration',
    build(_ctx, d) {
      const inferred = (d.data as { inferred?: string } | undefined)?.inferred;
      if (!inferred) return { documentChanges: [] };
      return insertAtTopEdit(d.source.file, `package ${inferred}\n\n`);
    },
  },
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    const declaredPackage = ctx.ast.packageDecl?.name ?? '';
    const { inferred, isRootFile } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    if (!declaredPackage && !isRootFile) {
      ctx.report({
        source: ctx.ast.source,
        message: `File is in package '${inferred}' but has no package declaration`,
        data: { inferred },
      });
    }
  },
};

export const PACKAGE_RULES: Rule[] = [
  circularPackageDependency,
  packageDeclarationMismatch,
  missingPackageDeclaration,
];
