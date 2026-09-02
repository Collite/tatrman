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
    .replace(/\s+/g, ' ');
}
