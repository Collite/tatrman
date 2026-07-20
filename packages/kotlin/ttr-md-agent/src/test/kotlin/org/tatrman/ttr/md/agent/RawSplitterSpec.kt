// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.md.resolve.PathComponent
import org.tatrman.ttr.md.resolve.PathText

/**
 * S7-A2 — the `raw` tokenizer the agent reuses (dot-path contracts §9: "split on `.` respecting
 * quotes/braces"). The splitter is `PathText.parse` in ttr-md-resolver (S7 reuses it rather than
 * owning a second one); this pins the §9 behaviour the agent depends on: quotes, brace sets, and `..`
 * ranges are each ONE component, whitespace is insignificant, and malformed input is rejected.
 */
class RawSplitterSpec :
    StringSpec({
        "the §9 example splits into five components (quote / set / range are one each)" {
            val components = PathText.parse("""sales."Kaufland K123".{a, b}.2024..2026.net""")
            components shouldHaveSize 5
            components[0].shouldBeInstanceOf<PathComponent.Ident>() // sales
            components[1].shouldBeInstanceOf<PathComponent.Quoted>() // "Kaufland K123" (spaces kept, one atom)
            components[2].shouldBeInstanceOf<PathComponent.SetLit>() // {a, b}
            components[3].shouldBeInstanceOf<PathComponent.RangeLit>() // 2024..2026
            components[4].shouldBeInstanceOf<PathComponent.Ident>() // net
        }

        "a quoted member keeps its inner spaces as a single component" {
            val q = PathText.parse("""."Kaufland K123"""").single()
            q.shouldBeInstanceOf<PathComponent.Quoted>()
            q.text shouldBe "Kaufland K123"
        }

        "a digit-only atom is an int member candidate, an identifier is not" {
            PathText.parse("2025").single().shouldBeInstanceOf<PathComponent.IntLit>()
            PathText.parse("net").single().shouldBeInstanceOf<PathComponent.Ident>()
        }

        "an unterminated quote is rejected (no partial split)" {
            shouldThrow<IllegalArgumentException> { PathText.parse("""sales."Kaufland""") }
        }
    })
