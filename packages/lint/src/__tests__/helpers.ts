import { parseString } from '@tatrman/parser';
import type { Document } from '@tatrman/parser';
import {
  ProjectSymbolTable,
  Resolver,
  resolveManifest,
  PackageGraphBuilder,
  synthesizeMappings,
  effectivePackage,
} from '@tatrman/semantics';
import type { ResolvedManifest } from '@tatrman/semantics';
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

/** Options shared by the lint helpers. `packages` overrides the `[packages]` config. */
export interface LintHelperOpts {
  config?: ResolvedLintConfig;
  projectRoot?: string;
  packages?: { root?: string; layout?: 'flexible' | 'strict' | 'off' };
}

function manifestFor(projectRoot: string, packages?: LintHelperOpts['packages']): ResolvedManifest {
  return resolveManifest({ packages }, projectRoot);
}

/** Build symbols/resolver/manifest/graph for a set of files, mirroring the LSP. */
export function buildProject(files: ProjectFile[], projectRoot = '', opts: LintHelperOpts = {}): BuiltProject {
  const manifest = manifestFor(projectRoot, opts.packages);
  const symbols = new ProjectSymbolTable();
  const documents = new Map<string, Document>();
  for (const { uri, src } of files) {
    const ast = parseString(src, uri).ast;
    if (!ast) throw new Error(`parse failed for ${uri}`);
    documents.set(uri, ast);
    const schemaCode = ast.modelDirective?.modelCode ?? '';
    const namespace = ast.modelDirective?.schema ?? '';
    const packageName = effectivePackage(ast, uri, projectRoot, manifest.packages);
    symbols.upsertDocument(uri, ast, schemaCode, namespace, packageName);
    synthesizeMappings(symbols, uri, ast);
  }
  const resolver = new Resolver(symbols, manifest.packages.root);
  const graph = new PackageGraphBuilder(symbols, documents).build();
  return { documents, deps: { manifest, symbols, resolver }, graph };
}

/** Lint a single document under a config (recommended by default). */
export function lintOne(
  uri: string,
  src: string,
  opts: LintHelperOpts = {}
): LintDiagnostic[] {
  const project = buildProject([{ uri, src }], opts.projectRoot ?? '', opts);
  return lintDocument(uri, project.documents.get(uri)!, project.deps, opts.config ?? recommendedConfig());
}

/** Lint one document of a multi-file project (document rules, full symbols). */
export function lintDocInProject(
  files: ProjectFile[],
  uri: string,
  opts: LintHelperOpts = {}
): LintDiagnostic[] {
  const project = buildProject(files, opts.projectRoot ?? '', opts);
  const ast = project.documents.get(uri);
  if (!ast) throw new Error(`no document ${uri}`);
  return lintDocument(uri, ast, project.deps, opts.config ?? recommendedConfig());
}

/** Lint a whole project (for project-scoped rules), bucketed by uri. */
export function lintProj(
  files: ProjectFile[],
  opts: LintHelperOpts = {}
): Map<string, LintDiagnostic[]> {
  const project = buildProject(files, opts.projectRoot ?? '', opts);
  return lintProject(project.documents, project.graph, project.deps, opts.config ?? recommendedConfig());
}

/**
 * Lint a single-file project with BOTH document- and project-scoped rules,
 * returning the merged diagnostics for that file. Use this for rules whose scope
 * may be either (e.g. the MD hierarchy/grain rules became project-scoped so they
 * can see maps in other files); a single-file project is the trivial case.
 */
export function lintAllOne(uri: string, src: string, opts: LintHelperOpts = {}): LintDiagnostic[] {
  const project = buildProject([{ uri, src }], opts.projectRoot ?? '', opts);
  const config = opts.config ?? recommendedConfig();
  const docDiags = lintDocument(uri, project.documents.get(uri)!, project.deps, config);
  const projDiags = lintProject(project.documents, project.graph, project.deps, config).get(uri) ?? [];
  return [...docDiags, ...projDiags];
}

/** Convenience: every code present in a diagnostics array. */
export function codesOf(diags: LintDiagnostic[]): string[] {
  return diags.map((d) => d.code);
}
