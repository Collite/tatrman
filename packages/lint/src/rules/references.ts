import { DiagnosticCode } from '@modeler/parser';
import type { Document, Reference, Definition } from '@modeler/parser';
import { defaultSchemaForKind, enclosingQnameOf, packageOfImport } from '@modeler/semantics';
import type { Resolver } from '@modeler/semantics';
import type { DocumentRuleContext, Rule } from '../rule.js';

// Ported from Validator.validateReferences. Uses ctx.refs (computed once by the
// runner) rather than recomputing collectAllReferences. The old `strict`
// error/warning flip for unresolved-reference is now config (P3); the rule
// reports unconditionally at its default (warning).

interface RefResolution {
  ref: Reference;
  res: ReturnType<Resolver['resolveReference']>;
}

function resolveAll(ctx: DocumentRuleContext): RefResolution[] {
  const ast: Document = ctx.ast;
  const directiveSchema = ast.schemaDirective?.schemaCode;
  const namespace = ast.schemaDirective?.namespace ?? '';
  const packageName = ast.packageDecl?.name ?? '';
  const out: RefResolution[] = [];
  for (const { ref, ownerDef } of ctx.refs) {
    const schemaCode = directiveSchema ?? defaultSchemaForKind((ownerDef as Definition).kind);
    const enclosingQname = enclosingQnameOf(ownerDef, schemaCode, namespace, packageName);
    const res = ctx.resolver.resolveReference(
      { path: ref.path, parts: ref.parts },
      { schemaCode, namespace, enclosingQname, imports: ast.imports, packageName }
    );
    out.push({ ref, res });
  }
  return out;
}

const unresolvedReference: Rule = {
  id: 'unresolved-reference',
  code: DiagnosticCode.UnresolvedReference,
  category: 'references',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A cross-reference could not be resolved to any definition.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const { ref, res } of resolveAll(ctx)) {
      if (!res.resolved && res.reason !== 'ambiguous') {
        ctx.report({
          source: ref.source,
          message: `Unresolved reference: '${ref.path}' (tried ${res.tried.map((a) => a.candidate).join(', ')})`,
        });
      }
    }
  },
};

const ambiguousReference: Rule = {
  id: 'ambiguous-reference',
  code: DiagnosticCode.AmbiguousReference,
  category: 'references',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A reference matches multiple definitions via wildcard imports.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const { ref, res } of resolveAll(ctx)) {
      if (!res.resolved && res.reason === 'ambiguous') {
        const loc = res.candidates?.[0]?.source ?? ref.source;
        ctx.report({
          source: loc,
          message: `Ambiguous reference: '${ref.path}' matches ${res.candidates?.length ?? 0} definitions via wildcard imports`,
        });
      }
    }
  },
};

const unimportedReference: Rule = {
  id: 'unimported-reference',
  code: DiagnosticCode.UnimportedReference,
  category: 'references',
  scope: 'document',
  defaultSeverity: 'info',
  docs: 'A reference resolves via package search but its package is not imported.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const ast = ctx.ast;
    const packageName = ast.packageDecl?.name ?? '';
    if (!packageName) return;
    const importedPkgs = new Set((ast.imports ?? []).map((imp) => packageOfImport(imp)));
    for (const { ref, res } of resolveAll(ctx)) {
      if (res.resolved && res.viaStep === 'fully-qualified') {
        const resolvedPackage = res.symbol.packageName;
        if (resolvedPackage && resolvedPackage !== packageName && !importedPkgs.has(resolvedPackage)) {
          ctx.report({
            source: ref.source,
            message: `Reference to '${res.symbol.qname}' resolves via package search; consider adding an import`,
          });
        }
      }
    }
  },
};

export const REFERENCE_RULES: Rule[] = [unresolvedReference, ambiguousReference, unimportedReference];
