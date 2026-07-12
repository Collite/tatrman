// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * M2 — a [Reference] carries the source span of the reference *token* (matching
 * the canonical TS `Reference`), not its enclosing def. A consumer building a
 * diagnostic or navigation target from a `Reference`-typed slot must land on the
 * reference itself.
 */
class ReferenceSourceSpec :
    StringSpec({

        "Reference-typed slots carry the reference token's own span (not the def's)" {
            // `customer_name` starts on line 3, well after the `def entity` keyword.
            val r =
                TtrLoader.parseString(
                    """
                    def entity Customer {
                        attributes: [def attribute customer_name { type: text }]
                        nameAttribute: customer_name
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val entity = r.definitions[0] as EntityDef

            val ref = entity.nameAttribute!!
            ref.path shouldBe "customer_name"
            ref.parts shouldBe listOf("customer_name")

            // The token is on line 3 (1-indexed); the def starts on line 1. The
            // pre-M2 fallback used the def's span — this asserts the regression
            // stays fixed.
            ref.source.line shouldBe 3
            (ref.source.line != entity.source.line) shouldBe true
            // The span covers exactly the identifier (13 chars: "customer_name").
            (ref.source.endColumn - ref.source.column) shouldBe "customer_name".length
        }

        "the convenience constructor derives parts and an unknown span" {
            val ref = Reference("er.entity.artikl")
            ref.parts shouldBe listOf("er", "entity", "artikl")
            ref.source shouldBe SourceLocation.UNKNOWN
        }
    })
