package org.tatrman.ttr.parser.conformance

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
 * the normalised JSON dump to `build/conformance/kt/<fixture>.json`. The TS side
 * writes matching dumps; `tests/conformance/diff.ts` compares them byte-for-byte.
 */
class ConformanceSpec :
    StringSpec({

        val fixturesDir = locateFixtures()
        val outDir = Paths.get("build/conformance/kt")
        Files.createDirectories(outDir)

        val fixtures =
            Files.list(fixturesDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.name.endsWith(".ttrm") }.sorted().toList()
            }

        fixtures.forEach { fixture ->
            "dumps cleanly: ${fixture.name}" {
                val result = TtrLoader.parseFile(fixture)
                result.errors shouldBe emptyList()

                val json = ConformanceDump.dump(result)
                outDir.resolve(fixture.name.removeSuffix(".ttrm") + ".json").writeText(json + "\n")
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
