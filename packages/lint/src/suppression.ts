import type { Document } from '@modeler/parser';
import type { RuleId } from './rule.js';

export interface SuppressionIndex {
  /** `line` is 1-indexed (ANTLR-style). */
  isSuppressed(ruleId: RuleId, line: number): boolean;
  unused(): Array<{ line: number; ruleId?: RuleId }>;
}

const NEVER_SUPPRESSED: SuppressionIndex = {
  isSuppressed: () => false,
  unused: () => [],
};

/**
 * Build a suppression index from the document's comment trivia.
 *
 * P2a stub: returns a never-suppressing index. P2c parses the real
 * `// ttr-disable-*` directives from `leadingTrivia`/`trailingTrivia`
 * (contracts §4).
 */
export function buildSuppressionIndex(_ast: Document): SuppressionIndex {
  return NEVER_SUPPRESSED;
}
