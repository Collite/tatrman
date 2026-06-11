import { DiagnosticCode } from '@modeler/parser';
import type { Definition } from '@modeler/parser';
import { defaultSchemaForKind, packageOfImport } from '@modeler/semantics';
import type { DocumentRuleContext, Rule } from '../rule.js';

// Ported from Validator.validateImports, split into three rules. The used-target
// computation uses ctx.refs (shared) instead of recomputing references.

const wildcardWithNoMatches: Rule = {
  id: 'wildcard-with-no-matches',
  code: DiagnosticCode.WildcardWithNoMatches,
  category: 'imports',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A wildcard import matches no definitions.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const imp of ctx.ast.imports ?? []) {
      if (imp.wildcard && ctx.symbols.getByPackage(imp.target).length === 0) {
        ctx.report({
          source: imp.source,
          message: `Wildcard import '${imp.target}.*' has no matching definitions`,
        });
      }
    }
  },
};

const duplicateImport: Rule = {
  id: 'duplicate-import',
  code: DiagnosticCode.DuplicateImport,
  category: 'imports',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'The same import target appears more than once.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const seen = new Set<string>();
    for (const imp of ctx.ast.imports ?? []) {
      if (seen.has(imp.target)) {
        ctx.report({ source: imp.source, message: `Duplicate import of '${imp.target}'` });
      } else {
        seen.add(imp.target);
      }
    }
  },
};

/** Targets actually used by a resolved reference (named- or wildcard-import). */
function usedTargets(ctx: DocumentRuleContext): Set<string> {
  const ast = ctx.ast;
  const directiveSchema = ast.schemaDirective?.schemaCode;
  const namespace = ast.schemaDirective?.namespace ?? '';
  const packageName = ast.packageDecl?.name ?? '';
  const imports = ast.imports ?? [];
  const used = new Set<string>();
  for (const { ref, ownerDef } of ctx.refs) {
    const schemaCode = directiveSchema ?? defaultSchemaForKind((ownerDef as Definition).kind);
    const res = ctx.resolver.resolveReference(
      { path: ref.path, parts: ref.parts },
      { schemaCode, namespace, imports, packageName }
    );
    if (res.resolved && (res.viaStep === 'named-import' || res.viaStep === 'wildcard-import')) {
      const lastDot = res.symbol.qname.lastIndexOf('.');
      used.add(res.symbol.qname.slice(0, lastDot));
    }
  }
  return used;
}

const unusedImport: Rule = {
  id: 'unused-import',
  code: DiagnosticCode.UnusedImport,
  category: 'imports',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A named import is never referenced.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const used = usedTargets(ctx);
    for (const imp of ctx.ast.imports ?? []) {
      if (imp.wildcard) continue;
      const pkg = packageOfImport(imp);
      if (pkg && !used.has(pkg)) {
        ctx.report({ source: imp.source, message: `Import '${imp.target}' is not referenced` });
      }
    }
  },
};

export const IMPORT_RULES: Rule[] = [wildcardWithNoMatches, duplicateImport, unusedImport];
