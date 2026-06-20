import { DiagnosticCode } from '@modeler/parser';
import { findCyclesOn, inferPackageFromUri, classifyPackageMismatch } from '@modeler/semantics';
import type { PackagesConfig } from '@modeler/semantics';
import { insertAtTopEdit, replaceRangeEdit } from '@modeler/edit';
import type { Rule, Severity, RuleContext, LintDiagnostic } from '../rule.js';

/**
 * Severity of a plain (leaf-only) declaration/directory mismatch, driven by
 * `[packages].layout` (B16): flexible → Warning, strict → Error, off → suppressed
 * (returns null so the rule reports nothing).
 */
function mismatchSeverity(cfg: PackagesConfig): Exclude<Severity, 'off'> | null {
  if (cfg.layout === 'off') return null;
  return cfg.layout === 'strict' ? 'error' : 'warning';
}

/**
 * Severity of a prefix-divergence (a non-leaf segment diverges) — the louder
 * diagnostic. Warning under flexible/off, Error under strict (contracts §13.2).
 * Never suppressed: a divergent prefix orphans the file from path resolution.
 */
function divergenceSeverity(cfg: PackagesConfig): Exclude<Severity, 'off'> {
  return cfg.layout === 'strict' ? 'error' : 'warning';
}

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

/** Shared suggestion fix: rewrite the declared package to the inferred one. */
const rewriteToInferred = {
  kind: 'suggestion' as const,
  title: 'Rewrite declaration to the inferred package',
  build(ctx: RuleContext, d: LintDiagnostic) {
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
};

const packageDeclarationMismatch: Rule = {
  id: 'package-declaration-mismatch',
  code: DiagnosticCode.PackageDeclarationMismatch,
  category: 'packages',
  scope: 'document',
  // Default tracks the `flexible` layout (B16); the effective severity is driven
  // per-report by `[packages].layout`.
  defaultSeverity: 'warning',
  docs: 'The declared package does not match the package inferred from the file path (leaf-only override).',
  // Rewriting the declared package is a judgment call (the file may be misplaced
  // instead) → suggestion, never batch-applied.
  fix: rewriteToInferred,
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    // Only the plain (leaf-only) mismatch is this rule's concern; a prefix
    // divergence is reported by `package-prefix-divergence` instead (PD1.6).
    const kind = classifyPackageMismatch(ctx.ast, ctx.uri, ctx.manifest.projectRoot, ctx.manifest.packages);
    if (kind !== 'leaf') return;
    const severity = mismatchSeverity(ctx.manifest.packages);
    if (severity === null) return; // layout = "off"
    const declaredPackage = ctx.ast.packageDecl!.name;
    const { inferred } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    ctx.report({
      source: ctx.ast.packageDecl!.source,
      message: `Declared package '${declaredPackage}' does not match inferred package '${inferred}'`,
      data: { inferred, name: declaredPackage },
      severity,
    });
  },
};

const packagePrefixDivergence: Rule = {
  id: 'package-prefix-divergence',
  code: DiagnosticCode.PackagePrefixDivergence,
  category: 'packages',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A declaration\'s non-leaf (prefix) segments diverge from the file\'s directory path, orphaning it from path-based resolution.',
  fix: rewriteToInferred,
  check(ctx) {
    if (ctx.scope !== 'document') return;
    if (ctx.uri.endsWith('.ttrg')) return;
    const kind = classifyPackageMismatch(ctx.ast, ctx.uri, ctx.manifest.projectRoot, ctx.manifest.packages);
    if (kind !== 'prefix') return;
    const declaredPackage = ctx.ast.packageDecl!.name;
    const { inferred } = inferPackageFromUri(ctx.uri, ctx.manifest.projectRoot);
    ctx.report({
      source: ctx.ast.packageDecl!.source,
      message: `Declared package '${declaredPackage}' diverges from the directory-derived package '${inferred}' in its prefix; the file is orphaned from anything resolving through that path. Rename the folder or change [packages].root instead.`,
      data: { inferred, name: declaredPackage },
      severity: divergenceSeverity(ctx.manifest.packages),
    });
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
  packagePrefixDivergence,
  missingPackageDeclaration,
];
