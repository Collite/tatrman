// SPDX-License-Identifier: Apache-2.0
import { DiagnosticCode } from '@tatrman/parser';
import type { Definition, LocalizedString } from '@tatrman/parser';
import type { Rule } from '../rule.js';

// NLS-P10 (grammar 0.13, ⚑GXP-D7) — `description:` accepts the localised map form
// `{ en: "…", cs: "…" }`. The PARSER stays mechanical about it (repo invariant), so
// the two ways an author can write a map that serves nobody are lint rules:
//
//   * `description: {}`            — names no locale at all
//   * `description: { en: … }` in a unit whose header declares `locale cs`
//
// Neither is fatal: Veles' D7 chain (requested → plain → `en` → first → "") always
// yields something. They are warnings because the authored intent is visibly
// unmet, which is exactly what a linter is for.

/**
 * Every localized description reachable from a definition, at any nesting depth.
 *
 * Deliberately a recursive scan rather than a per-kind list of nested arrays. The
 * first cut of this rule enumerated `table`/`view` columns and `entity` attributes,
 * and silently missed everything else that carries `descriptionProperty` under a
 * parent: `dimension.attributes`, `procedure.resultColumns`, a table's
 * `indices`/`constraints`, inline `cubelet.measures`. The grammar puts
 * `descriptionProperty` on ~30 def kinds at every depth, so an enumeration is a
 * list that goes stale the next time one is nested — the scan cannot.
 *
 * `source` is skipped (a `SourceLocation`, never a carrier and the widest subtree),
 * and only the `descriptionLocalized` key is collected — `displayLabel`, `label`
 * and `value_labels` are the same LocalizedString shape but a different property,
 * and neither rule has anything to say about them.
 */
function* localizedDescriptionsOf(def: Definition): Iterable<LocalizedString> {
  function* walk(node: unknown): Iterable<LocalizedString> {
    if (Array.isArray(node)) {
      for (const item of node) yield* walk(item);
      return;
    }
    if (node === null || typeof node !== 'object') return;
    for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
      if (key === 'source' || key === 'leadingTrivia' || key === 'trailingTrivia') continue;
      if (key === 'descriptionLocalized') {
        if (isLocalizedString(value)) yield value;
        continue;
      }
      yield* walk(value);
    }
  }
  yield* walk(def);
}

function isLocalizedString(v: unknown): v is LocalizedString {
  return (
    typeof v === 'object' &&
    v !== null &&
    (v as { kind?: unknown }).kind === 'localizedString' &&
    typeof (v as { entries?: unknown }).entries === 'object'
  );
}

const localizedDescriptionEmpty: Rule = {
  id: 'localized-description-empty',
  code: DiagnosticCode.LocalizedDescriptionEmpty,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A localized `description: {}` names no locale, so no reader can ever serve it.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const ls of localizedDescriptionsOf(def)) {
        if (Object.keys(ls.entries).length > 0) continue;
        ctx.report({
          source: ls.source,
          message:
            'localized description is empty — name at least one locale (`description: { en: "…" }`) or use the plain string form',
        });
      }
    }
  },
};

const localizedDescriptionMissingLocale: Rule = {
  id: 'localized-description-missing-locale',
  code: DiagnosticCode.LocalizedDescriptionMissingLocale,
  category: 'correctness',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: "A localized description omits the locale the unit header declares (`model … locale cs`).",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    // The only "declared locales" notion the language has today is the unit-level
    // header (v4.4 `model <code> locale <id>`). With no header there is nothing to
    // check against — the rule stays silent rather than inventing a house locale.
    const declared = ctx.ast.modelDirective?.locale;
    if (!declared) return;
    for (const def of ctx.ast.definitions) {
      for (const ls of localizedDescriptionsOf(def)) {
        const keys = Object.keys(ls.entries);
        if (keys.length === 0) continue; // the empty-map rule owns that case
        if (keys.includes(declared)) continue;
        ctx.report({
          source: ls.source,
          message: `localized description does not carry the unit's declared locale '${declared}' (has: ${keys.join(', ')})`,
        });
      }
    }
  },
};

export const DESCRIPTION_RULES: Rule[] = [
  localizedDescriptionEmpty,
  localizedDescriptionMissingLocale,
];
