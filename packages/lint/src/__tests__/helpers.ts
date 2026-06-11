import { parseString } from '@modeler/parser';
import type { Document } from '@modeler/parser';
import {
  ProjectSymbolTable,
  Resolver,
  resolveManifest,
  PackageGraphBuilder,
  synthesizeMappings,
} from '@modeler/semantics';
import { lintDocument, lintProject, type LintDeps } from '../runner.js';
import { recommendedConfig as packageRecommended, type ResolvedLintConfig } from '../config.js';
import type { LintDiagnostic, Severity } from '../rule.js';

/** A `recommended`-equivalent config: rule defaults, except missing-description off. */
export function recommendedConfig(overrides: Record<string, Severity> = {}): ResolvedLintConfig {
  return packageRecommended({ overrides });
}

export interface ProjectFile {
  uri: string;
  src: string;
}

interface BuiltProject {
  documents: Map<string, Document>;
  deps: LintDeps;
  graph: ReturnType<PackageGraphBuilder['build']>;
}

/** Build symbols/resolver/manifest/graph for a set of files, mirroring the LSP. */
export function buildProject(files: ProjectFile[], projectRoot = ''): BuiltProject {
  const symbols = new ProjectSymbolTable();
  const documents = new Map<string, Document>();
  for (const { uri, src } of files) {
    const ast = parseString(src, uri).ast;
    if (!ast) throw new Error(`parse failed for ${uri}`);
    documents.set(uri, ast);
    const schemaCode = ast.schemaDirective?.schemaCode ?? '';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';
    symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);
    synthesizeMappings(symbols, uri, ast);
  }
  const resolver = new Resolver(symbols);
  const manifest = resolveManifest({}, projectRoot);
  const graph = new PackageGraphBuilder(symbols, documents).build();
  return { documents, deps: { manifest, symbols, resolver }, graph };
}

/** Lint a single document under a config (recommended by default). */
export function lintOne(
  uri: string,
  src: string,
  opts: { config?: ResolvedLintConfig; projectRoot?: string } = {}
): LintDiagnostic[] {
  const project = buildProject([{ uri, src }], opts.projectRoot ?? '');
  return lintDocument(uri, project.documents.get(uri)!, project.deps, opts.config ?? recommendedConfig());
}

/** Lint one document of a multi-file project (document rules, full symbols). */
export function lintDocInProject(
  files: ProjectFile[],
  uri: string,
  opts: { config?: ResolvedLintConfig; projectRoot?: string } = {}
): LintDiagnostic[] {
  const project = buildProject(files, opts.projectRoot ?? '');
  const ast = project.documents.get(uri);
  if (!ast) throw new Error(`no document ${uri}`);
  return lintDocument(uri, ast, project.deps, opts.config ?? recommendedConfig());
}

/** Lint a whole project (for project-scoped rules), bucketed by uri. */
export function lintProj(
  files: ProjectFile[],
  opts: { config?: ResolvedLintConfig; projectRoot?: string } = {}
): Map<string, LintDiagnostic[]> {
  const project = buildProject(files, opts.projectRoot ?? '');
  return lintProject(project.documents, project.graph, project.deps, opts.config ?? recommendedConfig());
}

/** Convenience: every code present in a diagnostics array. */
export function codesOf(diags: LintDiagnostic[]): string[] {
  return diags.map((d) => d.code);
}
