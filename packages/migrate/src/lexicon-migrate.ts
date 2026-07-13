// SPDX-License-Identifier: Apache-2.0
// v4.4 S2 (RS-32) — the `migrate-lexicon` codemod. Rewrites a carrier's legacy
// `search { … }` vocabulary sub-properties into a sibling inline `lexicon { … }`
// block, leaving `searchable`/`fuzzy` behind as the slimmed retrieval config.
//
// AST-span based (NOT regex over nested braces): the `SearchBlock` carries a
// precise `source` span, so the whole `search { … }` block is replaced as one
// unit — trivia outside the block is untouched, and the transform is idempotent
// (a migrated block has no legacy vocab left, so a second pass is a no-op).
//
// Scope + guided-manual cases (documented; the deprecation warnings point the
// way for these):
//   • `search { aliases }`  → `lexicon { terms }`
//   • `search { patterns }` → `lexicon { patterns }`
//   • `search { examples }` → `lexicon { examples }`
//   • `search { keywords: { <locale>: [...] } }` is LOCALE-KEYED — inline
//     `lexicon` is base-locale only (RS-11), so keywords are LEFT IN PLACE in the
//     slimmed `search {}` block for manual migration to a `model lexicon locale
//     <id>` file. Entity top-level `aliases` is likewise left to the warning.

import { parseString } from '@tatrman/parser';
import type { SearchBlock, SourceLocation } from '@tatrman/parser';

interface Edit {
  start: number;
  end: number;
  text: string;
}

/** The legacy flat forms this codemod folds into an inline `lexicon` block. */
function hasMigratableVocab(s: SearchBlock): boolean {
  return (
    (s.aliases?.length ?? 0) > 0 ||
    (s.patterns?.length ?? 0) > 0 ||
    (s.examples?.length ?? 0) > 0
  );
}

/** The `search {}` block still needed after migration (searchable/fuzzy/keywords). */
function hasResidualSearch(s: SearchBlock): boolean {
  return (
    s.searchable !== undefined ||
    s.fuzzy !== undefined ||
    (s.keywords !== undefined && Object.keys(s.keywords.entries).length > 0)
  );
}

function jsonList(items: string[]): string {
  return `[${items.map((i) => JSON.stringify(i)).join(', ')}]`;
}

/** The indentation (leading whitespace) of the line the block opens on. */
function indentOf(text: string, offsetStart: number): string {
  const lineStart = text.lastIndexOf('\n', offsetStart - 1) + 1;
  const prefix = text.slice(lineStart, offsetStart);
  const m = prefix.match(/^[ \t]*/);
  return m ? m[0] : '';
}

function renderSlimSearch(s: SearchBlock): string {
  const parts: string[] = [];
  if (s.searchable !== undefined) parts.push(`searchable: ${s.searchable}`);
  if (s.fuzzy !== undefined) parts.push(`fuzzy: ${s.fuzzy}`);
  if (s.keywords && Object.keys(s.keywords.entries).length > 0) {
    // Locale-keyed — kept for manual migration to a `model lexicon locale` file.
    const kw = Object.entries(s.keywords.entries)
      .map(([loc, forms]) => `${loc}: ${jsonList(forms)}`)
      .join(', ');
    parts.push(`keywords: { ${kw} }`);
  }
  return `search { ${parts.join(', ')} }`;
}

function renderLexicon(s: SearchBlock): string {
  const parts: string[] = [];
  const terms = s.aliases ?? [];
  if (terms.length > 0) parts.push(`terms: ${jsonList(terms)}`);
  if (s.patterns?.length) parts.push(`patterns: ${jsonList(s.patterns)}`);
  if (s.examples?.length) parts.push(`examples: ${jsonList(s.examples)}`);
  return `lexicon { ${parts.join(', ')} }`;
}

/**
 * Rewrite a model file's legacy `search {}` vocabulary into inline `lexicon {}`
 * sugar. Pure, deterministic, idempotent. Returns the text unchanged when there
 * is nothing to migrate. Only `.ttrm` model content is meaningful here.
 */
export function migrateLexicon(text: string, fileLabel = '<migrate>'): string {
  const { ast } = parseString(text, fileLabel);
  if (!ast) return text;

  const edits: Edit[] = [];
  const visit = (search: SearchBlock | undefined): void => {
    if (!search || !hasMigratableVocab(search)) return;
    const loc: SourceLocation = search.source;
    // `search.source` spans only the `{ … }` body — extend the edit start left to
    // swallow the `search` keyword (+ optional `:` propSep + whitespace) so the
    // whole `search { … }` property is replaced, not just its braces.
    const before = text.slice(0, loc.offsetStart);
    const kw = before.match(/search[ \t]*:?[ \t\r\n]*$/);
    const start = kw ? before.length - kw[0].length : loc.offsetStart;
    const indent = indentOf(text, start);
    const lexicon = renderLexicon(search);
    const replacement = hasResidualSearch(search)
      ? `${renderSlimSearch(search)},\n${indent}${lexicon}`
      : lexicon;
    edits.push({ start, end: loc.offsetEnd, text: replacement });
  };

  for (const def of ast.definitions) {
    const carrier = def as { search?: SearchBlock; attributes?: Array<{ search?: SearchBlock }>; columns?: Array<{ search?: SearchBlock }> };
    visit(carrier.search);
    // Inline attribute/column defs (nested) carry their own search blocks.
    for (const child of carrier.attributes ?? []) visit(child.search);
    for (const child of carrier.columns ?? []) visit(child.search);
  }

  if (edits.length === 0) return text;
  // Apply right-to-left so earlier offsets stay valid.
  edits.sort((a, b) => b.start - a.start);
  let out = text;
  for (const e of edits) {
    out = out.slice(0, e.start) + e.text + out.slice(e.end);
  }
  return out;
}
