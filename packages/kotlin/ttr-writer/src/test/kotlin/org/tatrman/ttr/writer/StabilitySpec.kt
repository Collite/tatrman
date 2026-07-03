package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TableDef

/**
 * The renderer must be deterministic — same input, byte-identical output across
 * calls — so rendered TTR is diff-stable in source control (contracts.md §3).
 */
class StabilitySpec :
    StringSpec({

        "render is deterministic across repeated calls" {
            val defs =
                listOf(
                    RoleDef(name = "b_role", source = SourceLocation.UNKNOWN, description = "second"),
                    RoleDef(name = "a_role", source = SourceLocation.UNKNOWN, description = "first"),
                )
            TtrRenderer.render(defs) shouldBe TtrRenderer.render(defs)
        }

        "definitions of the same kind render in a stable (name-sorted) order" {
            val defs =
                listOf(
                    TableDef(name = "zebra", source = SourceLocation.UNKNOWN),
                    TableDef(name = "alpha", source = SourceLocation.UNKNOWN),
                )
            val out = TtrRenderer.render(defs)
            (out.indexOf("def table alpha") < out.indexOf("def table zebra")) shouldBe true
        }

        "render(definitions) is independent of the input list order for the same set" {
            val a = RoleDef(name = "a", source = SourceLocation.UNKNOWN)
            val b = RoleDef(name = "b", source = SourceLocation.UNKNOWN)
            TtrRenderer.render(listOf(a, b)) shouldBe TtrRenderer.render(listOf(b, a))
        }
    })
