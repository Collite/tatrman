import { DiagnosticCode } from '@modeler/parser';
import type { Rule } from '../rule.js';

// Placeholder until P2b ports the real 26 checks. It proves the registry/runner
// wiring without asserting any behaviour (it reports nothing).
const alwaysOk: Rule = {
  id: 'always-ok',
  code: DiagnosticCode.FileOrdering,
  category: 'style',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'Placeholder rule; reports nothing. Removed when P2b lands.',
  check() {
    /* no-op */
  },
};

/** Every rule in the registry, assembled from the per-category rule modules. */
export const ALL_RULES: Rule[] = [alwaysOk];
