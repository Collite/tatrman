// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * v4.1 — world model parse coverage (ttr-metadata M0). Mirrors the TS
 * `world.test.ts`; the golden roster invariants match `tasks-p1-s1.3` T1.3.1.
 */
class WorldParseSpec :
    StringSpec({

        "parses the golden fixture (57-world.ttrm) roster" {
            val r = TtrLoader.parseFile(fixture("57-world.ttrm"))
            r.ok shouldBe true
            r.modelDirective?.modelCode shouldBe "world"

            val worlds = r.definitions.filterIsInstance<WorldDef>()
            worlds shouldHaveSize 1
            val dev = worlds[0]
            dev.name shouldBe "dev"
            dev.engines shouldHaveSize 2
            dev.executors shouldHaveSize 1
            dev.storages shouldHaveSize 3

            val erpDb = dev.storages.first { it.name == "erp_db" }
            erpDb.hosts shouldBe listOf("erp")
            erpDb.via shouldBe "erp_pg"

            val staging = dev.storages.filter { it.staging }
            staging shouldHaveSize 1
            staging[0].name shouldBe "stage"

            val files = dev.storages.first { it.name == "files" }
            files.schemas shouldHaveSize 1
            files.schemas[0].name shouldBe "sales_csv"
            files.schemas[0].fields.map { it.name } shouldBe listOf("customer", "region", "amount")

            val erpPg = dev.engines.first { it.name == "erp_pg" }
            erpPg.type shouldBe "postgres"
            erpPg.version shouldBe "16"
            erpPg.manifest.keys shouldContain "extensions"
        }

        "parses the extends fixture (58-world-extends.ttrm)" {
            val r = TtrLoader.parseFile(fixture("58-world-extends.ttrm"))
            r.ok shouldBe true
            val world = r.definitions.filterIsInstance<WorldDef>().single()
            world.engines[0].extends shouldBe "acme.types.postgres16"
            world.storages[0].extends shouldBe "acme.types.scratch_dir"
            world.storages[0].staging shouldBe true
        }

        // Table-driven parser-level rejects (world-negative/*.ttrm).
        listOf(
            "neg-01-toplevel-engine.ttrm",
            "neg-02-staging-nonbool.ttrm",
            "neg-03-hosts-strings.ttrm",
            "neg-04-nested-world.ttrm",
            "neg-05-extends-string.ttrm",
        ).forEach { name ->
            "rejects $name" {
                val src = readResource("world-negative/$name")
                val r = TtrLoader.parseString(src)
                r.ok shouldBe false
            }
        }
    })

private fun fixture(name: String): Path = locateFixturesDir().resolve(name)

private fun locateFixturesDir(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("tests/conformance/fixtures")
        if (Files.isDirectory(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate tests/conformance/fixtures")
}

private fun readResource(path: String): String =
    object {}
        .javaClass.classLoader
        .getResource(path)
        ?.readText()
        ?: error("missing test resource: $path")
