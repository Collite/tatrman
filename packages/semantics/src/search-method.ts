// SPDX-License-Identifier: Apache-2.0
/**
 * RV-P1.5 (grammar 0.12, RV-31/RV-32) — the MEANING of `searchable` and its match
 * method. The parser is mechanical: it captures `searchable` (with an optional
 * boolean) and a `method:` identifier with an optional numeric argument. This
 * module owns everything else — the closed EXACT/TYPOS/TOKENS vocabulary, the
 * arity rule (only TYPOS takes a distance), the RV-32 default of `TYPOS(1)`, and
 * the `fuzzy` deprecation that maps the old boolean onto the new attribute.
 *
 * `searchable` is the lexicon INCLUSION marker (RV-31 source (3): "include this
 * carrier's content in the lexicon"), so a carrier that is not searchable has no
 * effective method at all — {@link effectiveMatchMethod} returns `undefined`
 * rather than a default nobody asked for.
 *
 * Resolution NEVER fails: a rejected method degrades to the default so downstream
 * consumers (the lexicon compiler, lex-matcher) keep working on a model whose
 * diagnostics an author has not fixed yet. The diagnostics are the signal; the
 * fallback is the behaviour.
 *
 * Cross-target contract: the three `DiagnosticCode`s are mirrored in Kotlin
 * (`ttr-semantics` `Validator`) and Python (`semantics/validator.py`), and the
 * `fuzzy` deprecation rides the portable conformance rule subset.
 */
import { DiagnosticCode } from '@tatrman/parser';
import type { Definition, Document, SearchBlock, SourceLocation } from '@tatrman/parser';

/** The closed RV-32 vocabulary. */
export type MatchMethodName = 'EXACT' | 'TYPOS' | 'TOKENS';

/** RV-32: the default method for an included carrier that declares none. */
export const DEFAULT_TYPOS_DISTANCE = 1;

export interface EffectiveMatchMethod {
  name: MatchMethodName;
  /** Maximum edit distance — TYPOS only. */
  maxDistance?: number;
  /**
   * `authored` — a valid `method:` attribute · `legacy-fuzzy` — derived from the
   * deprecated boolean · `default` — the RV-32 default (also the fallback for a
   * rejected method).
   */
  origin: 'authored' | 'legacy-fuzzy' | 'default';
  source: SourceLocation;
}

export interface SearchMethodDiagnostic {
  code: DiagnosticCode;
  message: string;
  severity: 'warning' | 'error';
  source: SourceLocation;
}

const KNOWN: ReadonlySet<string> = new Set<MatchMethodName>(['EXACT', 'TYPOS', 'TOKENS']);

function isValidDistance(n: number | undefined): boolean {
  return n === undefined || (Number.isInteger(n) && n > 0);
}

/**
 * The method a carrier's content is matched with, or `undefined` when the carrier
 * is not included in the lexicon.
 *
 * Precedence: an authored `method:` wins; then the deprecated `fuzzy` boolean
 * (`true` → `TYPOS(1)`, `false` → `EXACT` — the authored "no fuzzy" intent
 * survives the bump); then the RV-32 default.
 */
export function effectiveMatchMethod(search: SearchBlock | undefined): EffectiveMatchMethod | undefined {
  if (!search || search.searchable !== true) return undefined;
  const fallback: EffectiveMatchMethod = {
    name: 'TYPOS',
    maxDistance: DEFAULT_TYPOS_DISTANCE,
    origin: 'default',
    source: search.source,
  };

  const authored = search.method;
  if (authored) {
    const name = authored.name.toUpperCase();
    if (!KNOWN.has(name)) return fallback;
    if (name === 'TYPOS') {
      if (!isValidDistance(authored.argument)) return fallback;
      return {
        name: 'TYPOS',
        maxDistance: authored.argument ?? DEFAULT_TYPOS_DISTANCE,
        origin: 'authored',
        source: authored.source,
      };
    }
    // EXACT / TOKENS: a stray argument is diagnosed, not obeyed.
    return { name: name as MatchMethodName, origin: 'authored', source: authored.source };
  }

  if (search.fuzzy !== undefined) {
    return search.fuzzy
      ? { name: 'TYPOS', maxDistance: DEFAULT_TYPOS_DISTANCE, origin: 'legacy-fuzzy', source: search.source }
      : { name: 'EXACT', origin: 'legacy-fuzzy', source: search.source };
  }

  return fallback;
}

/** The message text is a cross-target contract — keep Kotlin/Python in step. */
export function fuzzyDeprecationMessage(fuzzy: boolean): string {
  const replacement = fuzzy ? `TYPOS(${DEFAULT_TYPOS_DISTANCE})` : 'EXACT';
  return (
    `'fuzzy: ${fuzzy}' is deprecated (grammar 0.12) — replace it with ` +
    `'searchable method: ${replacement}'`
  );
}

/** Validate the `searchable`/`method`/`fuzzy` surface of one document. */
export function validateSearchMethods(doc: Document): SearchMethodDiagnostic[] {
  const diagnostics: SearchMethodDiagnostic[] = [];
  for (const def of doc.definitions) {
    for (const sb of searchBlocksOf(def)) validateBlock(sb, diagnostics);
  }
  return diagnostics;
}

function validateBlock(search: SearchBlock, diagnostics: SearchMethodDiagnostic[]): void {
  const authored = search.method;
  if (authored) {
    const name = authored.name.toUpperCase();
    if (!KNOWN.has(name)) {
      diagnostics.push({
        code: DiagnosticCode.UnknownMatchMethod,
        message:
          `unknown match method '${authored.name}' — expected EXACT, TYPOS(n) or TOKENS; ` +
          `falling back to TYPOS(${DEFAULT_TYPOS_DISTANCE})`,
        severity: 'error',
        source: authored.source,
      });
    } else if (name === 'TYPOS') {
      if (!isValidDistance(authored.argument)) {
        diagnostics.push({
          code: DiagnosticCode.InvalidMatchMethodArgument,
          message:
            `match method 'TYPOS' takes a positive whole-number edit distance; got ` +
            `'${authored.argument}' — falling back to TYPOS(${DEFAULT_TYPOS_DISTANCE})`,
          severity: 'error',
          source: authored.source,
        });
      }
    } else if (authored.argument !== undefined) {
      diagnostics.push({
        code: DiagnosticCode.InvalidMatchMethodArgument,
        message: `match method '${name}' takes no argument`,
        severity: 'error',
        source: authored.source,
      });
    }
  }

  // The deprecation fires on the authored `fuzzy` whether or not a `method`
  // overrides it — the property is going away either way.
  if (search.fuzzy !== undefined) {
    diagnostics.push({
      code: DiagnosticCode.SearchFuzzyDeprecated,
      message: fuzzyDeprecationMessage(search.fuzzy),
      severity: 'warning',
      source: search.source,
    });
  }
}

/**
 * Every `search { … }` block reachable from a definition: the def's own, plus
 * those on nested columns/attributes. (Mirrors `@tatrman/lint`'s
 * `searchBlocksOf`, kept local so `semantics` does not depend on `lint`.)
 */
function searchBlocksOf(def: Definition): SearchBlock[] {
  const out: SearchBlock[] = [];
  const own = (def as { search?: SearchBlock }).search;
  if (own) out.push(own);
  const holder = def as unknown as { attributes?: unknown; columns?: unknown };
  for (const field of [holder.attributes, holder.columns]) {
    if (!Array.isArray(field)) continue;
    for (const child of field) {
      const nested = (child as { search?: SearchBlock } | undefined)?.search;
      if (nested) out.push(nested);
    }
  }
  return out;
}
