import { DiagnosticCode } from '@modeler/parser';
import type { AreaDef, SourceLocation } from '@modeler/parser';
import { areaPackageClosure } from '@modeler/semantics';
import type { Rule } from '../rule.js';

// Area validators (v3.0; formerly the `.ttrd` domain validators, PD3.5). Subject
// areas are now plain `def area` definitions that can appear in any model file,
// so these rules iterate the area defs in a document rather than a single
// `.ttrd` domain block. They cover member resolution, emptiness, cross-file name
// collisions, and redundant entity members. Diagnostic codes are `ttr/area-*`
// (v3.0; renamed from the v2.3 `ttr/domain-*`).

/** Source location for the i-th member, falling back to the area itself. */
function memberSource(sources: SourceLocation[] | undefined, i: number, fallback: SourceLocation): SourceLocation {
  return sources?.[i] ?? fallback;
}

/** All `def area` definitions in a document. */
function areasOf(ast: { definitions: ReadonlyArray<{ kind: string }> }): AreaDef[] {
  return ast.definitions.filter((d): d is AreaDef => d.kind === 'area');
}

const areaEmpty: Rule = {
  id: 'area-empty',
  code: DiagnosticCode.AreaEmpty,
  category: 'areas',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'An area def has no members (neither packages nor entities).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const area of areasOf(ctx.ast)) {
      if (area.packages.length === 0 && area.entities.length === 0) {
        ctx.report({
          source: area.source,
          message: `Area '${area.name}' has no members; it scopes nothing.`,
        });
      }
    }
  },
};

const areaMemberNotFound: Rule = {
  id: 'area-member-not-found',
  code: DiagnosticCode.AreaMemberNotFound,
  category: 'areas',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'An area packages:/entities: member does not resolve to a known package or entity.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const root = ctx.manifest.packages.root;

    for (const area of areasOf(ctx.ast)) {
      area.packages.forEach((member, i) => {
        if (areaPackageClosure(ctx.symbols, member, root).length === 0) {
          ctx.report({
            source: memberSource(area.packageSources, i, area.source),
            message: `Area package member '${member}' matches no known package.`,
          });
        }
      });

      area.entities.forEach((member, i) => {
        const res = ctx.resolver.resolveReference(
          { path: member, parts: member.split('.') },
          { schemaCode: '', namespace: '' }
        );
        if (!res.resolved) {
          ctx.report({
            source: memberSource(area.entitySources, i, area.source),
            message: `Area entity member '${member}' does not resolve to a known entity.`,
          });
        }
      });
    }
  },
};

const areaRedundantMember: Rule = {
  id: 'area-redundant-member',
  code: DiagnosticCode.AreaRedundantMember,
  category: 'areas',
  scope: 'document',
  defaultSeverity: 'info',
  docs: 'An entities: member is already covered by a recursive packages: member.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const root = ctx.manifest.packages.root;

    for (const area of areasOf(ctx.ast)) {
      if (area.entities.length === 0 || area.packages.length === 0) continue;

      const closure = new Set<string>();
      for (const member of area.packages) {
        for (const pkg of areaPackageClosure(ctx.symbols, member, root)) closure.add(pkg);
      }

      area.entities.forEach((member, i) => {
        const res = ctx.resolver.resolveReference(
          { path: member, parts: member.split('.') },
          { schemaCode: '', namespace: '' }
        );
        if (res.resolved && closure.has(res.symbol.packageName)) {
          ctx.report({
            source: memberSource(area.entitySources, i, area.source),
            message: `Entity '${member}' is already covered by a recursive packages: member (package '${res.symbol.packageName}').`,
          });
        }
      });
    }
  },
};

const duplicateArea: Rule = {
  id: 'duplicate-area',
  code: DiagnosticCode.DuplicateArea,
  category: 'areas',
  scope: 'project',
  defaultSeverity: 'error',
  docs: 'Two files declare a `def area` with the same name.',
  check(ctx) {
    if (ctx.scope !== 'project') return;
    const byName = new Map<string, Array<{ uri: string; area: AreaDef }>>();
    for (const [uri, doc] of ctx.documents) {
      for (const area of areasOf(doc)) {
        const arr = byName.get(area.name) ?? [];
        arr.push({ uri, area });
        byName.set(area.name, arr);
      }
    }
    for (const [name, occurrences] of byName) {
      if (occurrences.length < 2) continue;
      for (const occ of occurrences) {
        const others = occurrences
          .filter((o) => o.uri !== occ.uri)
          .map((o) => o.uri)
          .join(', ');
        ctx.report({
          source: occ.area.source,
          message: `Area '${name}' is declared in more than one file (also in ${others}).`,
        });
      }
    }
  },
};

export const AREA_RULES: Rule[] = [
  areaEmpty,
  areaMemberNotFound,
  areaRedundantMember,
  duplicateArea,
];
