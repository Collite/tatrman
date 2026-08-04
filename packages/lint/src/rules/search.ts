// SPDX-License-Identifier: Apache-2.0
import { DiagnosticCode } from '@tatrman/parser';
import { insertEdit, removeLineEdit } from '@tatrman/edit';
import { validateSearchMethods } from '@tatrman/semantics';
import type { Rule } from '../rule.js';
import { searchBlocksOf } from '../internal/search-blocks.js';
import { positionAt } from '../internal/text-position.js';

// Ported from the search-block checks in Validator.validateDocument.

const fuzzyWithoutSearchable: Rule = {
  id: 'fuzzy-without-searchable',
  code: DiagnosticCode.FuzzyWithoutSearchable,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'fuzzy search requires the element to be searchable.',
  // Safe: insert `searchable: true,` just inside the search block's opening brace.
  fix: {
    kind: 'safe',
    title: 'Add searchable: true',
    build(ctx, d) {
      if (ctx.scope !== 'document' || ctx.text === undefined) return { documentChanges: [] };
      const braceIdx = ctx.text.indexOf('{', d.source.offsetStart);
      if (braceIdx < 0) return { documentChanges: [] };
      const pos = positionAt(ctx.text, braceIdx + 1);
      return insertEdit(d.source.file, pos.line, pos.character, ' searchable: true,');
    },
  },
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
  // Deleting which duplicate to keep is a judgment call → suggestion.
  fix: {
    kind: 'suggestion',
    title: 'Delete the duplicate property',
    build(ctx, d) {
      const dup = (d.data as { dup?: string } | undefined)?.dup;
      if (ctx.scope !== 'document' || ctx.text === undefined || !dup) return { documentChanges: [] };
      // Remove the second occurrence's line within the search block.
      const lines = ctx.text.split('\n');
      const startLine = d.source.line - 1;
      let seen = 0;
      for (let i = startLine; i < lines.length && i <= d.source.endLine; i++) {
        if (new RegExp(`(^|[^\\w])${dup}\\s*:`).test(lines[i])) {
          seen++;
          if (seen === 2) return removeLineEdit(d.source.file, i);
        }
      }
      return { documentChanges: [] };
    },
  },
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const sb of searchBlocksOf(def)) {
        for (const dup of sb.duplicateProperties ?? []) {
          ctx.report({ source: sb.source, message: `Duplicate '${dup}' in search block`, data: { dup } });
        }
      }
    }
  },
};

/**
 * RV-P1.5 (grammar 0.12, RV-32) — surface `@tatrman/semantics`'
 * `validateSearchMethods` as lint rules. The MEANING (vocabulary, arity, the
 * `fuzzy` mapping) lives in semantics and is shared with the Kotlin/Python
 * validators; these rules only route it into the lint stream, which is what puts
 * them in the portable cross-target conformance subset.
 */
function searchMethodRule(
  id: string,
  code: DiagnosticCode,
  defaultSeverity: 'error' | 'warning',
  docs: string,
  fix?: Rule['fix'],
): Rule {
  return {
    id,
    code,
    category: 'correctness',
    scope: 'document',
    defaultSeverity,
    docs,
    fix,
    check(ctx) {
      if (ctx.scope !== 'document') return;
      for (const d of validateSearchMethods(ctx.ast)) {
        if (d.code !== code) continue;
        ctx.report({ source: d.source, message: d.message });
      }
    },
  };
}

// No autofix: `method:` is only legal INSIDE the `searchable` clause, so
// rewriting `fuzzy: true` in place would produce a second `searchable`. The
// migration is a two-token edit an author makes deliberately (see the 0.12
// migration note); a plausible-looking wrong fix is worse than none.
const fuzzyDeprecated = searchMethodRule(
  'fuzzy-deprecated',
  DiagnosticCode.SearchFuzzyDeprecated,
  'warning',
  '`fuzzy` is replaced by the `searchable method:` attribute (grammar 0.12).',
);

const unknownMatchMethod = searchMethodRule(
  'unknown-match-method',
  DiagnosticCode.UnknownMatchMethod,
  'error',
  'A `searchable method:` must be EXACT, TYPOS(n) or TOKENS.',
);

const invalidMatchMethodArgument = searchMethodRule(
  'invalid-match-method-argument',
  DiagnosticCode.InvalidMatchMethodArgument,
  'error',
  'Only TYPOS takes an argument, and it must be a whole-number edit distance of 1..3.',
);

export const SEARCH_RULES: Rule[] = [
  fuzzyWithoutSearchable,
  duplicateSearchProperty,
  fuzzyDeprecated,
  unknownMatchMethod,
  invalidMatchMethodArgument,
];
