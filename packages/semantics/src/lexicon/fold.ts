// SPDX-License-Identifier: Apache-2.0
// MH T1 (mention homonymy) — the *collision* fold.
//
// Two refs meet at runtime iff their folded forms are equal: the resolver's
// anchor index is keyed by `org.tatrman.text.Normalization.fold` (lowercase →
// NFD → strip combining marks), NOT by the compiler's `TermNormalizer.normalize`
// (which preserves diacritics and is the archive's merge key). So the
// collision lint must fold the way the *index* does — `vyroba` and `výroba`
// ARE a collision. See contracts §1 of
// `project/server/features/mention-homonymy/contracts.md`.
//
// Three implementations share one parity table (`fold-parity.json`):
//   - here (`@tatrman/semantics`, read by `@tatrman/lint`);
//   - `org.tatrman.ttr.lexicon.TermNormalizer.fold` (ttr-lexicon);
//   - `org.tatrman.text.Normalization.fold` (tatrman-server, the index itself).

/**
 * Fold a surface form to its collision key: lowercase, strip combining marks,
 * trim, and collapse internal whitespace to a single space.
 *
 * Only *combining* marks are stripped — a precomposed letter with no canonical
 * decomposition (`Đ`, `Ł`) survives, exactly as `Normalization.fold` leaves it.
 */
export function foldForCollision(s: string): string {
  return s
    .normalize('NFC')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .trim()
    .replace(WHITESPACE, ' ');
}

/**
 * Java's `\s` — `[ \t\n\x0B\f\r]` — and NOT JavaScript's, which also matches U+00A0 and the
 * Unicode space separators.
 *
 * The two twins have to agree about whitespace, and the tie-breaker is which one the RUNTIME
 * follows: `org.tatrman.text.Normalization.fold` does not collapse whitespace at all, and
 * `SpanProposal` splits a folded anchor on the literal `' '`. So a non-breaking space survives
 * into the anchor index — and `TermNormalizer.fold` keeps it too, because its whitespace rule
 * lives in `normalize`, the archive's merge key, which is held byte-identical to the server's
 * canonical form. Collapsing NBSP here would make this lint report collisions that neither the
 * compiler nor the matcher has. NBSP after a one-letter Czech preposition (`v z k s o u`) is
 * ordinary typography and survives any paste, so this is not a theoretical difference.
 */
const WHITESPACE = /[ \t\n\v\f\r]+/g;
