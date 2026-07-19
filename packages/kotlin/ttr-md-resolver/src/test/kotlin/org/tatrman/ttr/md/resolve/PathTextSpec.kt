// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * S2-A — the path-text tokenizer ([PathText]). Covers the §1.3 self-identifying path shapes
 * (numeric members, quoted members, sets, ranges, stars) that the resolver + S7 agent both consume.
 * Float-vs-path disambiguation (R1) is the frontend's job (S3), not this tokenizer's.
 */
class PathTextSpec :
    StringSpec({
        "splits a plain dotted path into idents and int literals" {
            PathText.parse("sales.2025.net") shouldBe
                listOf(PathComponent.Ident("sales"), PathComponent.IntLit("2025"), PathComponent.Ident("net"))
        }

        "keeps leading zeros as int-literal text" {
            PathText.parse("2025.06") shouldBe listOf(PathComponent.IntLit("2025"), PathComponent.IntLit("06"))
        }

        "reads a quoted member as one component, unquoted" {
            PathText.parse("""sales."Kaufland K123".net""") shouldBe
                listOf(PathComponent.Ident("sales"), PathComponent.Quoted("Kaufland K123"), PathComponent.Ident("net"))
        }

        "reads a brace set as one component" {
            PathText.parse("sales.{Kaufland, Lidl}.net") shouldBe
                listOf(
                    PathComponent.Ident("sales"),
                    PathComponent.SetLit(listOf(PathComponent.Ident("Kaufland"), PathComponent.Ident("Lidl"))),
                    PathComponent.Ident("net"),
                )
        }

        "reads a range as one component, not two dot-separated ints" {
            PathText.parse("sales.2024..2026.net") shouldBe
                listOf(
                    PathComponent.Ident("sales"),
                    PathComponent.RangeLit(PathComponent.IntLit("2024"), PathComponent.IntLit("2026")),
                    PathComponent.Ident("net"),
                )
        }

        "reads a star as one component" {
            PathText.parse("month.*") shouldBe listOf(PathComponent.Ident("month"), PathComponent.Star)
        }

        "is whitespace-insensitive (R2)" {
            PathText.parse("2024 .. 2026") shouldBe
                listOf(PathComponent.RangeLit(PathComponent.IntLit("2024"), PathComponent.IntLit("2026")))
        }
    })
