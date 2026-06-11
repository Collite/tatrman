export { lintDocument, lintProject } from './runner.js';
export type { LintDeps, ResolvedLintConfig } from './runner.js';
export { RULES, ruleForCode, rulesByCategory } from './registry.js';
export { buildSuppressionIndex } from './suppression.js';
export type { SuppressionIndex } from './suppression.js';
export type {
  Severity,
  RuleCategory,
  RuleScope,
  RuleId,
  LintDiagnostic,
  Fix,
  Rule,
  OwnedReference,
  DocumentRuleContext,
  ProjectRuleContext,
  RuleContext,
} from './rule.js';
