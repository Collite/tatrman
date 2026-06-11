import type { Document, SourceLocation } from '@modeler/parser';
import { collectAllReferences } from '@modeler/semantics';
import type { ResolvedManifest, ProjectSymbolTable, Resolver, PackageGraph } from '@modeler/semantics';
import type {
  LintDiagnostic,
  Rule,
  Severity,
  DocumentRuleContext,
  ProjectRuleContext,
} from './rule.js';
import { RULES } from './registry.js';
import { buildSuppressionIndex, type SuppressionIndex } from './suppression.js';
import type { ResolvedLintConfig } from './config.js';

/** Shared semantic inputs every rule context needs (design §5.2). */
export interface LintDeps {
  manifest: ResolvedManifest;
  symbols: ProjectSymbolTable;
  resolver: Resolver;
}

function allRules(): Rule[] {
  return [...RULES.values()];
}

/**
 * Lint a single document. Builds the shared context once (refs computed via
 * `collectAllReferences`, suppression index from the AST's comment trivia),
 * runs each enabled document-scoped rule, and stamps effective severity from
 * config. `off` rules are skipped (never invoked).
 *
 * NB: the suppression index reads from the AST trivia attached in P0
 * (`buildSuppressionIndex(ast)`, contracts §4.2) — no `CommonTokenStream` is
 * needed, so the contract's §3.5 `tokenStream` parameter is omitted.
 */
export function lintDocument(
  uri: string,
  ast: Document,
  deps: LintDeps,
  config: ResolvedLintConfig,
  rules: Rule[] = allRules()
): LintDiagnostic[] {
  const refs = collectAllReferences(ast);
  const suppression = buildSuppressionIndex(ast);
  const out: LintDiagnostic[] = [];

  for (const rule of rules) {
    if (rule.scope !== 'document') continue;
    const severity = config.severityOf(rule.id);
    if (severity === 'off') continue;

    const ctx: DocumentRuleContext = {
      scope: 'document',
      uri,
      ast,
      refs,
      manifest: deps.manifest,
      symbols: deps.symbols,
      resolver: deps.resolver,
      report: (d) => {
        emit(out, rule, severity, suppression, d);
      },
    };
    rule.check(ctx);
  }

  // A directive that suppressed nothing → ttrlint/unused-suppression (warning).
  for (const u of suppression.unused()) {
    out.push({
      ruleId: 'ttrlint/unused-suppression',
      code: 'ttrlint/unused-suppression',
      severity: 'warning',
      message: u.ruleId
        ? `Unused suppression directive for rule '${u.ruleId}'`
        : 'Unused suppression directive (suppressed nothing)',
      source: lineLoc(uri, u.line),
    });
  }
  return out;
}

/**
 * Lint a whole project (project-scoped rules). Results are bucketed by the
 * `source.file` (URI) each diagnostic points at, so editing one file still
 * surfaces project-level diagnostics on the right file (design §9.1).
 */
export function lintProject(
  documents: ReadonlyMap<string, Document>,
  packageGraph: PackageGraph,
  deps: LintDeps,
  config: ResolvedLintConfig,
  rules: Rule[] = allRules()
): Map<string, LintDiagnostic[]> {
  const result = new Map<string, LintDiagnostic[]>();
  for (const uri of documents.keys()) result.set(uri, []);

  // Project-scope diagnostics aren't line-suppressible per-document here; the
  // (no-op-for-now) hook lives in lintDocument. Build per-doc suppression lazily
  // so project rules still honour file-level directives once P2c fills them in.
  const suppressionByUri = new Map<string, SuppressionIndex>();
  const suppressionFor = (uri: string): SuppressionIndex | undefined => {
    if (!documents.has(uri)) return undefined;
    let idx = suppressionByUri.get(uri);
    if (!idx) {
      idx = buildSuppressionIndex(documents.get(uri)!);
      suppressionByUri.set(uri, idx);
    }
    return idx;
  };

  for (const rule of rules) {
    if (rule.scope !== 'project') continue;
    const severity = config.severityOf(rule.id);
    if (severity === 'off') continue;

    const ctx: ProjectRuleContext = {
      scope: 'project',
      packageGraph,
      documents,
      manifest: deps.manifest,
      symbols: deps.symbols,
      resolver: deps.resolver,
      report: (d) => {
        const uri = d.source.file;
        let bucket = result.get(uri);
        if (!bucket) {
          bucket = [];
          result.set(uri, bucket);
        }
        emit(bucket, rule, severity, suppressionFor(uri), d);
      },
    };
    rule.check(ctx);
  }
  return result;
}

function emit(
  out: LintDiagnostic[],
  rule: Rule,
  severity: Exclude<Severity, 'off'>,
  suppression: SuppressionIndex | undefined,
  d: { source: SourceLocation; message: string; data?: unknown }
): void {
  const line = d.source.line;
  if (suppression) {
    if (rule.category === 'correctness') {
      // Correctness rules describe a model that will not load — they cannot be
      // suppressed (design §6.5/§8). If a directive tried, mark it used (so it
      // isn't reported as "unused") and emit ttrlint/cannot-suppress.
      if (suppression.targets(rule.id, line)) {
        suppression.isSuppressed(rule.id, line); // mark the directive used
        out.push({
          ruleId: 'ttrlint/cannot-suppress',
          code: 'ttrlint/cannot-suppress',
          severity: 'info',
          message: `Rule '${rule.id}' is a correctness rule and cannot be suppressed`,
          source: d.source,
        });
      }
      // fall through: the real diagnostic is always emitted.
    } else if (suppression.isSuppressed(rule.id, line)) {
      return; // suppressed
    }
  }
  out.push({
    ruleId: rule.id,
    code: rule.code,
    severity,
    message: d.message,
    source: d.source,
    data: d.data,
  });
}

function lineLoc(file: string, line: number): SourceLocation {
  return { file, line, column: 0, endLine: line, endColumn: 0, offsetStart: 0, offsetEnd: 0 };
}
