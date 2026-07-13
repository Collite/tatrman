// SPDX-License-Identifier: Apache-2.0
import { DiagnosticCode } from '@tatrman/parser';
import type { Document } from '@tatrman/parser';
import { desugarLexicon } from '@tatrman/semantics';
import type { LexiconAnalysis } from '@tatrman/semantics';
import type { Rule, DocumentRuleContext } from '../rule.js';

// v4.4 TTR-M lexicon surface (RG-P4) — surface the ttr-semantics lexicon desugar
// diagnostics (placement / missing target / missing field / duplicate form /
// misplaced locale) as lint rules. One code per rule id (per-rule severity, the
// semantics-rules pattern); the desugar analysis runs once per document and is
// memoised. Referential integrity (`for:` target resolution) rides the generic
// reference resolver — it is NOT re-checked here.

const cache = new WeakMap<Document, LexiconAnalysis>();

function analysisFor(ctx: DocumentRuleContext): LexiconAnalysis {
  const memo = cache.get(ctx.ast);
  if (memo) return memo;
  const analysis = desugarLexicon(ctx.ast);
  cache.set(ctx.ast, analysis);
  return analysis;
}

function lexRule(id: string, code: DiagnosticCode, defaultSeverity: 'error' | 'warning', docs: string): Rule {
  return {
    id,
    code,
    category: 'lexicon',
    scope: 'document',
    defaultSeverity,
    docs,
    check(ctx) {
      if (ctx.scope !== 'document') return;
      for (const d of analysisFor(ctx).diagnostics) {
        if (d.code !== code) continue;
        ctx.report({ source: d.source, message: d.message });
      }
    },
  };
}

export const LEXICON_RULES: Rule[] = [
  lexRule('lexicon-wrong-model-kind', DiagnosticCode.LexiconWrongModelKind, 'warning', 'A `term`/`pattern`/`example` outside `model lexicon`, or a non-lexicon def inside one.'),
  lexRule('lexicon-missing-target', DiagnosticCode.LexiconMissingTarget, 'error', 'A lexicon entry has no `for:` target.'),
  lexRule('lexicon-entry-field-missing', DiagnosticCode.LexiconEntryFieldMissing, 'warning', 'A `term` needs `forms`, a `pattern` needs `match`, an `example` needs `text`.'),
  lexRule('lexicon-duplicate-form', DiagnosticCode.LexiconDuplicateForm, 'warning', 'The same surface form is declared more than once for one target.'),
  lexRule('lexicon-locale-on-non-lexicon', DiagnosticCode.LexiconLocaleOnNonLexicon, 'warning', 'A `locale` unit header on a model that is not `lexicon`.'),
];
