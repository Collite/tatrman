package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody

/**
 * C2-f: fragment interiors are byte-preserved through parse. Each fragment's
 * `sourceText` must equal the exact byte slice of the input file between the tag
 * line's newline and the closing fence — no dedent, no trim. The expected value is
 * sliced from the RAW fixture (not re-derived), so this catches any interior
 * mangling incl. `--`/`#` comments, dict literals, blank lines, trailing spaces.
 */
class TtrpTaggedBlockSpec :
    StringSpec({
        val raw = Fixtures.golden("fragments.ttrp")
        val containers =
            TtrpParser
                .parseString(raw, "fragments.ttrp")
                .document.statements
                .filterIsInstance<ContainerDecl>()

        fun rawInterior(tag: String): String {
            val open = raw.indexOf("\"\"\"$tag")
            val afterTagLine = raw.indexOf('\n', open) + 1
            val close = raw.indexOf("\"\"\"", afterTagLine)
            return raw.substring(afterTagLine, close)
        }

        for (tag in listOf("sql", "pandas", "ttrb")) {
            "fragment `$tag` interior is byte-identical to the raw slice" {
                val body = containers.map { it.body }.filterIsInstance<FragmentBody>().single { it.tag == tag }
                body.sourceText shouldBe rawInterior(tag)
            }
        }

        "dict literal and trailing spaces survive verbatim in the pandas fragment" {
            val pandas = containers.map { it.body }.filterIsInstance<FragmentBody>().single { it.tag == "pandas" }
            (pandas.sourceText.contains("d = { 'k': 1 }")) shouldBe true
            (pandas.sourceText.contains("# a pandas comment   \n")) shouldBe true // trailing spaces kept
        }
    })
