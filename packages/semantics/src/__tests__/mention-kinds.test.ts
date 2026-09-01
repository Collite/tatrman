// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import {
  MENTION_KIND_MEASURE,
  MENTION_KIND_ATTRIBUTE,
  MENTION_KIND_ENTITY,
  MENTION_KIND_ENTITY_WITH_MEASURES,
  mentionKindOf,
} from '../semantics-block/mention-kinds.js';

// contracts §5, exhaustively. Twin of the Kotlin `MentionKindsSpec`; the Kotlin
// `VocabularyParitySpec` asserts the four strings agree across the two files.
describe('MS — MentionKinds, the one derivation table', () => {
  it('the four kind strings are exactly the contracts §5 values', () => {
    expect(MENTION_KIND_MEASURE).toBe('measure');
    expect(MENTION_KIND_ATTRIBUTE).toBe('attribute');
    expect(MENTION_KIND_ENTITY).toBe('entity');
    expect(MENTION_KIND_ENTITY_WITH_MEASURES).toBe('entity_with_measures');
  });

  it('all eight fact combinations derive their contracts §5 kind', () => {
    // An ATTRIBUTE ignores `ownerHasMeasures`: what makes it a measure is being listed, not
    // living on an entity that has some. An ENTITY ignores `listedAsMeasure`, which is a fact
    // about attributes — stated here rather than left to be inferred from the branches.
    const table: Array<[boolean, boolean, boolean, string]> = [
      [true, true, true, MENTION_KIND_MEASURE],
      [true, true, false, MENTION_KIND_MEASURE],
      // MS-R4: absence is the answer, not a missing declaration.
      [true, false, true, MENTION_KIND_ATTRIBUTE],
      [true, false, false, MENTION_KIND_ATTRIBUTE],
      [false, true, true, MENTION_KIND_ENTITY_WITH_MEASURES],
      [false, false, true, MENTION_KIND_ENTITY_WITH_MEASURES],
      [false, true, false, MENTION_KIND_ENTITY],
      [false, false, false, MENTION_KIND_ENTITY],
    ];
    expect(table).toHaveLength(8);
    for (const [isAttribute, listedAsMeasure, ownerHasMeasures, expected] of table) {
      expect(
        mentionKindOf({
          isAttribute,
          ownerRef: isAttribute ? 'er.entity.sales' : undefined,
          listedAsMeasure,
          ownerHasMeasures,
        }),
        `isAttribute=${isAttribute} listedAsMeasure=${listedAsMeasure} ownerHasMeasures=${ownerHasMeasures}`,
      ).toBe(expected);
    }
  });

  it('ownerRef is carried, not consulted', () => {
    // MS's ⛔ rule is that nothing anywhere derives a kind from a ref STRING; the cheapest
    // way to keep that true is for the table to be blind to it.
    const listed = { isAttribute: true, listedAsMeasure: true, ownerHasMeasures: true };
    expect(mentionKindOf({ ...listed, ownerRef: 'er.entity.sales' })).toBe(mentionKindOf(listed));
    expect(mentionKindOf({ ...listed, ownerRef: 'op:whatever.measure' })).toBe(mentionKindOf(listed));
  });
});
