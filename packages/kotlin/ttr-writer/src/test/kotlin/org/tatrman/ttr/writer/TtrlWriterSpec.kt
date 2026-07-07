package org.tatrman.ttr.writer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrlLoader

/**
 * `TtrlWriter` canonical, byte-stable emission + round-trip (T5.2.4): `parse(write(x)) == x`
 * for a canonically-ordered document, `write` is idempotent, and node/canvas ordering is
 * normalized deterministically.
 */
class TtrlWriterSpec :
    StringSpec({

        // The canonical hero sidecar (writer's own ordering + minimal-emission rules).
        val canonical =
            "ttrl 1\n" +
                "\n" +
                "canvas program {\n" +
                "    skin: \"alteryx-knime\"\n" +
                "    mode: manual\n" +
                "    nodes: {\n" +
                "        \"big_customers~1\": { x: 700, y: 40 }\n" +
                "        \"crunch\": { x: 420, y: 80 }\n" +
                "        \"db_prep\": { x: 120, y: 80 }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "canvas crunch {\n" +
                "    skin: \"enso\"\n" +
                "    mode: auto\n" +
                "}\n"

        "write(parse(canonical)) is byte-identical" {
            val doc = TtrlLoader.parseString(canonical, "hero.ttrl").document!!
            TtrlWriter.write(doc) shouldBe canonical
        }

        "parse(write(x)) == x (structural round-trip)" {
            val doc = TtrlLoader.parseString(canonical, "hero.ttrl").document!!
            val reparsed = TtrlLoader.parseString(TtrlWriter.write(doc), "hero.ttrl").document!!
            reparsed shouldBe doc
        }

        "write is idempotent" {
            val doc = TtrlLoader.parseString(canonical, "hero.ttrl").document!!
            val once = TtrlWriter.write(doc)
            val twice = TtrlWriter.write(TtrlLoader.parseString(once, "hero.ttrl").document!!)
            twice shouldBe once
        }

        "canvas + node ordering is normalized regardless of input order" {
            val scrambled =
                """
                ttrl 1
                canvas crunch { mode: auto skin: "enso" }
                canvas program {
                    mode: manual
                    skin: "alteryx-knime"
                    nodes: {
                        "db_prep": { x: 120, y: 80 }
                        "big_customers~1": { x: 700, y: 40 }
                        "crunch": { x: 420, y: 80 }
                    }
                }
                """.trimIndent()
            val doc = TtrlLoader.parseString(scrambled, "hero.ttrl").document!!
            TtrlWriter.write(doc) shouldBe canonical
        }
    })
