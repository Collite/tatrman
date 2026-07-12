// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.conformance

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Parses every shared conformance fixture, asserts it is error-free, and writes
 * the normalised semantics dump to `build/conformance/kt-sem/<fixture>.json`.
 * The TS side (`run-ts-sem.ts`) writes matching dumps; `tests/conformance/diff-sem.ts`
 * compares them byte-for-byte.
 */
class SemanticsConformanceSpec :
    StringSpec({

        val fixturesDir = locateFixtures()
        val outDir = Paths.get("build/conformance/kt-sem")
        Files.createDirectories(outDir)

        // Single-file fixtures: one `.ttrm` → one single-document dump.
        val fixtures =
            Files.list(fixturesDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.name.endsWith(".ttrm") }.sorted().toList()
            }

        fixtures.forEach { fixture ->
            "sem dumps cleanly: ${fixture.name}" {
                val result = TtrLoader.parseFile(fixture)
                result.errors shouldBe emptyList()

                // Use the bare filename as the URI so both runtimes agree (positions are not compared).
                val json = SemanticsConformanceDump.dump(result, fixture.name)
                outDir.resolve(fixture.name.removeSuffix(".ttrm") + ".json").writeText(json + "\n")
            }
        }

        // Multi-document scenarios: each subdirectory bundles several `.ttrm` files
        // loaded into one symbol table → one `<dir>.json` dump. Mirrors run-ts-sem.ts.
        // `*-negative/` directories are parser-reject rosters (they contain syntax
        // errors by design) — they are exercised by the dedicated negative-parse spec
        // (ttr-parser SemanticsNegativeSpec), NOT as positive conformance scenarios.
        val scenarioDirs =
            Files.list(fixturesDir).use { stream ->
                stream.filter { Files.isDirectory(it) && !it.name.endsWith("-negative") }.sorted().toList()
            }

        scenarioDirs.forEach { dir ->
            "sem dumps cleanly (multi-doc): ${dir.name}" {
                val subFiles =
                    Files.list(dir).use { stream ->
                        stream.filter { it.name.endsWith(".ttrm") }.sorted().toList()
                    }
                val docs =
                    subFiles.map { sf ->
                        val result = TtrLoader.parseFile(sf)
                        result.errors shouldBe emptyList()
                        SemanticsConformanceDump.DocInput("${dir.name}/${sf.name}", result)
                    }
                val json = SemanticsConformanceDump.dumpDocs(docs)
                outDir.resolve(dir.name + ".json").writeText(json + "\n")
            }
        }
    })

/** Walks up from the test working dir to find `tests/conformance/fixtures`. */
private fun locateFixtures(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("tests/conformance/fixtures")
        if (Files.isDirectory(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate tests/conformance/fixtures from ${Paths.get("").toAbsolutePath()}")
}
