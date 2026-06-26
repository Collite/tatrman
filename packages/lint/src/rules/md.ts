import { DiagnosticCode } from '@modeler/parser';
import type { CrossRef, Definition } from '@modeler/parser';
import { defaultSchemaForKind, resolveMdRef } from '@modeler/semantics';
import type { Rule, DocumentRuleContext } from '../rule.js';

/** The six MD logical def kinds (schema md). */
const MD_LOGICAL_KINDS: ReadonlySet<string> = new Set([
  'mdDomain',
  'dimension',
  'mdMap',
  'hierarchy',
  'measure',
  'cubelet',
]);

/** The four MD binding def kinds (schema binding). */
const MD_BINDING_KINDS: ReadonlySet<string> = new Set([
  'md2dbCubelet',
  'md2dbDomain',
  'md2dbMap',
  'md2erCubelet',
]);

export function isMdKind(kind: string): boolean {
  return MD_LOGICAL_KINDS.has(kind) || MD_BINDING_KINDS.has(kind);
}

/** Every span-carrying cross-reference reachable from a top-level MD def. */
function mdRefsOf(def: Definition): CrossRef[] {
  const out: CrossRef[] = [];
  const push = (crs?: CrossRef[]) => {
    if (crs) out.push(...crs);
  };
  switch (def.kind) {
    case 'dimension':
      push(def.crossRefs); // hierarchies
      for (const attr of def.attributes) push(attr.crossRefs); // domain refs
      break;
    case 'cubelet':
      push(def.crossRefs); // grain + measure refs
      for (const m of def.measures) if (typeof m !== 'string') push(m.crossRefs); // inline measure domains
      break;
    case 'mdMap':
    case 'hierarchy':
    case 'measure':
      push(def.crossRefs);
      break;
    default:
      break;
  }
  return out;
}

const unknownRef: Rule = {
  id: 'md-unknown-ref',
  code: DiagnosticCode.MdUnknownRef,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: "An MD cross-reference (domain/map/dimension/measure/hierarchy/grain) doesn't resolve.",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const cr of mdRefsOf(def)) {
        if (!resolveMdRef(ctx.symbols, cr.path, cr.role)) {
          ctx.report({
            source: cr.source,
            message: `Unresolved ${cr.role} reference: '${cr.path}'`,
            data: { role: cr.role, path: cr.path },
          });
        }
      }
    }
  },
};

const unknownSchemaDef: Rule = {
  id: 'md-unknown-schema-def',
  code: DiagnosticCode.MdUnknownSchemaDef,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An MD logical def appears outside `schema md`, or a binding def outside `schema binding`.',
  check(ctx: DocumentRuleContext) {
    if (ctx.scope !== 'document') return;
    const directive = ctx.ast.schemaDirective?.schemaCode;
    if (!directive) return; // schema-less files derive per-def; nothing to police
    for (const def of ctx.ast.definitions) {
      if (!isMdKind(def.kind)) continue;
      const expected = defaultSchemaForKind(def.kind); // 'md' (logical) | 'binding' (binding)
      if (expected !== directive) {
        ctx.report({
          source: def.source,
          message: `'${def.kind}' belongs in 'schema ${expected}', not 'schema ${directive}'`,
          data: { kind: def.kind, expected, actual: directive },
        });
      }
    }
  },
};

export const MD_RULES: Rule[] = [unknownRef, unknownSchemaDef];
