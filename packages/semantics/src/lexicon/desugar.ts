// SPDX-License-Identifier: Apache-2.0
// v4.4 TTR-M lexicon surface (RG-P4, RS-9 T1). Project inline `lexicon{}` sugar
// AND standalone `def term|pattern|example` entries into ONE canonical entry list
// (`CanonicalLexiconEntry`), then merge per target (union of forms). This mirrors
// the binding pattern — inline `binding:` and standalone `def er2db_*` synthesize
// into one symbol namespace tagged by source (see `mapping-synthesizer.ts`'s
// `mappingSource`); the difference is that vocabulary MERGES (many entries per
// target is normal), so the conflict signal is an exact-duplicate surface form,
// not a duplicate definition.

import type { Definition, Document, LexiconBlock, LexiconEntryDef, SourceLocation } from '@tatrman/parser';
import { DiagnosticCode } from '@tatrman/parser';
import { modelForKind, namespaceForKind, defaultNamespaceForSchema } from '../default-schema.js';
import type { CanonicalLexiconEntry, LexiconAnalysis, LexiconDiagnostic } from './model.js';

const LEXICON_ENTRY_KINDS = new Set(['term', 'pattern', 'example']);

/** The dotted target path an inline `lexicon{}` block resolves to (its carrier). */
function inlineTargetPath(def: Definition & { name: string }): string {
  const model = modelForKind(def.kind);
  const ns = namespaceForKind(def.kind) || defaultNamespaceForSchema(model) || def.kind;
  return `${model}.${ns}.${def.name}`;
}

/** Carriers that may hold an inline `lexicon{}` block (grammar 4.4). */
interface InlineCarrier {
  kind: string;
  name: string;
  lexicon?: LexiconBlock;
}

export function desugarLexicon(doc: Document): LexiconAnalysis {
  const entries: CanonicalLexiconEntry[] = [];
  const diagnostics: LexiconDiagnostic[] = [];

  const modelCode = doc.modelDirective?.modelCode;
  const isLexiconModel = modelCode === 'lexicon';
  const unitLocale = doc.modelDirective?.locale;

  // `locale` is only meaningful on a lexicon unit (RS-11).
  if (unitLocale !== undefined && !isLexiconModel) {
    diagnostics.push({
      code: DiagnosticCode.LexiconLocaleOnNonLexicon,
      message: `'locale ${unitLocale}' is only valid on a 'model lexicon' unit`,
      severity: 'warning',
      source: doc.modelDirective!.source,
    });
  }

  for (const def of doc.definitions) {
    if (LEXICON_ENTRY_KINDS.has(def.kind)) {
      collectCanonical(def as LexiconEntryDef, isLexiconModel, unitLocale, entries, diagnostics);
    } else if (isLexiconModel) {
      // A non-lexicon def in a `model lexicon` file is misplaced (world-validate
      // precedent: model-code and def-kind must agree).
      diagnostics.push({
        code: DiagnosticCode.LexiconWrongModelKind,
        message: `'def ${def.kind} ${def.name}' is not valid in a 'model lexicon' file`,
        severity: 'warning',
        source: def.source,
      });
    }

    const carrier = def as unknown as InlineCarrier;
    if (carrier.lexicon) {
      collectInline(def, carrier.lexicon, entries);
    }

    // RS-32 — legacy vocabulary forms are a second sugar source (entity `aliases`
    // + `search { aliases, keywords, patterns, examples }`). Only in non-lexicon
    // models: a `model lexicon` file carries canonical `def term|…` entries, not
    // data-bearing carriers.
    if (!isLexiconModel) {
      collectLegacy(def, entries, diagnostics);
      collectValueLabels(def, entries);
    }
  }

  const byTarget = mergeByTarget(entries, diagnostics);
  return { entries, byTarget, diagnostics };
}

/** A data-bearing carrier's legacy vocabulary surface (all optional). */
interface LegacyCarrier {
  kind: string;
  name: string;
  aliases?: string[];
  search?: {
    aliases?: string[];
    keywords?: { entries: Record<string, string[]>; source: SourceLocation };
    patterns?: string[];
    examples?: string[];
    descriptions?: { entries: Record<string, string[]>; source: SourceLocation };
    source: SourceLocation;
  };
}

/** A value-label entry (A4-β): a coded value's localized label + per-value aliases. */
interface ValueLabelLike {
  entries: Array<{ key: string; label: { entries: Record<string, string> }; aliases?: string[]; source: SourceLocation }>;
}
interface ValueLabelCarrier {
  kind: string;
  name: string;
  valueLabels?: ValueLabelLike;
  attributes?: Array<{ kind: string; name: string; valueLabels?: ValueLabelLike; source: SourceLocation }>;
}

/**
 * A4-β (RS-12) — a coded attribute's `valueLabels` are declared MEMBER vocabulary
 * and ride the snapshot beside lexicon terms (origin `valueLabels`). Each value's
 * localized label rides per-locale; per-value `aliases` ride the base locale.
 * Handles top-level `def attribute` and attributes nested in `entity`/`dimension`.
 */
