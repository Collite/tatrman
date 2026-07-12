// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.format

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.parser.TtrpParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * The C2-f regression tripwire, independent of golden pairs: for every input, the byte
 * span of every tagged-block interior in the output equals the input's interior exactly —
 * even after canonical (non-fragment) whitespace is mutated around the fragment.
 */
class FragmentPreservationSpec :
    StringSpec({
        val formatter = TtrpFormatter()

        fun fragmentInteriors(source: String): List<String> =
            TtrpParser
                .parseString(source, "x.ttrp")
                .document.statements
                .filterIsInstance<ContainerDecl>()
                .mapNotNull { (it.body as? FragmentBody)?.sourceText }

        val fragmentFixture =
            Files.readString(Path.of("src/test/resources/format/fragment-untouchable.in.ttrp"))

        "fragment interiors survive formatting byte-for-byte" {
            val before = fragmentInteriors(fragmentFixture)
            val after = fragmentInteriors(formatter.format(fragmentFixture, "x.ttrp"))
            after shouldBe before
        }

        "fragment interiors survive under mutated canonical whitespace" {
            checkAll(20, Arb.int(0, 6)) { extraSpaces ->
                // Mutate only the canonical region (the wiring lines below the fence): add
                // leading spaces. The fragment interior between the fences must not move.
                val mutated =
                    fragmentFixture.replace(
                        "acc_prep",
                        " ".repeat(extraSpaces) + "acc_prep",
                    )
                val out = formatter.format(mutated, "x.ttrp")
                fragmentInteriors(out) shouldBe fragmentInteriors(fragmentFixture)
            }
        }
    })
