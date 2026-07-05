package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.parser.loader.TtrLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * v4.1 world validators (ttr-metadata M0). Mirrors the TS `world-validate.test.ts`
 * case-for-case. Hard errors stay in M2's WorldResolver (MD5).
 */
class WorldSemanticsSpec :
    StringSpec({

        "golden fixture produces zero world diagnostics" {
            val r = TtrLoader.parseFile(fixture("57-world.ttrm"))
            WorldValidator.validateWorldDocument(r, setOf("erp")) shouldBe emptyList()
        }

        "two staging storages → one world/duplicate-staging warning" {
            val r =
                TtrLoader.parseString(
                    """
                    package acme.worlds
                    model world
                    def world dev {
                      def storage a { type: local_dir, staging: true }
                      def storage b { type: local_dir, staging: true }
                    }
                    """.trimIndent(),
                )
            val staging = WorldValidator.validateWorldDocument(r).filter { it.code == "world/duplicate-staging" }
            staging shouldHaveSize 1
            staging[0].severity shouldBe "warning"
            staging[0].message shouldContain "a"
            staging[0].message shouldContain "b"
        }

        "hosts naming an unknown package → world/hosts-unknown-package" {
            val r =
                TtrLoader.parseString(
                    """
                    package acme.worlds
                    model world
                    def world dev {
                      def storage s { type: postgres, hosts: [nosuch] }
                    }
                    """.trimIndent(),
                )
            val hosts =
                WorldValidator
                    .validateWorldDocument(r, setOf("erp"))
                    .filter { it.code == "world/hosts-unknown-package" }
            hosts shouldHaveSize 1
            hosts[0].message shouldContain "nosuch"
        }

        "a def world in a non-model-world file → world/wrong-model-kind" {
            val r =
                TtrLoader.parseString(
                    """
                    package acme.worlds
                    model db
                    def world dev { }
                    """.trimIndent(),
                )
            WorldValidator.validateWorldDocument(r).any { it.code == "world/wrong-model-kind" } shouldBe true
        }

        "a non-world def in a model world file → world/wrong-model-kind" {
            val r =
                TtrLoader.parseString(
                    """
                    package acme.worlds
                    model world
                    def table t { }
                    """.trimIndent(),
                )
            WorldValidator.validateWorldDocument(r).any { it.code == "world/wrong-model-kind" } shouldBe true
        }
    })

private fun fixture(name: String): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("tests/conformance/fixtures/$name")
        if (Files.isRegularFile(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate tests/conformance/fixtures/$name")
}