function collectValueLabels(def: Definition, entries: CanonicalLexiconEntry[]): void {
  const carrier = def as unknown as ValueLabelCarrier;
  if (carrier.valueLabels) {
    emitValueLabels(inlineTargetPath(def as Definition & { name: string }), carrier.valueLabels, entries);
  }
  // Nested attributes (entity.attributes / dimension.attributes) — target is the
  // parent's ref plus the attribute name. `attributes` is an AttributeDef[] on
  // entity/dimension; on md2db binding kinds it is an attribute MAP (not iterable
  // here) — guard with Array.isArray.
  if (Array.isArray(carrier.attributes)) {
    const parentTarget = inlineTargetPath(def as Definition & { name: string });
    for (const attr of carrier.attributes) {
      if (attr.valueLabels) emitValueLabels(`${parentTarget}.${attr.name}`, attr.valueLabels, entries);
    }
  }
}

function emitValueLabels(target: string, vl: ValueLabelLike, entries: CanonicalLexiconEntry[]): void {
  for (const entry of vl.entries) {
    for (const [locale, text] of Object.entries(entry.label.entries)) {
      entries.push({ entryKind: 'term', name: `${entry.key}`, target, locale, forms: [text], origin: 'valueLabels', source: entry.source });
    }
    if (entry.aliases && entry.aliases.length > 0) {
      entries.push({ entryKind: 'term', name: `${entry.key}`, target, locale: undefined, forms: entry.aliases, origin: 'valueLabels', source: entry.source });
    }
  }
}

/**
 * RS-32 — migrate a def's legacy vocabulary forms into canonical entries (origin
 * `legacy`), emitting a named deprecation warning per form. `searchable`/`fuzzy`
 * are retrieval config and are NOT migrated. `search.descriptions` folds into
 * `description` and is handled separately (T3).
 */
function collectLegacy(def: Definition, entries: CanonicalLexiconEntry[], diagnostics: LexiconDiagnostic[]): void {
  const carrier = def as unknown as LegacyCarrier;
  const target = inlineTargetPath(def as Definition & { name: string });
  const defSource = (def as { source: SourceLocation }).source;

  const pushTerm = (forms: string[], locale: string | undefined, source: SourceLocation): void => {
    if (forms.length === 0) return;
    entries.push({ entryKind: 'term', name: carrier.name, target, locale, forms, origin: 'legacy', source });
  };

  // entity `aliases: [...]` → term (base locale).
  if (carrier.aliases && carrier.aliases.length > 0) {
    pushTerm(carrier.aliases, undefined, defSource);
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyAliases,
      message: `'aliases' on '${def.kind} ${def.name}' is deprecated — declare a lexicon 'term' for '${target}' instead`,
      severity: 'warning',
      source: defSource,
    });
  }

  const search = carrier.search;
  if (!search) return;
  const searchSource = search.source;

  // `search { aliases }` → term (base locale).
  if (search.aliases && search.aliases.length > 0) {
    pushTerm(search.aliases, undefined, searchSource);
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyAliases,
      message: `'search { aliases }' on '${def.kind} ${def.name}' is deprecated — declare a lexicon 'term' for '${target}' instead`,
      severity: 'warning',
      source: searchSource,
    });
  }

  // `search { keywords: { <locale>: [...] } }` → one term per locale.
  if (search.keywords && Object.keys(search.keywords.entries).length > 0) {
    for (const [locale, forms] of Object.entries(search.keywords.entries)) {
      pushTerm(forms, locale, search.keywords.source);
    }
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyKeywords,
      message: `'search { keywords }' on '${def.kind} ${def.name}' is deprecated — declare locale-keyed lexicon 'term' entries for '${target}' instead`,
      severity: 'warning',
      source: search.keywords.source,
    });
  }

  // `search { patterns }` → pattern entries (one per regex, base locale).
  if (search.patterns && search.patterns.length > 0) {
    for (const match of search.patterns) {
      entries.push({ entryKind: 'pattern', name: carrier.name, target, locale: undefined, match, origin: 'legacy', source: searchSource });
    }
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyPatterns,
      message: `'search { patterns }' on '${def.kind} ${def.name}' is deprecated — declare lexicon 'pattern' entries for '${target}' instead`,
      severity: 'warning',
      source: searchSource,
    });
  }

  // `search { examples }` → example entries (one per utterance, base locale).
  if (search.examples && search.examples.length > 0) {
    for (const text of search.examples) {
      entries.push({ entryKind: 'example', name: carrier.name, target, locale: undefined, text, origin: 'legacy', source: searchSource });
    }
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyExamples,
      message: `'search { examples }' on '${def.kind} ${def.name}' is deprecated — declare lexicon 'example' entries for '${target}' instead`,
      severity: 'warning',
      source: searchSource,
    });
  }

  // `search { descriptions }` → fold into `description` (RS-32 T3: no consumer
  // reads it distinctly). Prose, not vocabulary — so NO lexicon entry, only the
  // deprecation pointing at the single `description` home.
  if (search.descriptions && Object.keys(search.descriptions.entries).length > 0) {
    diagnostics.push({
      code: DiagnosticCode.LexiconLegacyDescriptions,
      message: `'search { descriptions }' on '${def.kind} ${def.name}' is deprecated — use the single 'description' property (no consumer reads it distinctly, RS-32)`,
      severity: 'warning',
      source: search.descriptions.source,
    });
  }
}

