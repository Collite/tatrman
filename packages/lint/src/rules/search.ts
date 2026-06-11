import { DiagnosticCode } from '@modeler/parser';
import type { Rule } from '../rule.js';
import { searchBlocksOf } from '../internal/search-blocks.js';

// Ported from the search-block checks in Validator.validateDocument.

const fuzzyWithoutSearchable: Rule = {
  id: 'fuzzy-without-searchable',
  code: DiagnosticCode.FuzzyWithoutSearchable,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'fuzzy search requires the element to be searchable.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const sb of searchBlocksOf(def)) {
        if (sb.fuzzy === true && sb.searchable !== true) {
          ctx.report({
            source: sb.source,
            message:
              'fuzzy search is enabled but the element is not marked searchable; set searchable: true',
          });
        }
      }
    }
  },
};

const duplicateSearchProperty: Rule = {
  id: 'duplicate-search-property',
  code: DiagnosticCode.DuplicateSearchProperty,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A search block must not repeat a property.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const sb of searchBlocksOf(def)) {
        for (const dup of sb.duplicateProperties ?? []) {
          ctx.report({ source: sb.source, message: `Duplicate '${dup}' in search block` });
        }
      }
    }
  },
};

export const SEARCH_RULES: Rule[] = [fuzzyWithoutSearchable, duplicateSearchProperty];
