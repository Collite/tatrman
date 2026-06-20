import { DiagnosticCode } from '@modeler/parser';
import type { DomainBlock, SourceLocation } from '@modeler/parser';
import { domainPackageClosure } from '@modeler/semantics';
import type { Rule } from '../rule.js';

// Domain validators (PD3.5). The `.ttrd` file-kind itself (wrong-file-kind) is
// enforced by the parser walker; these rules cover member resolution, emptiness,
// cross-file name collisions, and redundant entity members. They gate on
// `.ttrd` + a `domain` block, mirroring how the graph rules gate on `.ttrg`.

/** Source location for the i-th member, falling back to the block itself. */
function memberSource(sources: SourceLocation[] | undefined, i: number, fallback: SourceLocation): SourceLocation {
  return sources?.[i] ?? fallback;
}

const domainEmpty: Rule = {
  id: 'domain-empty',
  code: DiagnosticCode.DomainEmpty,
  category: 'domains',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A domain block has no members (neither packages nor entities).',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrd')) return;
    const domain = ctx.ast.domain;
    if (domain && domain.packages.length === 0 && domain.entities.length === 0) {
      ctx.report({
        source: domain.source,
        message: `Domain '${domain.name}' has no members; it scopes nothing.`,
      });
    }
  },
};

const domainMemberNotFound: Rule = {
  id: 'domain-member-not-found',
  code: DiagnosticCode.DomainMemberNotFound,
  category: 'domains',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A domain packages:/entities: member does not resolve to a known package or entity.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrd')) return;
    const domain = ctx.ast.domain;
    if (!domain) return;
    const root = ctx.manifest.packages.root;

    domain.packages.forEach((member, i) => {
      if (domainPackageClosure(ctx.symbols, member, root).length === 0) {
        ctx.report({
          source: memberSource(domain.packageSources, i, domain.source),
          message: `Domain package member '${member}' matches no known package.`,
        });
      }
    });

    domain.entities.forEach((member, i) => {
      const res = ctx.resolver.resolveReference(
        { path: member, parts: member.split('.') },
        { schemaCode: '', namespace: '' }
      );
      if (!res.resolved) {
        ctx.report({
          source: memberSource(domain.entitySources, i, domain.source),
          message: `Domain entity member '${member}' does not resolve to a known entity.`,
        });
      }
    });
  },
};

const domainRedundantMember: Rule = {
  id: 'domain-redundant-member',
  code: DiagnosticCode.DomainRedundantMember,
  category: 'domains',
  scope: 'document',
  defaultSeverity: 'info',
  docs: 'An entities: member is already covered by a recursive packages: member.',
  check(ctx) {
    if (ctx.scope !== 'document' || !ctx.uri.endsWith('.ttrd')) return;
    const domain = ctx.ast.domain;
    if (!domain || domain.entities.length === 0 || domain.packages.length === 0) return;
    const root = ctx.manifest.packages.root;

    const closure = new Set<string>();
    for (const member of domain.packages) {
      for (const pkg of domainPackageClosure(ctx.symbols, member, root)) closure.add(pkg);
    }

    domain.entities.forEach((member, i) => {
      const res = ctx.resolver.resolveReference(
        { path: member, parts: member.split('.') },
        { schemaCode: '', namespace: '' }
      );
      if (res.resolved && closure.has(res.symbol.packageName)) {
        ctx.report({
          source: memberSource(domain.entitySources, i, domain.source),
          message: `Entity '${member}' is already covered by a recursive packages: member (package '${res.symbol.packageName}').`,
        });
      }
    });
  },
};

const duplicateDomain: Rule = {
  id: 'duplicate-domain',
  code: DiagnosticCode.DuplicateDomain,
  category: 'domains',
  scope: 'project',
  defaultSeverity: 'error',
  docs: 'Two .ttrd files declare a domain with the same name.',
  check(ctx) {
    if (ctx.scope !== 'project') return;
    const byName = new Map<string, Array<{ uri: string; block: DomainBlock }>>();
    for (const [uri, doc] of ctx.documents) {
      if (!uri.endsWith('.ttrd') || !doc.domain) continue;
      const arr = byName.get(doc.domain.name) ?? [];
      arr.push({ uri, block: doc.domain });
      byName.set(doc.domain.name, arr);
    }
    for (const [name, occurrences] of byName) {
      if (occurrences.length < 2) continue;
      for (const occ of occurrences) {
        const others = occurrences
          .filter((o) => o.uri !== occ.uri)
          .map((o) => o.uri)
          .join(', ');
        ctx.report({
          source: occ.block.source,
          message: `Domain '${name}' is declared in more than one file (also in ${others}).`,
        });
      }
    }
  },
};

export const DOMAIN_RULES: Rule[] = [
  domainEmpty,
  domainMemberNotFound,
  domainRedundantMember,
  duplicateDomain,
];