function collectCanonical(
  def: LexiconEntryDef,
  isLexiconModel: boolean,
  unitLocale: string | undefined,
  entries: CanonicalLexiconEntry[],
  diagnostics: LexiconDiagnostic[],
): void {
  // Canonical entries live in `model lexicon` (the inline sugar is the in-model
  // carrier elsewhere).
  if (!isLexiconModel) {
    diagnostics.push({
      code: DiagnosticCode.LexiconWrongModelKind,
      message: `'def ${def.kind} ${def.name}' is only valid in a 'model lexicon' file (use inline 'lexicon { … }' sugar on the target otherwise)`,
      severity: 'warning',
      source: def.source,
    });
  }

  if (!def.target) {
    diagnostics.push({
      code: DiagnosticCode.LexiconMissingTarget,
      message: `lexicon ${def.kind} '${def.name}' has no 'for:' target`,
      severity: 'error',
      source: def.source,
    });
  }

  // Per-kind required field (term→forms, pattern→match, example→text).
  const missingField =
    (def.kind === 'term' && (!def.forms || def.forms.length === 0)) ||
    (def.kind === 'pattern' && def.match === undefined) ||
    (def.kind === 'example' && def.text === undefined);
  if (missingField) {
    const field = def.kind === 'term' ? 'forms' : def.kind === 'pattern' ? 'match' : 'text';
    diagnostics.push({
      code: DiagnosticCode.LexiconEntryFieldMissing,
      message: `lexicon ${def.kind} '${def.name}' is missing its '${field}:'`,
      severity: 'warning',
      source: def.source,
    });
  }

  entries.push({
    entryKind: def.kind,
    name: def.name,
    target: def.target?.path ?? '',
    targetRef: def.target,
    locale: isLexiconModel ? unitLocale : undefined,
    forms: def.forms,
    match: def.match,
    text: def.text,
    origin: 'canonical',
    source: def.source,
  });
}

function collectInline(def: Definition, block: LexiconBlock, entries: CanonicalLexiconEntry[]): void {
  const target = inlineTargetPath(def as Definition & { name: string });
  // Inline entries default to the base/deployment locale (RS-11) — locale left
  // undefined; the loader resolves it against the deployment default.
  if (block.terms && block.terms.length > 0) {
    entries.push({
      entryKind: 'term',
      name: (def as { name: string }).name,
      target,
      locale: undefined,
      forms: block.terms,
      origin: 'inline',
      source: block.source,
    });
  }
  for (const pattern of block.patterns ?? []) {
    entries.push({
      entryKind: 'pattern',
      name: (def as { name: string }).name,
      target,
      locale: undefined,
      match: pattern,
      origin: 'inline',
      source: block.source,
    });
  }
  for (const example of block.examples ?? []) {
    entries.push({
      entryKind: 'example',
      name: (def as { name: string }).name,
      target,
      locale: undefined,
      text: example,
      origin: 'inline',
      source: block.source,
    });
  }
}

/**
 * Group entries by target and union their forms; warn on an exact-duplicate
 * surface form for one target (the merge-conflict signal — the vocabulary
 * analogue of a duplicate binding).
 */
function mergeByTarget(
  entries: CanonicalLexiconEntry[],
  diagnostics: LexiconDiagnostic[],
): Map<string, CanonicalLexiconEntry[]> {
  const byTarget = new Map<string, CanonicalLexiconEntry[]>();
  // Per target+locale, the surface forms already contributed (dup detection).
  const seenForms = new Map<string, Set<string>>();

  for (const entry of entries) {
    if (!entry.target) continue;
    const group = byTarget.get(entry.target) ?? [];
    group.push(entry);
    byTarget.set(entry.target, group);

    if (entry.entryKind !== 'term' || !entry.forms) continue;
    const key = `${entry.target} ${entry.locale ?? ''}`;
    const seen = seenForms.get(key) ?? new Set<string>();
    for (const form of entry.forms) {
      if (seen.has(form)) {
        diagnostics.push({
          code: DiagnosticCode.LexiconDuplicateForm,
          message: `surface form '${form}' is declared more than once for target '${entry.target}'`,
          severity: 'warning',
          source: entry.source,
        });
      } else {
        seen.add(form);
      }
    }
    seenForms.set(key, seen);
  }

  return byTarget;
}
