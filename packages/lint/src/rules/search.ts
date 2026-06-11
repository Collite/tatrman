import { DiagnosticCode } from '@modeler/parser';
import { insertEdit, removeLineEdit } from '@modeler/edit';
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

export const SEARCH_RULES: Rule[] = [fuzzyWithoutSearchable, duplicateSearchProperty];
