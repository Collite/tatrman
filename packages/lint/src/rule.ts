import type { SourceLocation, Document, Reference, Definition, DiagnosticCode } from '@modeler/parser';
import type { ResolvedManifest, ProjectSymbolTable, Resolver, PackageGraph } from '@modeler/semantics';
import type { WorkspaceEdit } from '@modeler/edit';

export type Severity = 'error' | 'warning' | 'info' | 'off';
export type RuleCategory =
  | 'correctness'
  | 'references'
  | 'imports'
  | 'packages'
  | 'graph'
  | 'style';
export type RuleScope = 'document' | 'project';
export type RuleId = string; // kebab, no `ttr/` prefix

export interface LintDiagnostic {
  ruleId: RuleId;
  code: DiagnosticCode; // from @modeler/parser, for wire back-compat
  severity: Exclude<Severity, 'off'>;
  message: string;
  source: SourceLocation;
  data?: unknown; // carried to fix.build
}

export interface Fix {
  /** 'safe' fixes are applied by `--fix`; 'suggestion' fixes are CodeAction-only. */
  kind: 'safe' | 'suggestion';
  title: string;
  build(ctx: RuleContext, d: LintDiagnostic): WorkspaceEdit;
}

export interface Rule {
  id: RuleId;
  code: DiagnosticCode;
  category: RuleCategory;
  scope: RuleScope;
  defaultSeverity: Exclude<Severity, 'off'>;
  docs: string;
  check(ctx: RuleContext): void;
  fix?: Fix;
}

/** A reference paired with the top-level definition that owns it. */
export interface OwnedReference {
  ref: Reference;
  ownerDef: Definition;
}

interface BaseContext {
  manifest: ResolvedManifest;
  symbols: ProjectSymbolTable;
  resolver: Resolver;
  /** A rule reports here; it never sets severity (the runner stamps it). */
  report(d: { source: SourceLocation; message: string; data?: unknown }): void;
}

export interface DocumentRuleContext extends BaseContext {
  scope: 'document';
  uri: string;
  ast: Document;
  /** `collectAllReferences(ast)`, computed once by the runner and shared. */
  refs: ReadonlyArray<OwnedReference>;
}

export interface ProjectRuleContext extends BaseContext {
  scope: 'project';
  packageGraph: PackageGraph;
  documents: ReadonlyMap<string, Document>;
}

export type RuleContext = DocumentRuleContext | ProjectRuleContext;
