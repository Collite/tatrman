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

        val fixtures =
            Files.list(fixturesDir).use { stream ->
                stream.filter { it.name.endsWith(".ttr") }.sorted().toList()
            }

        fixtures.forEach { fixture ->
            "sem dumps cleanly: ${fixture.name}" {
                val result = TtrLoader.parseFile(fixture)
                result.errors shouldBe emptyList()

                // Use the bare filename as the URI so both runtimes agree (positions are not compared).
                val json = SemanticsConformanceDump.dump(result, fixture.name)
                outDir.resolve(fixture.name.removeSuffix(".ttr") + ".json").writeText(json + "\n")
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
