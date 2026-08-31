// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.semanticsblock.MentionKinds

/**
 * contracts §5 — THE derivation table, exhaustively. Eight rows: every combination of
 * `isAttribute` × `listedAsMeasure` × `ownerHasMeasures`, including the ones where a field
 * is a don't-care, because a table claimed to be TOTAL has to be shown to be.
 *
 * The four string values are pinned separately: they cross a wire (the archive's
 * `TargetFacts.objectKind`, then the resolver), so a rename is a breaking change and should
 * read like one in the diff.
 */
class MentionKindsSpec :
    StringSpec({

        "the four kind strings are exactly the contracts §5 values" {
            MentionKinds.MEASURE shouldBe "measure"
            MentionKinds.ATTRIBUTE shouldBe "attribute"
            MentionKinds.ENTITY shouldBe "entity"
            MentionKinds.ENTITY_WITH_MEASURES shouldBe "entity_with_measures"
        }

        "all eight fact combinations derive their contracts §5 kind" {
            // isAttribute · listedAsMeasure · ownerHasMeasures → kind
            //
            // An ATTRIBUTE ignores `ownerHasMeasures`: what makes it a measure is being listed,
            // not living on an entity that has some. An ENTITY ignores `listedAsMeasure`, which
            // is a fact about attributes and is meaningless on an entity — stated here rather
            // than left to be inferred from the `when`.
            val table =
                listOf(
                    Triple(true, true, true) to MentionKinds.MEASURE,
                    Triple(true, true, false) to MentionKinds.MEASURE,
                    // MS-R4: absence is the answer, not a missing declaration.
                    Triple(true, false, true) to MentionKinds.ATTRIBUTE,
                    Triple(true, false, false) to MentionKinds.ATTRIBUTE,
                    Triple(false, true, true) to MentionKinds.ENTITY_WITH_MEASURES,
                    Triple(false, false, true) to MentionKinds.ENTITY_WITH_MEASURES,
                    Triple(false, true, false) to MentionKinds.ENTITY,
                    Triple(false, false, false) to MentionKinds.ENTITY,
                )
            table.size shouldBe 8
            for ((facts, expected) in table) {
                val (isAttribute, listed, ownerHas) = facts
                withClue("isAttribute=$isAttribute listedAsMeasure=$listed ownerHasMeasures=$ownerHas") {
                    MentionKinds.of(
                        MentionKinds.ObjectFacts(
                            isAttribute = isAttribute,
                            ownerRef = if (isAttribute) "er.entity.sales" else null,
                            listedAsMeasure = listed,
                            ownerHasMeasures = ownerHas,
                        ),
                    ) shouldBe expected
                }
            }
        }

        "ownerRef is carried, not consulted" {
            // The kind must not depend on the ref at all — MS's ⛔ rule is that nothing anywhere
            // derives a kind from a ref STRING, and the cheapest way to keep that true is for the
            // table to be blind to it.
            val listed =
                MentionKinds.ObjectFacts(
                    isAttribute = true,
                    ownerRef = "er.entity.sales",
                    listedAsMeasure = true,
                    ownerHasMeasures = true,
                )
            MentionKinds.of(listed) shouldBe MentionKinds.of(listed.copy(ownerRef = null))
            MentionKinds.of(listed) shouldBe MentionKinds.of(listed.copy(ownerRef = "op:whatever.measure"))
        }
    })
