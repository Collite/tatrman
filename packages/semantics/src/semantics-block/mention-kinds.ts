// SPDX-License-Identifier: Apache-2.0
// MS (contracts §5) — THE derivation table: declared model facts → the `objectKind` a
// resolver reads.
//
// The producing implementation is the Kotlin twin (`MentionKinds.kt`): the lexicon
// compiler calls it and writes the result into the archive's `targets` map. This mirror
// exists so the table can be read, tested and kept honest on the TS side too — the
// Kotlin `VocabularyParitySpec` asserts the four strings agree across the two files.
//
// ⛔ A kind is NEVER derived from a ref STRING, here or anywhere. `ownerRef` is carried so
// the caller can record who owns an attribute; `mentionKindOf` does not read it, and a
// test pins that it cannot. `isAttribute` comes from which model node the ref resolved to;
// `listedAsMeasure` from the owner's declared `measures:` list. Both are graph facts.
//
// Versioned by SEMANTICS_VOCABULARY_VERSION, like the vocabulary it sits beside.

export const MENTION_KIND_MEASURE = 'measure';
export const MENTION_KIND_ATTRIBUTE = 'attribute';
export const MENTION_KIND_ENTITY = 'entity';
export const MENTION_KIND_ENTITY_WITH_MEASURES = 'entity_with_measures';

/**
 * The four values cross a wire, so consumers of the string tolerate unknowns (J-v2): a
 * reader that meets a kind it does not know treats the ref as unclassified, not as an error.
 */
export type MentionKind =
  | typeof MENTION_KIND_MEASURE
  | typeof MENTION_KIND_ATTRIBUTE
  | typeof MENTION_KIND_ENTITY
  | typeof MENTION_KIND_ENTITY_WITH_MEASURES;

/** Facts about one model object, as the model graph states them (NEVER from ref strings). */
export interface ObjectFacts {
  /** attribute/column (true) vs entity/table (false). */
  readonly isAttribute: boolean;
  /** The owning entity's targetRef; undefined for entities. Carried for the archive, not consulted. */
  readonly ownerRef?: string;
  /** This attribute appears in its owner's `measures:` list. */
  readonly listedAsMeasure?: boolean;
  /** The entity's `measures:` list is non-empty. */
  readonly ownerHasMeasures?: boolean;
}

/** Total and closed — every ObjectFacts maps to one of the four kinds. */
export function mentionKindOf(f: ObjectFacts): MentionKind {
  if (f.isAttribute && f.listedAsMeasure) return MENTION_KIND_MEASURE;
  // MS-R4: an attribute that is not listed IS an attribute. Absence is the answer, not a
  // missing declaration to be guessed around.
  if (f.isAttribute) return MENTION_KIND_ATTRIBUTE;
  if (f.ownerHasMeasures) return MENTION_KIND_ENTITY_WITH_MEASURES;
  return MENTION_KIND_ENTITY;
}
